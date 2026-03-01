package com.karmakameleon.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PendingQuote {
    private val _text = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = _text.asStateFlow()

    fun set(text: String) {
        _text.value = text
    }

    fun consume(): String? {
        val t = _text.value
        _text.value = null
        return t
    }
}
