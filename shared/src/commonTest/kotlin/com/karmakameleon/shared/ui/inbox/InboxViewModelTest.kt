package com.karmakameleon.shared.ui.inbox

import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.createTestComment
import com.karmakameleon.shared.createTestListing
import com.karmakameleon.shared.createTestMessage
import com.karmakameleon.shared.createTestUser
import com.karmakameleon.shared.data.repository.InboxPoller
import com.karmakameleon.shared.data.repository.MessageRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.domain.model.InboxFilter
import com.karmakameleon.shared.domain.model.Message
import com.karmakameleon.shared.testing.FakeAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val fakeAuthManager = FakeAuthManager()
    private lateinit var fakeApi: FakeRedditApi
    private lateinit var messageRepo: MessageRepository
    private lateinit var userRepo: UserRepository
    private lateinit var inboxPoller: InboxPoller
    private lateinit var viewModel: InboxViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeApi = FakeRedditApi(fakeAuthManager)
        messageRepo = MessageRepository(fakeApi)
        userRepo = UserRepository(fakeApi, fakeAuthManager)
        inboxPoller = InboxPoller(messageRepo, fakeAuthManager)
        viewModel = InboxViewModel(messageRepo, userRepo, inboxPoller)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Initialization ====================

    @Test
    fun init_setsDefaultState() {
        assertEquals(InboxFilter.ALL, viewModel.uiState.value.currentFilter)
        assertTrue(
            viewModel.uiState.value.messages
                .isEmpty(),
        )
    }

    // ==================== Loading Messages ====================

    @Test
    fun loadMessages_loggedOut_doesNotLoad() =
        runTest {
            fakeApi.inboxResult = createTestListing(items = listOf(createTestMessage(id = "msg1")))

            viewModel.loadMessages()
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.messages
                    .isEmpty(),
            )
        }

    @Test
    fun loadMessages_replacesMessages() =
        runTest {
            logInWithMessages(createTestMessage(id = "msg1"))
            fakeApi.inboxResult = createTestListing(items = listOf(createTestMessage(id = "msg2")))

            viewModel.loadMessages()
            advanceUntilIdle()

            assertEquals(
                listOf("msg2"),
                viewModel.uiState.value.messages
                    .map { it.id },
            )
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun loadMessages_forceRefresh_replacesMessages() =
        runTest {
            logInWithMessages(createTestMessage(id = "msg1"))
            fakeApi.inboxResult = createTestListing(items = listOf(createTestMessage(id = "msg2")))

            viewModel.loadMessages(forceRefresh = true)
            advanceUntilIdle()

            assertEquals(
                listOf("msg2"),
                viewModel.uiState.value.messages
                    .map { it.id },
            )
            assertFalse(viewModel.uiState.value.isRefreshing)
        }

    @Test
    fun loadMessages_clearsErrorOnNewLoad() =
        runTest {
            logInWithMessages(createTestMessage(id = "msg1"))
            fakeApi.shouldThrow = IllegalStateException("Inbox failed")
            viewModel.loadMessages()
            advanceUntilIdle()
            assertEquals("Inbox failed", viewModel.uiState.value.error)

            fakeApi.shouldThrow = null
            viewModel.loadMessages()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.error)
        }

    // ==================== Filtering ====================

    @Test
    fun setFilter_loadsThatFilter() =
        runTest {
            logInWithMessages(createTestMessage(id = "msg1"))
            fakeApi.inboxResult = createTestListing(items = listOf(createTestMessage(id = "msg2")), after = "t4_msg2")

            viewModel.setFilter(InboxFilter.UNREAD)
            viewModel.setFilter(InboxFilter.UNREAD)
            advanceUntilIdle()

            assertEquals(listOf(InboxFilter.ALL, InboxFilter.UNREAD), fakeApi.inboxRequests)
            val state = viewModel.uiState.value
            assertEquals(InboxFilter.UNREAD, state.currentFilter)
            assertEquals(listOf("msg2"), state.messages.map { it.id })
            assertEquals("t4_msg2", state.after)
            assertTrue(state.hasMore)
        }

    @Test
    fun loadMessages_skipsALoadWhileOneIsRunningUnlessForced() =
        runTest {
            logInWithMessages()
            fakeApi.inboxRequests.clear()
            fakeApi.responseDelayMillis = 1000

            viewModel.loadMessages()
            runCurrent()
            viewModel.loadMessages()
            runCurrent()
            viewModel.loadMessages(forceRefresh = true)
            advanceUntilIdle()

            assertEquals(2, fakeApi.inboxRequests.size)
        }

    @Test
    fun newMessages_reloadTheInbox() =
        runTest {
            val poller = InboxPoller(messageRepo, fakeAuthManager, backgroundScope)
            val viewModel = InboxViewModel(messageRepo, userRepo, poller)
            logInWithMessages(createTestMessage(id = "msg1"))
            fakeAuthManager.fakeIsLoggedIn = true
            poller.start()
            runCurrent()
            fakeApi.inboxResult = createTestListing(items = listOf(createTestMessage(id = "msg1"), createTestMessage(id = "msg2")))

            advanceTimeBy(InboxPoller.FOREGROUND_INTERVAL_SECONDS * 1000 + 1)
            poller.stop()
            advanceUntilIdle()

            assertEquals(
                listOf("msg1", "msg2"),
                viewModel.uiState.value.messages
                    .map { it.id },
            )
        }

    // ==================== Selection ====================

    @Test
    fun selectMessage_togglesSelectedMessageId() =
        runTest {
            logInWithMessages(createTestMessage(id = "msg1"))

            viewModel.selectMessage("msg1")

            assertEquals("msg1", viewModel.uiState.value.selectedMessageId)
        }

    @Test
    fun selectMessage_togglesOff() =
        runTest {
            logInWithMessages(createTestMessage(id = "msg1"))

            viewModel.selectMessage("msg1")
            viewModel.selectMessage("msg1")

            assertNull(viewModel.uiState.value.selectedMessageId)
        }

    // ==================== Reply ====================

    @Test
    fun startReply_setsReplyingToMessage() =
        runTest {
            logInWithMessages(createTestMessage(id = "msg1"))
            val message =
                viewModel.uiState.value.messages
                    .first()

            viewModel.startReply(message)

            assertEquals(message, viewModel.uiState.value.replyingToMessage)
        }

    @Test
    fun cancelReply_clearsReply() =
        runTest {
            logInWithMessages(createTestMessage(id = "msg1"))

            viewModel.startReply(
                viewModel.uiState.value.messages
                    .first(),
            )
            viewModel.cancelReply()

            assertNull(viewModel.uiState.value.replyingToMessage)
        }

    @Test
    fun setReplyText_updatesReplyText() =
        runTest {
            viewModel.setReplyText("Test reply")
            assertEquals("Test reply", viewModel.uiState.value.replyText)
        }

    @Test
    fun setReplyText_emptyString() =
        runTest {
            viewModel.setReplyText("Test")
            viewModel.setReplyText("")
            assertEquals("", viewModel.uiState.value.replyText)
        }

    // ==================== Mark As Read ====================

    @Test
    fun markAsRead_updatesMessage() =
        runTest {
            logInWithMessages(createTestMessage(id = "msg1", isNew = true), createTestMessage(id = "msg2", isNew = true))

            viewModel.markAsRead(
                viewModel.uiState.value.messages
                    .first(),
            )
            advanceUntilIdle()

            assertEquals(
                listOf(false, true),
                viewModel.uiState.value.messages
                    .map { it.isNew },
            )
        }

    @Test
    fun markAllAsRead_updatesAllMessages() =
        runTest {
            logInWithMessages(createTestMessage(id = "msg1", isNew = true), createTestMessage(id = "msg2", isNew = true))

            viewModel.markAllAsRead()
            advanceUntilIdle()

            assertEquals(
                listOf(false, false),
                viewModel.uiState.value.messages
                    .map { it.isNew },
            )
        }

    // ==================== Voting ====================

    @Test
    fun voteOnMessage_updatesMessage() =
        runTest {
            logInWithMessages(createTestMessage(id = "msg1", likes = null), createTestMessage(id = "msg2", likes = null))

            viewModel.voteOnMessage(
                viewModel.uiState.value.messages
                    .first(),
                1,
            )
            advanceUntilIdle()

            assertEquals(
                listOf(true, null),
                viewModel.uiState.value.messages
                    .map { it.likes },
            )
        }

    @Test
    fun voteOnMessage_keepsTheMessageWhenItFails() =
        runTest {
            logInWithMessages(createTestMessage(id = "msg1", likes = null))
            fakeApi.voteResult = false

            viewModel.voteOnMessage(
                viewModel.uiState.value.messages
                    .first(),
                1,
            )
            advanceUntilIdle()

            assertNull(
                viewModel.uiState.value.messages
                    .single()
                    .likes,
            )
        }

    // ==================== Unread and blocking ====================

    @Test
    fun markAsUnread_marksItAndClosesTheMessage() =
        runTest {
            logInWithMessages(createTestMessage(id = "msg1", name = "t4_msg1"), createTestMessage(id = "msg2", name = "t4_msg2"))
            viewModel.selectMessage("msg1")

            viewModel.markAsUnread(
                viewModel.uiState.value.messages
                    .first(),
            )
            advanceUntilIdle()

            assertEquals(listOf("t4_msg1"), fakeApi.messagesMarkedUnread)
            assertEquals(
                listOf(true, false),
                viewModel.uiState.value.messages
                    .map { it.isNew },
            )
            assertNull(viewModel.uiState.value.selectedMessageId)
        }

    @Test
    fun blockUser_blocksAndClosesTheMessage() =
        runTest {
            logInWithMessages(createTestMessage(id = "msg1"))
            viewModel.selectMessage("msg1")

            viewModel.blockUser("sender")
            advanceUntilIdle()

            assertEquals(listOf("sender"), fakeApi.blockedUsers)
            assertNull(viewModel.uiState.value.selectedMessageId)
        }

    // ==================== Sending a reply ====================

    @Test
    fun submitReply_sendsItAndClosesTheReply() =
        runTest {
            logInWithMessages(createTestMessage(id = "msg1", name = "t4_msg1"))
            fakeApi.submitCommentResult = createTestComment(id = "r1")
            viewModel.startReply(
                viewModel.uiState.value.messages
                    .first(),
            )
            viewModel.setReplyText("  Thanks  ")

            viewModel.submitReply()
            advanceUntilIdle()

            assertEquals(listOf("t4_msg1" to "Thanks"), fakeApi.submittedComments)
            assertNull(viewModel.uiState.value.replyingToMessage)
            assertEquals("", viewModel.uiState.value.replyText)
        }

    @Test
    fun submitReply_keepsTheReplyWhenItFails() =
        runTest {
            logInWithMessages(createTestMessage(id = "msg1"))
            viewModel.startReply(
                viewModel.uiState.value.messages
                    .first(),
            )
            viewModel.setReplyText("Thanks")

            viewModel.submitReply()
            advanceUntilIdle()

            assertEquals(
                "msg1",
                viewModel.uiState.value.replyingToMessage
                    ?.id,
            )
            assertEquals("Thanks", viewModel.uiState.value.replyText)
        }

    @Test
    fun submitReply_skipsABlankReplyOrNoMessage() =
        runTest {
            logInWithMessages(createTestMessage(id = "msg1"))
            viewModel.setReplyText("Thanks")
            viewModel.submitReply()
            viewModel.startReply(
                viewModel.uiState.value.messages
                    .first(),
            )
            viewModel.setReplyText("  ")
            viewModel.submitReply()
            advanceUntilIdle()

            assertTrue(fakeApi.submittedComments.isEmpty())
        }

    // Logs in with [messages] in the inbox. The view model loads the inbox once the user is logged in.
    private suspend fun TestScope.logInWithMessages(vararg messages: Message) {
        fakeApi.inboxResult = createTestListing(items = messages.toList())
        fakeApi.meResult = createTestUser()
        userRepo.loadCurrentUser()
        advanceUntilIdle()
    }
}
