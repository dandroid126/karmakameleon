package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.createTestComment
import com.karmakameleon.shared.createTestListing
import com.karmakameleon.shared.createTestMessage
import com.karmakameleon.shared.domain.model.InboxFilter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageRepositoryTest {

    private fun createRepo(api: FakeRedditApi = FakeRedditApi()) = MessageRepository(api) to api

    // ==================== getInbox ====================

    @Test
    fun getInbox_returnsSuccess() = runTest {
        val (repo, api) = createRepo()
        val messages = listOf(createTestMessage(id = "m1"), createTestMessage(id = "m2"))
        api.inboxResult = createTestListing(items = messages)

        val result = repo.getInbox()
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().items.size)
    }

    @Test
    fun getInbox_withFilter() = runTest {
        val (repo, api) = createRepo()
        api.inboxResult = createTestListing(items = listOf(createTestMessage(id = "m1", isNew = true)))

        val result = repo.getInbox(InboxFilter.UNREAD)
        assertTrue(result.isSuccess)
    }

    @Test
    fun getInbox_returnsFailureOnException() = runTest {
        val (repo, api) = createRepo()
        api.shouldThrow = Exception("Network error")

        val result = repo.getInbox()
        assertTrue(result.isFailure)
    }

    // ==================== sendMessage ====================

    @Test
    fun sendMessage_returnsSuccess() = runTest {
        val (repo, api) = createRepo()
        api.sendMessageResult = true

        val result = repo.sendMessage("recipient", "Subject", "Body")
        assertTrue(result.isSuccess)
    }

    @Test
    fun sendMessage_returnsFailureWhenFalse() = runTest {
        val (repo, api) = createRepo()
        api.sendMessageResult = false

        val result = repo.sendMessage("recipient", "Subject", "Body")
        assertTrue(result.isFailure)
    }

    @Test
    fun sendMessage_returnsFailureOnException() = runTest {
        val (repo, api) = createRepo()
        api.shouldThrow = Exception("Network error")

        val result = repo.sendMessage("recipient", "Subject", "Body")
        assertTrue(result.isFailure)
    }

    // ==================== markAsRead ====================

    @Test
    fun markAsRead_returnsSuccess() = runTest {
        val (repo, api) = createRepo()
        val message = createTestMessage(id = "m1", isNew = true)
        api.markMessageReadResult = true

        val result = repo.markAsRead(message)
        assertTrue(result.isSuccess)
    }

    @Test
    fun markAsRead_returnsFailureWhenFalse() = runTest {
        val (repo, api) = createRepo()
        val message = createTestMessage(id = "m1")
        api.markMessageReadResult = false

        val result = repo.markAsRead(message)
        assertTrue(result.isFailure)
    }

    // ==================== markAllAsRead ====================

    @Test
    fun markAllAsRead_returnsSuccess() = runTest {
        val (repo, api) = createRepo()
        api.markAllMessagesReadResult = true

        val result = repo.markAllAsRead()
        assertTrue(result.isSuccess)
    }

    @Test
    fun markAllAsRead_returnsFailureWhenFalse() = runTest {
        val (repo, api) = createRepo()
        api.markAllMessagesReadResult = false

        val result = repo.markAllAsRead()
        assertTrue(result.isFailure)
    }

    // ==================== markAsUnread ====================

    @Test
    fun markAsUnread_returnsSuccess() = runTest {
        val (repo, api) = createRepo()
        val message = createTestMessage(id = "m1")
        api.markMessageUnreadResult = true

        val result = repo.markAsUnread(message)
        assertTrue(result.isSuccess)
    }

    @Test
    fun markAsUnread_returnsFailureWhenFalse() = runTest {
        val (repo, api) = createRepo()
        val message = createTestMessage(id = "m1")
        api.markMessageUnreadResult = false

        val result = repo.markAsUnread(message)
        assertTrue(result.isFailure)
    }

    // ==================== blockUser ====================

    @Test
    fun blockUser_returnsSuccess() = runTest {
        val (repo, api) = createRepo()
        api.blockUserResult = true

        val result = repo.blockUser("spammer")
        assertTrue(result.isSuccess)
    }

    @Test
    fun blockUser_returnsFailureWhenFalse() = runTest {
        val (repo, api) = createRepo()
        api.blockUserResult = false

        val result = repo.blockUser("spammer")
        assertTrue(result.isFailure)
    }

    // ==================== voteOnMessage ====================

    @Test
    fun voteOnMessage_upvote() = runTest {
        val (repo, api) = createRepo()
        val message = createTestMessage(id = "m1", likes = null)
        api.voteResult = true

        val result = repo.voteOnMessage(message, 1)
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrThrow().likes)
    }

    @Test
    fun voteOnMessage_downvote() = runTest {
        val (repo, api) = createRepo()
        val message = createTestMessage(id = "m1", likes = null)
        api.voteResult = true

        val result = repo.voteOnMessage(message, -1)
        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrThrow().likes)
    }

    @Test
    fun voteOnMessage_removeVote() = runTest {
        val (repo, api) = createRepo()
        val message = createTestMessage(id = "m1", likes = true)
        api.voteResult = true

        val result = repo.voteOnMessage(message, 0)
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow().likes)
    }

    @Test
    fun voteOnMessage_failureReturnsError() = runTest {
        val (repo, api) = createRepo()
        val message = createTestMessage(id = "m1")
        api.voteResult = false

        val result = repo.voteOnMessage(message, 1)
        assertTrue(result.isFailure)
    }

    // ==================== replyToMessage ====================

    @Test
    fun replyToMessage_returnsSuccess() = runTest {
        val (repo, api) = createRepo()
        val message = createTestMessage(id = "m1")
        val reply = createTestComment(id = "reply1", body = "Reply text")
        api.submitCommentResult = reply

        val result = repo.replyToMessage(message, "Reply text")
        assertTrue(result.isSuccess)
        assertEquals("reply1", result.getOrThrow().id)
    }

    @Test
    fun replyToMessage_returnsFailureWhenNull() = runTest {
        val (repo, api) = createRepo()
        val message = createTestMessage(id = "m1")
        api.submitCommentResult = null

        val result = repo.replyToMessage(message, "Reply text")
        assertTrue(result.isFailure)
    }

    @Test
    fun replyToMessage_returnsFailureOnException() = runTest {
        val (repo, api) = createRepo()
        val message = createTestMessage(id = "m1")
        api.shouldThrow = Exception("Network error")

        val result = repo.replyToMessage(message, "Reply text")
        assertTrue(result.isFailure)
    }
}
