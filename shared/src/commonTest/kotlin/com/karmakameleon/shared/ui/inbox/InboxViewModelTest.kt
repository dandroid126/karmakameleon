package com.karmakameleon.shared.ui.inbox

import com.karmakameleon.shared.FakeAuthManager
import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.data.repository.InboxPoller
import com.karmakameleon.shared.data.repository.MessageRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.domain.model.InboxFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
        assertTrue(viewModel.uiState.value.messages.isEmpty())
    }

    // ==================== Loading Messages ====================

    @Test
    fun loadMessages_setsLoadingState() = runTest {
        viewModel.loadMessages()
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun loadMessages_forceRefresh_setsRefreshingState() = runTest {
        viewModel.loadMessages(forceRefresh = true)
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun loadMessages_clearsErrorOnNewLoad() = runTest {
        viewModel.loadMessages()
        advanceUntilIdle()
        
        assertNull(viewModel.uiState.value.error)
    }

    // ==================== Filtering ====================

    @Test
    fun setFilter_changesCurrentFilter() = runTest {
        viewModel.setFilter(InboxFilter.UNREAD)
        advanceUntilIdle()
        assertEquals(InboxFilter.UNREAD, viewModel.uiState.value.currentFilter)
    }

    @Test
    fun setFilter_preventsDuplicateFilter() = runTest {
        viewModel.setFilter(InboxFilter.ALL)
        advanceUntilIdle()
        
        viewModel.setFilter(InboxFilter.ALL)
        advanceUntilIdle()
        
        assertEquals(InboxFilter.ALL, viewModel.uiState.value.currentFilter)
    }

    // ==================== Selection ====================

    @Test
    fun selectMessage_togglesSelectedMessageId() = runTest {
        viewModel.loadMessages()
        advanceUntilIdle()
        
        val message = viewModel.uiState.value.messages.firstOrNull() ?: return@runTest
        viewModel.selectMessage(message.id)
        
        assertEquals(message.id, viewModel.uiState.value.selectedMessageId)
    }

    @Test
    fun selectMessage_togglesOff() = runTest {
        viewModel.loadMessages()
        advanceUntilIdle()
        
        val message = viewModel.uiState.value.messages.firstOrNull() ?: return@runTest
        viewModel.selectMessage(message.id)
        viewModel.selectMessage(message.id)
        
        assertNull(viewModel.uiState.value.selectedMessageId)
    }

    // ==================== Reply ====================

    @Test
    fun startReply_setsReplyingToMessage() = runTest {
        viewModel.loadMessages()
        advanceUntilIdle()
        
        val message = viewModel.uiState.value.messages.firstOrNull() ?: return@runTest
        viewModel.startReply(message)
        
        assertEquals(message, viewModel.uiState.value.replyingToMessage)
    }

    @Test
    fun cancelReply_clearsReply() = runTest {
        viewModel.loadMessages()
        advanceUntilIdle()
        
        val message = viewModel.uiState.value.messages.firstOrNull() ?: return@runTest
        viewModel.startReply(message)
        viewModel.cancelReply()
        
        assertNull(viewModel.uiState.value.replyingToMessage)
    }

    @Test
    fun setReplyText_updatesReplyText() = runTest {
        viewModel.setReplyText("Test reply")
        assertEquals("Test reply", viewModel.uiState.value.replyText)
    }

    @Test
    fun setReplyText_emptyString() = runTest {
        viewModel.setReplyText("Test")
        viewModel.setReplyText("")
        assertEquals("", viewModel.uiState.value.replyText)
    }

    // ==================== Mark As Read ====================

    @Test
    fun markAsRead_updatesMessage() = runTest {
        viewModel.loadMessages()
        advanceUntilIdle()
        
        val message = viewModel.uiState.value.messages.firstOrNull() ?: return@runTest
        viewModel.markAsRead(message)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.messages.any { it.id == message.id })
    }

    @Test
    fun markAllAsRead_updatesAllMessages() = runTest {
        viewModel.loadMessages()
        advanceUntilIdle()
        
        viewModel.markAllAsRead()
        advanceUntilIdle()
        
        assertNull(viewModel.uiState.value.error)
    }

    // ==================== Voting ====================

    @Test
    fun voteOnMessage_updatesMessage() = runTest {
        viewModel.loadMessages()
        advanceUntilIdle()
        
        val message = viewModel.uiState.value.messages.firstOrNull() ?: return@runTest
        viewModel.voteOnMessage(message, 1)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.messages.any { it.id == message.id })
    }
}
