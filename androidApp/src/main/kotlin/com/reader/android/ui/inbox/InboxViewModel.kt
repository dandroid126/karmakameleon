package com.reader.android.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.shared.data.repository.MessageRepository
import com.reader.shared.data.repository.UserRepository
import com.reader.shared.domain.model.InboxFilter
import com.reader.shared.domain.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InboxUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentFilter: InboxFilter = InboxFilter.ALL,
    val after: String? = null,
    val hasMore: Boolean = true,
    val isLoggedIn: Boolean = false
)

class InboxViewModel(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.isLoggedIn.collect { isLoggedIn ->
                _uiState.update { it.copy(isLoggedIn = isLoggedIn) }
                if (isLoggedIn) {
                    loadMessages()
                }
            }
        }
    }

    fun loadMessages(forceRefresh: Boolean = false) {
        if (!_uiState.value.isLoggedIn) return
        if (_uiState.value.isLoading && !forceRefresh) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = messageRepository.getInbox(_uiState.value.currentFilter)

            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            messages = listing.items,
                            after = listing.after,
                            hasMore = listing.after != null,
                            isLoading = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message)
                    }
                }
            )
        }
    }

    fun setFilter(filter: InboxFilter) {
        if (_uiState.value.currentFilter == filter) return
        _uiState.update { it.copy(currentFilter = filter, messages = emptyList(), after = null) }
        loadMessages()
    }

    fun markAsRead(message: Message) {
        viewModelScope.launch {
            messageRepository.markAsRead(message)
            _uiState.update { state ->
                state.copy(
                    messages = state.messages.map {
                        if (it.id == message.id) it.copy(isNew = false) else it
                    }
                )
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            messageRepository.markAllAsRead()
            _uiState.update { state ->
                state.copy(messages = state.messages.map { it.copy(isNew = false) })
            }
        }
    }
}
