package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.FakeAuthManager
import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.createTestListing
import com.karmakameleon.shared.createTestMessage
import com.karmakameleon.shared.domain.model.Message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InboxPollerTest {
    private val authManager = FakeAuthManager().apply { fakeIsLoggedIn = true }
    private val fakeApi = FakeRedditApi(authManager)

    // Starts a poller on the test's clock, and collects what it reports.
    private fun TestScope.startPoller(): Pair<InboxPoller, List<List<String>>> {
        val poller = InboxPoller(MessageRepository(fakeApi), authManager, backgroundScope)
        val reported = mutableListOf<List<String>>()
        backgroundScope.launch { poller.newMessages.collect { messages -> reported += messages.map { it.id } } }
        runCurrent()
        poller.start(INTERVAL_SECONDS)
        runCurrent()
        return poller to reported
    }

    private fun unread(vararg messages: Message) {
        fakeApi.inboxResult = createTestListing(items = messages.toList())
    }

    private fun TestScope.nextPoll() {
        advanceTimeBy(INTERVAL_SECONDS * 1000)
        runCurrent()
    }

    @Test
    fun reportsOnlyMessagesThatArriveAfterTheFirstPoll() =
        runTest {
            unread(createTestMessage(id = "old"))
            val (_, reported) = startPoller()

            unread(createTestMessage(id = "old"), createTestMessage(id = "new1"), createTestMessage(id = "new2"))
            nextPoll()
            nextPoll()
            unread(createTestMessage(id = "new3"))
            nextPoll()

            assertEquals(listOf(listOf("new1", "new2"), listOf("new3")), reported)
        }

    @Test
    fun aMessageReadAndThenUnreadAgainIsReportedAgain() =
        runTest {
            unread(createTestMessage(id = "m1"))
            val (_, reported) = startPoller()

            unread()
            nextPoll()
            unread(createTestMessage(id = "m1"))
            nextPoll()

            assertEquals(listOf(listOf("m1")), reported)
        }

    @Test
    fun aFailedPollIsSkipped() =
        runTest {
            val (_, reported) = startPoller()
            fakeApi.shouldThrow = Exception("No connection")
            unread(createTestMessage(id = "m1"))

            nextPoll()
            fakeApi.shouldThrow = null
            nextPoll()

            assertEquals(listOf(listOf("m1")), reported)
        }

    @Test
    fun doesNotPollWhileLoggedOut() =
        runTest {
            authManager.fakeIsLoggedIn = false
            val (_, reported) = startPoller()
            unread(createTestMessage(id = "m1"))
            nextPoll()

            // The first poll after logging in only learns what's already there.
            authManager.fakeIsLoggedIn = true
            nextPoll()
            unread(createTestMessage(id = "m1"), createTestMessage(id = "m2"))
            nextPoll()

            assertEquals(listOf(listOf("m2")), reported)
        }

    @Test
    fun stopsPolling() =
        runTest {
            val (poller, reported) = startPoller()

            poller.stop()
            unread(createTestMessage(id = "m1"))
            nextPoll()
            nextPoll()

            assertTrue(reported.isEmpty())
        }

    @Test
    fun startingAgainReplacesTheRunningPoll() =
        runTest {
            val (poller, reported) = startPoller()

            poller.start(INTERVAL_SECONDS * 10)
            runCurrent()
            unread(createTestMessage(id = "m1"))

            // The first loop would have polled by now.
            nextPoll()
            assertTrue(reported.isEmpty())

            advanceTimeBy(INTERVAL_SECONDS * 9 * 1000)
            runCurrent()
            assertEquals(listOf(listOf("m1")), reported)
        }

    private companion object {
        const val INTERVAL_SECONDS = 60L
    }
}
