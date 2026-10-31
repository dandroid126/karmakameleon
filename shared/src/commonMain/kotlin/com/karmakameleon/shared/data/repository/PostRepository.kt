package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.data.api.CommentOrMore
import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.domain.model.CommentSort
import com.karmakameleon.shared.domain.model.Listing
import com.karmakameleon.shared.domain.model.MoreComments
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.PostSort
import com.karmakameleon.shared.domain.model.SearchSort
import com.karmakameleon.shared.domain.model.TimeFilter
import com.karmakameleon.shared.util.runSuspendCatching
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update

class PostRepository(
    private val redditApi: RedditApi,
    private val markdownRepository: MarkdownRepository,
) {
    private val cachedPosts = MutableStateFlow<Map<String, Post>>(emptyMap())
    private val _postUpdates = MutableSharedFlow<Post>(extraBufferCapacity = 16)
    val postUpdates: SharedFlow<Post> = _postUpdates.asSharedFlow()

    suspend fun getPosts(
        subreddit: String? = null,
        sort: PostSort = PostSort.HOT,
        time: TimeFilter? = null,
        after: String? = null,
        limit: Int = 25,
    ): Result<Listing<Post>> =
        runSuspendCatching {
            val listing = redditApi.getPosts(subreddit, sort, time, after, limit)
            // Cache posts
            cachedPosts.update { cache ->
                cache + listing.items.associateBy { it.id }
            }
            listing
        }

    suspend fun enrichSparsePosts(posts: List<Post>): List<Post> {
        val sparsePosts =
            posts.filter { post ->
                post.preview == null && post.thumbnail == null && !post.isTextPost
            }
        if (sparsePosts.isEmpty()) return emptyList()

        val enriched = redditApi.fetchPostsByIds(sparsePosts.map { it.name })
        if (enriched.isNotEmpty()) {
            cachedPosts.update { cache ->
                cache + enriched.associateBy { it.id }
            }
        }
        return enriched
    }

    suspend fun getPost(
        subreddit: String,
        postId: String,
    ): Result<Post> {
        // Check cache first
        cachedPosts.value[postId]?.let { return Result.success(it) }

        return runSuspendCatching {
            val post = redditApi.getPost(subreddit, postId) ?: error("Post not found")
            cachedPosts.update { it + (postId to post) }
            post
        }
    }

    // Finds the post a share link (reddit.com/r/<subreddit>/s/<id>) points to, as its subreddit and ID, or null if
    // it doesn't point to a post.
    suspend fun resolveShareLink(
        subreddit: String,
        shareId: String,
    ): Pair<String, String>? = redditApi.resolveShareLink(subreddit, shareId)

    suspend fun getPostWithComments(
        subreddit: String,
        postId: String,
        sort: CommentSort? = CommentSort.CONFIDENCE,
        commentId: String? = null,
        context: Int? = null,
    ): Result<Pair<Post, List<CommentOrMore>>> =
        runSuspendCatching {
            val result = redditApi.getPostWithComments(subreddit, postId, sort, commentId = commentId, context = context)
            val post = preserveCachedPreviewData(result.first)
            cachedPosts.update { it + (postId to post) }
            parseMarkdown(post, result.second)
            post to result.second
        }

    private fun preserveCachedPreviewData(freshPost: Post): Post {
        val cached = cachedPosts.value[freshPost.id] ?: return freshPost
        if (freshPost.preview != null && freshPost.thumbnail != null) return freshPost
        return freshPost.copy(
            preview = freshPost.preview ?: cached.preview,
            thumbnail = freshPost.thumbnail ?: cached.thumbnail,
            thumbnailWidth = freshPost.thumbnailWidth ?: cached.thumbnailWidth,
            thumbnailHeight = freshPost.thumbnailHeight ?: cached.thumbnailHeight,
        )
    }

    /**
     * Loads the next comments of [more], each with its replies and placed at the depth of [more]. Comments are loaded one
     * request each, since Reddit blocks /api/morechildren, so a long list takes several calls: what's left of it comes back
     * as the last item.
     */
    suspend fun getMoreComments(
        subreddit: String,
        postId: String,
        more: MoreComments,
        sort: CommentSort = CommentSort.CONFIDENCE,
    ): Result<List<CommentOrMore>> =
        runSuspendCatching {
            val batch = more.children.take(MORE_COMMENTS_BATCH_SIZE)
            val threads =
                coroutineScope {
                    batch
                        .map { id ->
                            async {
                                redditApi
                                    .getPostWithComments(subreddit, postId, sort, commentId = id)
                                    .second
                                    .filterIsInstance<CommentOrMore.CommentItem>()
                                    // A deleted comment comes back as nothing, or as other comments.
                                    .filter { it.comment.id == id }
                                    .map { CommentOrMore.CommentItem(it.comment.atDepth(more.depth)) }
                            }
                        }.awaitAll()
                        .flatten()
                }
            parseMarkdown(post = null, threads)
            threads + listOfNotNull(more.afterLoading(batch.size, threads))
        }

    private fun Comment.atDepth(newDepth: Int): Comment =
        copy(
            depth = newDepth,
            replies = replies.map { it.atDepth(newDepth + 1) },
            moreReplies = moreReplies?.copy(depth = newDepth + 1),
        )

    // What's left of this "more" entry after loading the first batchSize of its comments, if anything.
    private fun MoreComments.afterLoading(
        batchSize: Int,
        threads: List<CommentOrMore.CommentItem>,
    ): CommentOrMore.More? {
        val remaining = children.drop(batchSize)
        if (remaining.isEmpty()) return null

        fun size(comment: Comment): Int = 1 + comment.replies.sumOf(::size)
        val loaded = threads.sumOf { size(it.comment) }
        return CommentOrMore.More(copy(children = remaining, count = maxOf(remaining.size, count - loaded)))
    }

    // Parses the text of the post and of the comments and their replies before they're shown, so the screen doesn't
    // parse them while drawing.
    private suspend fun parseMarkdown(
        post: Post?,
        comments: List<CommentOrMore>,
    ) {
        val texts = mutableListOf<String>()
        post?.selfText?.let(texts::add)

        fun addThread(comment: Comment) {
            texts += comment.body
            comment.replies.forEach(::addThread)
        }
        comments.filterIsInstance<CommentOrMore.CommentItem>().forEach { addThread(it.comment) }
        markdownRepository.parseAll(texts)
    }

    suspend fun vote(
        post: Post,
        direction: Int,
    ): Result<Post> =
        runSuspendCatching {
            check(redditApi.vote(post.name, direction)) { "Vote failed" }
            val updatedPost =
                post.copy(
                    likes = voteToLikes(direction),
                    score = post.score + direction - likesToVote(post.likes),
                )
            cachedPosts.update { it + (post.id to updatedPost) }
            _postUpdates.emit(updatedPost)
            updatedPost
        }

    suspend fun save(post: Post): Result<Post> =
        runSuspendCatching {
            val success =
                if (post.isSaved) {
                    redditApi.unsave(post.name)
                } else {
                    redditApi.save(post.name)
                }
            check(success) { "Save failed" }
            val updatedPost = post.copy(isSaved = !post.isSaved)
            cachedPosts.update { it + (post.id to updatedPost) }
            _postUpdates.emit(updatedPost)
            updatedPost
        }

    suspend fun hide(post: Post): Result<Post> =
        runSuspendCatching {
            val success =
                if (post.isHidden) {
                    redditApi.unhide(post.name)
                } else {
                    redditApi.hide(post.name)
                }
            check(success) { "Hide failed" }
            val updatedPost = post.copy(isHidden = !post.isHidden)
            cachedPosts.update { it + (post.id to updatedPost) }
            _postUpdates.emit(updatedPost)
            updatedPost
        }

    suspend fun search(
        query: String,
        subreddit: String? = null,
        sort: SearchSort = SearchSort.RELEVANCE,
        time: TimeFilter = TimeFilter.ALL,
        after: String? = null,
    ): Result<Listing<Post>> = runSuspendCatching { redditApi.search(query, subreddit, sort, time, after) }

    suspend fun submitPost(
        subreddit: String,
        title: String,
        text: String? = null,
        url: String? = null,
        nsfw: Boolean = false,
        spoiler: Boolean = false,
    ): Result<String> =
        runSuspendCatching {
            val kind = if (url != null) "link" else "self"
            redditApi.submitPost(subreddit, title, kind, text, url, nsfw, spoiler) ?: error("Failed to submit post")
        }

    fun getCachedPost(postId: String): Post? = cachedPosts.value[postId]
}

// How many comments one tap on a "load more" button loads, since each one is a request.
private const val MORE_COMMENTS_BATCH_SIZE = 20
