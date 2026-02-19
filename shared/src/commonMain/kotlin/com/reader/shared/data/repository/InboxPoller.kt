package com.reader.shared.data.repository

import com.reader.shared.data.api.AuthManager
import com.reader.shared.domain.model.InboxFilter
import com.reader.shared.domain.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class InboxPoller(
    private val messageRepository: MessageRepository,
    private val authManager: AuthManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    private val _newMessages = MutableSharedFlow<List<Message>>(replay = 0)
    val newMessages: SharedFlow<List<Message>> = _newMessages.asSharedFlow()

    private var knownIds: Set<String> = emptySet()
    private var initialized = false

    fun start(intervalSeconds: Long = FOREGROUND_INTERVAL_SECONDS) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                poll()
                delay(intervalSeconds * 1_000L)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun poll() {
        if (!authManager.isLoggedIn()) return
        val messages = messageRepository.getInbox(InboxFilter.UNREAD)
            .getOrNull()?.items ?: return
        val currentIds = messages.map { it.id }.toSet()
        if (!initialized) {
            knownIds = currentIds
            initialized = true
            return
        }
        val newMessages = messages.filter { it.id !in knownIds }
        knownIds = currentIds
        if (newMessages.isNotEmpty()) {
            _newMessages.emit(newMessages)
        }
    }

    companion object {
        const val FOREGROUND_INTERVAL_SECONDS = 60L
    }
}
