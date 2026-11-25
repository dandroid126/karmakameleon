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
import com.karmakameleon.shared.util.RedditLink
import com.karmakameleon.shared.util.markdown.MarkdownParser
import com.karmakameleon.shared.util.runSuspendCatching
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class PostRepository(
    private val redditApi: RedditApi,
    private val markdownParser: MarkdownParser,
) {
    private val cachedPosts = MutableStateFlow<Map<String, Post>>(emptyMap())

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
    ): RedditLink? = redditApi.resolveShareLink(subreddit, shareId)

    suspend fun getPostWithComments(
        subreddit: String,
        postId: String,
        sort: CommentSort? = CommentSort.CONFIDENCE,
        commentId: String? = null,
        context: Int? = null,
    ): Result<Pair<Post, List<CommentOrMore>>> =
        runSuspendCatching {
            val result = redditApi.getPostWithComments(subreddit, postId, sort, commentId = commentId, context = context)
            // Its comments' text is parsed already, as they were read.
            val post = markdownParser.withParsedSelfText(preserveCachedPreviewData(result.first))
            cachedPosts.update { it + (postId to post) }
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
     * as the last item. [more] lists replies too, so comments that an earlier thread brought along, or holds in its own
     * "more" entries, aren't requested: they'd end up a level too high. A failed request stops the loading and its comments
     * stay in what's left, and it only fails the call if nothing loaded.
     */
    suspend fun getMoreComments(
        subreddit: String,
        postId: String,
        more: MoreComments,
        sort: CommentSort = CommentSort.CONFIDENCE,
    ): Result<List<CommentOrMore>> =
        runSuspendCatching {
            val threads = mutableListOf<CommentOrMore.CommentItem>()
            val loadedIds = mutableSetOf<String>()
            val inThreadMoreIds = mutableSetOf<String>()
            val requested = mutableSetOf<String>()
            val failed = mutableSetOf<String>()
            var failure: Throwable? = null
            while (failure == null && requested.size < MORE_COMMENTS_BATCH_SIZE) {
                val next =
                    more.children
                        .filter { it !in loadedIds && it !in inThreadMoreIds && it !in requested }
                        .take(minOf(MORE_COMMENTS_PARALLEL_REQUESTS, MORE_COMMENTS_BATCH_SIZE - requested.size))
                if (next.isEmpty()) break
                requested += next
                val results =
                    coroutineScope {
                        next.map { id -> async { id to runSuspendCatching { loadThread(subreddit, postId, id, sort) } } }.awaitAll()
                    }
                for ((id, result) in results) {
                    result
                        .onSuccess { thread ->
                            thread?.let {
                                threads += CommentOrMore.CommentItem(it.atDepth(more.depth))
                                it.addIdsTo(loadedIds, inThreadMoreIds)
                            }
                        }.onFailure {
                            failed += id
                            failure = it
                        }
                }
            }
            failure?.takeIf { threads.isEmpty() }?.let { throw it }
            val remaining =
                more.children
                    .filter { it !in loadedIds && it !in inThreadMoreIds }
                    .filter { it !in requested || it in failed }
            threads + listOfNotNull(more.afterLoading(remaining, loadedIds.size))
        }

    // The comment with its replies, or null when it's deleted: then the thread is empty, or holds other comments.
    private suspend fun loadThread(
        subreddit: String,
        postId: String,
        commentId: String,
        sort: CommentSort,
    ): Comment? =
        redditApi
            .getPostWithComments(subreddit, postId, sort, commentId = commentId)
            .second
            .filterIsInstance<CommentOrMore.CommentItem>()
            .firstOrNull { it.comment.id == commentId }
            ?.comment

    private fun Comment.addIdsTo(
        loadedIds: MutableSet<String>,
        inMoreIds: MutableSet<String>,
    ) {
        loadedIds += id
        moreReplies?.let { inMoreIds += it.children }
        replies.forEach { it.addIdsTo(loadedIds, inMoreIds) }
    }

    private fun Comment.atDepth(newDepth: Int): Comment =
        copy(
            depth = newDepth,
            replies = replies.map { it.atDepth(newDepth + 1) },
            moreReplies = moreReplies?.copy(depth = newDepth + 1),
        )

    // What's left of this "more" entry after loading some of its comments, if anything.
    private fun MoreComments.afterLoading(
        remaining: List<String>,
        loadedCount: Int,
    ): CommentOrMore.More? =
        remaining.takeIf { it.isNotEmpty() }?.let {
            CommentOrMore.More(copy(children = it, count = maxOf(it.size, count - loadedCount)))
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

    /** [post] with its text parsed, like a post [getPostWithComments] returns, for a post that came from a feed. */
    suspend fun withParsedSelfText(post: Post): Post = markdownParser.withParsedSelfText(post)
}

// How many requests one tap on a "load more" button can make, and how many of them run at a time.
private const val MORE_COMMENTS_BATCH_SIZE = 20
private const val MORE_COMMENTS_PARALLEL_REQUESTS = 5
