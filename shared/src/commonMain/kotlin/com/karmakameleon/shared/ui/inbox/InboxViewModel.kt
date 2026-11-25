package com.karmakameleon.shared.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karmakameleon.shared.data.repository.ActionRepository
import com.karmakameleon.shared.data.repository.InboxPoller
import com.karmakameleon.shared.data.repository.MessageRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.data.repository.withChanges
import com.karmakameleon.shared.domain.model.InboxFilter
import com.karmakameleon.shared.domain.model.Message
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InboxUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val currentFilter: InboxFilter = InboxFilter.ALL,
    val after: String? = null,
    val hasMore: Boolean = true,
    val isLoggedIn: Boolean = false,
    val selectedMessageId: String? = null,
    val replyingToMessage: Message? = null,
    val replyText: String = "",
)

class InboxViewModel(
    private val messageRepository: MessageRepository,
    private val actionRepository: ActionRepository,
    private val userRepository: UserRepository,
    private val inboxPoller: InboxPoller,
) : ViewModel() {
    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            userRepository.isLoggedIn.collect { isLoggedIn ->
                _uiState.update { it.copy(isLoggedIn = isLoggedIn) }
                if (isLoggedIn) {
                    loadMessages()
                }
            }
        }
        viewModelScope.launch {
            actionRepository.changes.collect { changes ->
                _uiState.update { state -> state.copy(messages = state.messages.map { it.withChanges(changes) }) }
            }
        }
        viewModelScope.launch {
            inboxPoller.newMessages.collect {
                loadMessages(forceRefresh = true)
            }
        }
    }

    fun loadMessages(forceRefresh: Boolean = false) {
        if (!_uiState.value.isLoggedIn) return
        if (_uiState.value.isLoading && !forceRefresh) return
        startLoading(forceRefresh)
    }

    // Cancels the load still running, so only the newest one sets the messages. Its answer could be for another filter.
    private fun startLoading(forceRefresh: Boolean) {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isLoading = !forceRefresh,
                        isRefreshing = forceRefresh,
                        error = null,
                    )
                }

                val result = messageRepository.getInbox(_uiState.value.currentFilter)

                result.fold(
                    onSuccess = { listing ->
                        _uiState.update {
                            it.copy(
                                messages = listing.items.map { message -> message.withChanges(actionRepository.changes.value) },
                                after = listing.after,
                                hasMore = listing.after != null,
                                isLoading = false,
                                isRefreshing = false,
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = error.message,
                            )
                        }
                    },
                )
            }
    }

    // A load still running is for the old filter, so it's replaced rather than waited for (#87). Opening the inbox from
    // a notification sets the Unread filter while the first load, of all messages, is running.
    fun setFilter(filter: InboxFilter) {
        if (_uiState.value.currentFilter == filter) return
        _uiState.update { it.copy(currentFilter = filter, messages = emptyList(), after = null) }
        if (_uiState.value.isLoggedIn) startLoading(forceRefresh = false)
    }

    // Opening a message marks it read, so this runs on every tap. Skip read ones, so a failure isn't reported for a
    // message that didn't change.
    fun markAsRead(message: Message) {
        if (!message.isNew) return
        actionRepository.setIsNew(message, isNew = false)
    }

    fun markAllAsRead() = actionRepository.markAllRead(_uiState.value.messages)

    fun selectMessage(id: String?) {
        _uiState.update { it.copy(selectedMessageId = if (it.selectedMessageId == id) null else id) }
    }

    fun voteOnMessage(
        message: Message,
        direction: Int,
    ) = actionRepository.vote(message, direction)

    fun markAsUnread(message: Message) {
        _uiState.update { it.copy(selectedMessageId = null) }
        actionRepository.setIsNew(message, isNew = true)
    }

    fun blockUser(username: String) {
        viewModelScope.launch {
            actionRepository.blockUser(username)
            _uiState.update { it.copy(selectedMessageId = null) }
        }
    }

    fun startReply(message: Message) {
        _uiState.update { it.copy(replyingToMessage = message, replyText = "", selectedMessageId = null) }
    }

    fun cancelReply() {
        _uiState.update { it.copy(replyingToMessage = null, replyText = "") }
    }

    fun setReplyText(text: String) {
        _uiState.update { it.copy(replyText = text) }
    }

    fun submitReply() {
        val message = _uiState.value.replyingToMessage ?: return
        val text = _uiState.value.replyText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            if (actionRepository.reply(message, text)) {
                _uiState.update { it.copy(replyingToMessage = null, replyText = "") }
            }
        }
    }
}
