package com.reader.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PendingNotificationAction {
    private val _openInboxUnread = MutableStateFlow(false)
    val openInboxUnread: StateFlow<Boolean> = _openInboxUnread.asStateFlow()

    fun triggerOpenInboxUnread() {
        _openInboxUnread.value = true
    }

    fun consumeOpenInboxUnread() {
        _openInboxUnread.value = false
    }
}
