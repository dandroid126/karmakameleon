package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.createTestComment
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommentRepositoryTest {

    private fun createRepo(api: FakeRedditApi = FakeRedditApi()) = CommentRepository(api) to api

    // ==================== vote ====================

    @Test
    fun vote_upvoteUpdatesState() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1", score = 5, likes = null)
        api.voteResult = true

        val result = repo.vote(comment, 1)
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrThrow().likes)
        assertEquals(6, result.getOrThrow().score)
    }

    @Test
    fun vote_downvoteUpdatesState() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1", score = 5, likes = null)
        api.voteResult = true

        val result = repo.vote(comment, -1)
        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrThrow().likes)
        assertEquals(4, result.getOrThrow().score)
    }

    @Test
    fun vote_removeUpvote() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1", score = 6, likes = true)
        api.voteResult = true

        val result = repo.vote(comment, 0)
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow().likes)
        assertEquals(5, result.getOrThrow().score)
    }

    @Test
    fun vote_removeDownvote() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1", score = 4, likes = false)
        api.voteResult = true

        val result = repo.vote(comment, 0)
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow().likes)
        assertEquals(5, result.getOrThrow().score)
    }

    @Test
    fun vote_switchUpToDown() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1", score = 6, likes = true)
        api.voteResult = true

        val result = repo.vote(comment, -1)
        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrThrow().likes)
        assertEquals(5, result.getOrThrow().score)
    }

    @Test
    fun vote_switchDownToUp() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1", score = 4, likes = false)
        api.voteResult = true

        val result = repo.vote(comment, 1)
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrThrow().likes)
        assertEquals(5, result.getOrThrow().score)
    }

    @Test
    fun vote_failureReturnsError() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1")
        api.voteResult = false

        val result = repo.vote(comment, 1)
        assertTrue(result.isFailure)
    }

    @Test
    fun vote_exceptionReturnsFailure() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1")
        api.shouldThrow = Exception("Network error")

        val result = repo.vote(comment, 1)
        assertTrue(result.isFailure)
    }

    // ==================== save ====================

    @Test
    fun save_savesUnsavedComment() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1", isSaved = false)
        api.saveResult = true

        val result = repo.save(comment)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isSaved)
    }

    @Test
    fun save_unsavesSavedComment() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1", isSaved = true)
        api.unsaveResult = true

        val result = repo.save(comment)
        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().isSaved)
    }

    @Test
    fun save_failureReturnsError() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1", isSaved = false)
        api.saveResult = false

        val result = repo.save(comment)
        assertTrue(result.isFailure)
    }

    @Test
    fun save_exceptionReturnsFailure() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1")
        api.shouldThrow = Exception("Network error")

        val result = repo.save(comment)
        assertTrue(result.isFailure)
    }

    // ==================== submitComment ====================

    @Test
    fun submitComment_returnsSuccess() = runTest {
        val (repo, api) = createRepo()
        val newComment = createTestComment(id = "new1", body = "New comment")
        api.submitCommentResult = newComment

        val result = repo.submitComment("t3_parent", "New comment")
        assertTrue(result.isSuccess)
        assertEquals("new1", result.getOrThrow().id)
    }

    @Test
    fun submitComment_returnsFailureWhenNull() = runTest {
        val (repo, api) = createRepo()
        api.submitCommentResult = null

        val result = repo.submitComment("t3_parent", "text")
        assertTrue(result.isFailure)
    }

    @Test
    fun submitComment_returnsFailureOnException() = runTest {
        val (repo, api) = createRepo()
        api.shouldThrow = Exception("Network error")

        val result = repo.submitComment("t3_parent", "text")
        assertTrue(result.isFailure)
    }

    // ==================== editComment ====================

    @Test
    fun editComment_returnsUpdatedComment() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1", body = "old text")
        api.editCommentResult = true

        val result = repo.editComment(comment, "new text")
        assertTrue(result.isSuccess)
        assertEquals("new text", result.getOrThrow().body)
    }

    @Test
    fun editComment_failureReturnsError() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1")
        api.editCommentResult = false

        val result = repo.editComment(comment, "text")
        assertTrue(result.isFailure)
    }

    @Test
    fun editComment_exceptionReturnsFailure() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1")
        api.shouldThrow = Exception("Network error")

        val result = repo.editComment(comment, "text")
        assertTrue(result.isFailure)
    }

    // ==================== deleteComment ====================

    @Test
    fun deleteComment_returnsSuccess() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1")
        api.deleteCommentResult = true

        val result = repo.deleteComment(comment)
        assertTrue(result.isSuccess)
    }

    @Test
    fun deleteComment_failureReturnsError() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1")
        api.deleteCommentResult = false

        val result = repo.deleteComment(comment)
        assertTrue(result.isFailure)
    }

    @Test
    fun deleteComment_exceptionReturnsFailure() = runTest {
        val (repo, api) = createRepo()
        val comment = createTestComment(id = "c1")
        api.shouldThrow = Exception("Network error")

        val result = repo.deleteComment(comment)
        assertTrue(result.isFailure)
    }
}
