package com.karmakameleon.shared.ui.inbox

import com.karmakameleon.shared.FakeAuthManager
import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.createTestListing
import com.karmakameleon.shared.createTestMessage
import com.karmakameleon.shared.createTestUser
import com.karmakameleon.shared.data.repository.InboxPoller
import com.karmakameleon.shared.data.repository.MessageRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.domain.model.InboxFilter
import com.karmakameleon.shared.domain.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
    private lateinit var fakeApi: FakeRedditApi
    private lateinit var messageRepo: MessageRepository
    private lateinit var userRepo: UserRepository
    private lateinit var inboxPoller: InboxPoller
    private lateinit var viewModel: InboxViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val fakeAuthManager = FakeAuthManager()
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
    fun setFilter_changesCurrentFilter() =
        runTest {
            viewModel.setFilter(InboxFilter.UNREAD)
            advanceUntilIdle()
            assertEquals(InboxFilter.UNREAD, viewModel.uiState.value.currentFilter)
        }

    @Test
    fun setFilter_preventsDuplicateFilter() =
        runTest {
            viewModel.setFilter(InboxFilter.ALL)
            advanceUntilIdle()

            viewModel.setFilter(InboxFilter.ALL)
            advanceUntilIdle()

            assertEquals(InboxFilter.ALL, viewModel.uiState.value.currentFilter)
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

    // Logs in with [messages] in the inbox. The view model loads the inbox once the user is logged in.
    private suspend fun TestScope.logInWithMessages(vararg messages: Message) {
        fakeApi.inboxResult = createTestListing(items = messages.toList())
        fakeApi.meResult = createTestUser()
        userRepo.loadCurrentUser()
        advanceUntilIdle()
    }
}
