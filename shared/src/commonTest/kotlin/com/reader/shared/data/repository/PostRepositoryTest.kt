package com.reader.shared.data.repository

import com.reader.shared.FakeRedditApi
import com.reader.shared.createTestComment
import com.reader.shared.createTestListing
import com.reader.shared.createTestPost
import com.reader.shared.data.api.CommentOrMore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostRepositoryTest {

    private fun createRepo(api: FakeRedditApi = FakeRedditApi()) = PostRepository(api) to api

    // ==================== getPosts ====================

    @Test
    fun getPosts_returnsSuccess() = runTest {
        val (repo, api) = createRepo()
        val posts = listOf(createTestPost(id = "1"), createTestPost(id = "2"))
        api.postsResult = createTestListing(items = posts, after = "after1")

        val result = repo.getPosts()
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().items.size)
        assertEquals("after1", result.getOrThrow().after)
    }

    @Test
    fun getPosts_cachesPostsInternally() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "cached1")
        api.postsResult = createTestListing(items = listOf(post))

        repo.getPosts()
        assertEquals(post, repo.getCachedPost("cached1"))
    }

    @Test
    fun getPosts_returnsFailureOnException() = runTest {
        val (repo, api) = createRepo()
        api.shouldThrow = Exception("Network error")

        val result = repo.getPosts()
        assertTrue(result.isFailure)
    }

    // ==================== getPost ====================

    @Test
    fun getPost_returnsCachedPost() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "p1")
        api.postsResult = createTestListing(items = listOf(post))
        repo.getPosts() // cache it

        val result = repo.getPost("testsubreddit", "p1")
        assertTrue(result.isSuccess)
        assertEquals("p1", result.getOrThrow().id)
    }

    @Test
    fun getPost_fetchesFromApiWhenNotCached() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "p2")
        api.postResult = post

        val result = repo.getPost("testsubreddit", "p2")
        assertTrue(result.isSuccess)
        assertEquals("p2", result.getOrThrow().id)
    }

    @Test
    fun getPost_returnsFailureWhenNotFound() = runTest {
        val (repo, api) = createRepo()
        api.postResult = null

        val result = repo.getPost("testsubreddit", "missing")
        assertTrue(result.isFailure)
    }

    // ==================== getPostWithComments ====================

    @Test
    fun getPostWithComments_returnsPostAndComments() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "p1")
        val comment = createTestComment(id = "c1")
        api.postWithCommentsResult = post to listOf(CommentOrMore.CommentItem(comment))

        val result = repo.getPostWithComments("sub", "p1")
        assertTrue(result.isSuccess)
        val (resultPost, resultComments) = result.getOrThrow()
        assertEquals("p1", resultPost.id)
        assertEquals(1, resultComments.size)
    }

    // ==================== getMoreComments ====================

    @Test
    fun getMoreComments_returnsComments() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1")
        api.moreCommentsResult = listOf(CommentOrMore.CommentItem(comment))

        val result = repo.getMoreComments("t3_p1", listOf("c1"))
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
    }

    // ==================== vote ====================

    @Test
    fun vote_upvoteUpdatesPostState() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "p1", score = 10, likes = null)
        api.voteResult = true

        val result = repo.vote(post, 1)
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()
        assertEquals(true, updated.likes)
        assertEquals(11, updated.score)
    }

    @Test
    fun vote_downvoteUpdatesPostState() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "p1", score = 10, likes = null)
        api.voteResult = true

        val result = repo.vote(post, -1)
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()
        assertEquals(false, updated.likes)
        assertEquals(9, updated.score)
    }

    @Test
    fun vote_removeUpvote() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "p1", score = 11, likes = true)
        api.voteResult = true

        val result = repo.vote(post, 0)
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()
        assertNull(updated.likes)
        assertEquals(10, updated.score)
    }

    @Test
    fun vote_removeDownvote() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "p1", score = 9, likes = false)
        api.voteResult = true

        val result = repo.vote(post, 0)
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()
        assertNull(updated.likes)
        assertEquals(10, updated.score)
    }

    @Test
    fun vote_switchFromUpToDown() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "p1", score = 11, likes = true)
        api.voteResult = true

        val result = repo.vote(post, -1)
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()
        assertEquals(false, updated.likes)
        assertEquals(10, updated.score)
    }

    @Test
    fun vote_failureReturnsError() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "p1")
        api.voteResult = false

        val result = repo.vote(post, 1)
        assertTrue(result.isFailure)
    }

    @Test
    fun vote_exceptionReturnsFailure() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "p1")
        api.shouldThrow = Exception("Network error")

        val result = repo.vote(post, 1)
        assertTrue(result.isFailure)
    }

    @Test
    fun vote_redditApiExceptionPreservesMessage() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "p1")
        api.shouldThrow = com.reader.shared.data.api.RedditApiException("THREAD_LOCKED", "you are not allowed to do that")

        val result = repo.vote(post, 1)
        assertTrue(result.isFailure)
        assertEquals("you are not allowed to do that", result.exceptionOrNull()?.message)
    }

    // ==================== voteComment ====================

    @Test
    fun voteComment_upvoteUpdatesState() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1", score = 5, likes = null)
        api.voteResult = true

        val result = repo.voteComment(comment, 1)
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrThrow().likes)
        assertEquals(6, result.getOrThrow().score)
    }

    // ==================== save ====================

    @Test
    fun save_savesUnsavedPost() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "p1", isSaved = false)
        api.saveResult = true

        val result = repo.save(post)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isSaved)
    }

    @Test
    fun save_unsavesSavedPost() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "p1", isSaved = true)
        api.unsaveResult = true

        val result = repo.save(post)
        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().isSaved)
    }

    @Test
    fun save_failureReturnsError() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "p1", isSaved = false)
        api.saveResult = false

        val result = repo.save(post)
        assertTrue(result.isFailure)
    }

    // ==================== hide ====================

    @Test
    fun hide_hidesVisiblePost() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "p1", isHidden = false)
        api.hideResult = true

        val result = repo.hide(post)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isHidden)
    }

    @Test
    fun hide_unhidesHiddenPost() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "p1", isHidden = true)
        api.unhideResult = true

        val result = repo.hide(post)
        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().isHidden)
    }

    // ==================== search ====================

    @Test
    fun search_returnsResults() = runTest {
        val (repo, api) = createRepo()
        val posts = listOf(createTestPost(id = "s1"), createTestPost(id = "s2"))
        api.searchResult = createTestListing(items = posts)

        val result = repo.search("test query")
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().items.size)
    }

    @Test
    fun search_returnsFailureOnError() = runTest {
        val (repo, api) = createRepo()
        api.shouldThrow = Exception("Search failed")

        val result = repo.search("test")
        assertTrue(result.isFailure)
    }

    // ==================== submitPost ====================

    @Test
    fun submitPost_returnsPostName() = runTest {
        val (repo, api) = createRepo()
        api.submitPostResult = "t3_newpost"

        val result = repo.submitPost("testsub", "Title", text = "Body")
        assertTrue(result.isSuccess)
        assertEquals("t3_newpost", result.getOrThrow())
    }

    @Test
    fun submitPost_linkPost() = runTest {
        val (repo, api) = createRepo()
        api.submitPostResult = "t3_linkpost"

        val result = repo.submitPost("testsub", "Title", url = "https://example.com")
        assertTrue(result.isSuccess)
    }

    @Test
    fun submitPost_returnsFailureWhenNull() = runTest {
        val (repo, api) = createRepo()
        api.submitPostResult = null

        val result = repo.submitPost("testsub", "Title")
        assertTrue(result.isFailure)
    }

    // ==================== enrichSparsePosts ====================

    @Test
    fun enrichSparsePosts_returnsEmptyForNonSparsePosts() = runTest {
        val (repo, api) = createRepo()
        val post = createTestPost(id = "p1", postHint = "self", selfText = "text")
        val result = repo.enrichSparsePosts(listOf(post))
        assertTrue(result.isEmpty())
    }

    // ==================== getCachedPost ====================

    @Test
    fun getCachedPost_returnsNullWhenNotCached() {
        val (repo, _) = createRepo()
        assertNull(repo.getCachedPost("nonexistent"))
    }
}
