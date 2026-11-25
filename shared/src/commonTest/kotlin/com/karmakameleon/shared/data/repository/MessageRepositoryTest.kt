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
    fun getInbox_returnsSuccess() =
        runTest {
            val (repo, api) = createRepo()
            val messages = listOf(createTestMessage(id = "m1"), createTestMessage(id = "m2"))
            api.inboxResult = createTestListing(items = messages)

            val result = repo.getInbox()
            assertTrue(result.isSuccess)
            assertEquals(2, result.getOrThrow().items.size)
        }

    @Test
    fun getInbox_withFilter() =
        runTest {
            val (repo, api) = createRepo()
            api.inboxResult = createTestListing(items = listOf(createTestMessage(id = "m1", isNew = true)))

            val result = repo.getInbox(InboxFilter.UNREAD)
            assertTrue(result.isSuccess)
        }

    @Test
    fun getInbox_returnsFailureOnException() =
        runTest {
            val (repo, api) = createRepo()
            api.shouldThrow = Exception("Network error")

            val result = repo.getInbox()
            assertTrue(result.isFailure)
        }

    // ==================== sendMessage ====================

    @Test
    fun sendMessage_returnsSuccess() =
        runTest {
            val (repo, api) = createRepo()
            api.sendMessageResult = true

            val result = repo.sendMessage("recipient", "Subject", "Body")
            assertTrue(result.isSuccess)
        }

    @Test
    fun sendMessage_returnsFailureWhenFalse() =
        runTest {
            val (repo, api) = createRepo()
            api.sendMessageResult = false

            val result = repo.sendMessage("recipient", "Subject", "Body")
            assertTrue(result.isFailure)
        }

    @Test
    fun sendMessage_returnsFailureOnException() =
        runTest {
            val (repo, api) = createRepo()
            api.shouldThrow = Exception("Network error")

            val result = repo.sendMessage("recipient", "Subject", "Body")
            assertTrue(result.isFailure)
        }
}
