package com.reader.shared.data.repository

import com.reader.shared.data.api.CommentOrMore
import com.reader.shared.data.api.RedditApi
import com.reader.shared.domain.model.CommentSort
import com.reader.shared.domain.model.Listing
import com.reader.shared.domain.model.Post
import com.reader.shared.domain.model.PostSort
import com.reader.shared.domain.model.SearchSort
import com.reader.shared.domain.model.TimeFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PostRepository(
    private val redditApi: RedditApi,
) {
    private val _cachedPosts = MutableStateFlow<Map<String, Post>>(emptyMap())
    
    suspend fun getPosts(
        subreddit: String? = null,
        sort: PostSort = PostSort.HOT,
        time: TimeFilter? = null,
        after: String? = null,
        limit: Int = 25
    ): Result<Listing<Post>> {
        return try {
            val listing = redditApi.getPosts(subreddit, sort, time, after, limit)
            // Cache posts
            _cachedPosts.update { cache ->
                cache + listing.items.associateBy { it.id }
            }
            Result.success(listing)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPost(subreddit: String, postId: String): Result<Post> {
        // Check cache first
        _cachedPosts.value[postId]?.let { return Result.success(it) }
        
        return try {
            val post = redditApi.getPost(subreddit, postId)
            if (post != null) {
                _cachedPosts.update { it + (postId to post) }
                Result.success(post)
            } else {
                Result.failure(Exception("Post not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPostWithComments(
        subreddit: String,
        postId: String,
        sort: CommentSort = CommentSort.CONFIDENCE
    ): Result<Pair<Post, List<CommentOrMore>>> {
        return try {
            val result = redditApi.getPostWithComments(subreddit, postId, sort)
            _cachedPosts.update { it + (postId to result.first) }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMoreComments(
        linkId: String,
        children: List<String>,
        sort: CommentSort = CommentSort.CONFIDENCE
    ): Result<List<CommentOrMore>> {
        return try {
            val comments = redditApi.getMoreComments(linkId, children, sort)
            Result.success(comments)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun vote(post: Post, direction: Int): Result<Post> {
        return try {
            val success = redditApi.vote(post.name, direction)
            if (success) {
                val newLikes = when (direction) {
                    1 -> true
                    -1 -> false
                    else -> null
                }
                val scoreDiff = when {
                    post.likes == true && direction != 1 -> -1
                    post.likes == false && direction != -1 -> 1
                    post.likes == null && direction == 1 -> 1
                    post.likes == null && direction == -1 -> -1
                    else -> 0
                }
                val updatedPost = post.copy(
                    likes = newLikes,
                    score = post.score + scoreDiff
                )
                _cachedPosts.update { it + (post.id to updatedPost) }
                Result.success(updatedPost)
            } else {
                Result.failure(Exception("Vote failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun save(post: Post): Result<Post> {
        return try {
            val success = if (post.isSaved) {
                redditApi.unsave(post.name)
            } else {
                redditApi.save(post.name)
            }
            if (success) {
                val updatedPost = post.copy(isSaved = !post.isSaved)
                _cachedPosts.update { it + (post.id to updatedPost) }
                Result.success(updatedPost)
            } else {
                Result.failure(Exception("Save failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun hide(post: Post): Result<Post> {
        return try {
            val success = if (post.isHidden) {
                redditApi.unhide(post.name)
            } else {
                redditApi.hide(post.name)
            }
            if (success) {
                val updatedPost = post.copy(isHidden = !post.isHidden)
                _cachedPosts.update { it + (post.id to updatedPost) }
                Result.success(updatedPost)
            } else {
                Result.failure(Exception("Hide failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun search(
        query: String,
        subreddit: String? = null,
        sort: SearchSort = SearchSort.RELEVANCE,
        time: TimeFilter = TimeFilter.ALL,
        after: String? = null
    ): Result<Listing<Post>> {
        return try {
            val listing = redditApi.search(query, subreddit, sort, time, after)
            Result.success(listing)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitPost(
        subreddit: String,
        title: String,
        text: String? = null,
        url: String? = null,
        nsfw: Boolean = false,
        spoiler: Boolean = false
    ): Result<String> {
        return try {
            val kind = if (url != null) "link" else "self"
            val postName = redditApi.submitPost(subreddit, title, kind, text, url, nsfw, spoiler)
            if (postName != null) {
                Result.success(postName)
            } else {
                Result.failure(Exception("Failed to submit post"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCachedPost(postId: String): Post? = _cachedPosts.value[postId]
    
    fun observeCachedPost(postId: String): Flow<Map<String, Post>> = _cachedPosts.asStateFlow()
}
