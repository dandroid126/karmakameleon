package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.createTestComment
import com.karmakameleon.shared.util.markdown.MarkdownParser
import com.karmakameleon.shared.util.markdown.parseMarkdownBlocks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommentRepositoryTest {
    // Parses as soon as it's asked to, so the texts are cached by the time a call returns.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val markdown = MarkdownParser(UnconfinedTestDispatcher())

    private fun createRepo(api: FakeRedditApi = FakeRedditApi()) = CommentRepository(api, markdown) to api

    // ==================== submitComment ====================

    @Test
    fun submitComment_returnsSuccess() =
        runTest {
            val (repo, api) = createRepo()
            val newComment = createTestComment(id = "new1", body = "New comment")
            api.submitCommentResult = newComment

            val result = repo.submitComment("t3_parent", "New comment")
            assertTrue(result.isSuccess)
            assertEquals("new1", result.getOrThrow().id)
        }

    @Test
    fun submitComment_returnsFailureWhenNull() =
        runTest {
            val (repo, api) = createRepo()
            api.submitCommentResult = null

            val result = repo.submitComment("t3_parent", "text")
            assertTrue(result.isFailure)
        }

    @Test
    fun submitComment_returnsFailureOnException() =
        runTest {
            val (repo, api) = createRepo()
            api.shouldThrow = Exception("Network error")

            val result = repo.submitComment("t3_parent", "text")
            assertTrue(result.isFailure)
        }

    // ==================== editComment ====================

    @Test
    fun editComment_returnsUpdatedComment() =
        runTest {
            val (repo, api) = createRepo()
            val comment = createTestComment(id = "c1", body = "old text")
            api.editCommentResult = true

            val result = repo.editComment(comment, "new text")
            assertTrue(result.isSuccess)
            assertEquals("new text", result.getOrThrow().body)
            // So the comment shows its new text on its first frame.
            assertEquals(parseMarkdownBlocks("new text"), result.getOrThrow().bodyBlocks)
        }

    @Test
    fun editComment_failureReturnsError() =
        runTest {
            val (repo, api) = createRepo()
            val comment = createTestComment(id = "c1")
            api.editCommentResult = false

            val result = repo.editComment(comment, "text")
            assertTrue(result.isFailure)
        }

    @Test
    fun editComment_exceptionReturnsFailure() =
        runTest {
            val (repo, api) = createRepo()
            val comment = createTestComment(id = "c1")
            api.shouldThrow = Exception("Network error")

            val result = repo.editComment(comment, "text")
            assertTrue(result.isFailure)
        }

    // ==================== deleteComment ====================

    @Test
    fun deleteComment_returnsSuccess() =
        runTest {
            val (repo, api) = createRepo()
            val comment = createTestComment(id = "c1")
            api.deleteCommentResult = true

            val result = repo.deleteComment(comment)
            assertTrue(result.isSuccess)
        }

    @Test
    fun deleteComment_failureReturnsError() =
        runTest {
            val (repo, api) = createRepo()
            val comment = createTestComment(id = "c1")
            api.deleteCommentResult = false

            val result = repo.deleteComment(comment)
            assertTrue(result.isFailure)
        }

    @Test
    fun deleteComment_exceptionReturnsFailure() =
        runTest {
            val (repo, api) = createRepo()
            val comment = createTestComment(id = "c1")
            api.shouldThrow = Exception("Network error")

            val result = repo.deleteComment(comment)
            assertTrue(result.isFailure)
        }
}
