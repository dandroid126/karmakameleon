package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.data.api.CommentOrMore
import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.domain.model.CommentSort
import com.karmakameleon.shared.domain.model.Listing
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.PostSort
import com.karmakameleon.shared.domain.model.SearchSort
import com.karmakameleon.shared.domain.model.TimeFilter
import com.karmakameleon.shared.util.runSuspendCatching
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update

class PostRepository(
    private val redditApi: RedditApi,
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

    suspend fun getMoreComments(
        linkId: String,
        children: List<String>,
        sort: CommentSort = CommentSort.CONFIDENCE,
    ): Result<List<CommentOrMore>> = runSuspendCatching { redditApi.getMoreComments(linkId, children, sort) }

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
