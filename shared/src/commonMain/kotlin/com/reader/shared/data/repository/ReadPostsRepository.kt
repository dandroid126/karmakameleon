package com.reader.shared.data.repository

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReadPostsRepository(private val settings: Settings) {

    private val _readPostIds = MutableStateFlow(loadReadPostIds())
    val readPostIds: StateFlow<Set<String>> = _readPostIds.asStateFlow()

    fun isRead(postId: String): Boolean = _readPostIds.value.contains(postId)

    fun markAsRead(postId: String) {
        val updated = _readPostIds.value + postId
        _readPostIds.value = updated
        saveReadPostIds(updated)
    }

    private fun loadReadPostIds(): Set<String> {
        val stored = settings.getStringOrNull(KEY_READ_POSTS) ?: return emptySet()
        return stored.split(",").filter { it.isNotBlank() }.toSet()
    }

    private fun saveReadPostIds(postIds: Set<String>) {
        settings[KEY_READ_POSTS] = postIds.joinToString(",")
    }

    companion object {
        private const val KEY_READ_POSTS = "read_post_ids"
    }
}
