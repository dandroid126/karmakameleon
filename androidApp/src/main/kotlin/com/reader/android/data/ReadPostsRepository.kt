package com.reader.android.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReadPostsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _readPostIds = MutableStateFlow(loadReadPostIds())
    val readPostIds: StateFlow<Set<String>> = _readPostIds.asStateFlow()

    fun isRead(postId: String): Boolean = _readPostIds.value.contains(postId)

    fun markAsRead(postId: String) {
        val updated = _readPostIds.value + postId
        _readPostIds.value = updated
        prefs.edit().putStringSet(KEY_READ_POSTS, updated).apply()
    }

    private fun loadReadPostIds(): Set<String> {
        return prefs.getStringSet(KEY_READ_POSTS, emptySet()) ?: emptySet()
    }

    companion object {
        private const val PREFS_NAME = "read_posts_prefs"
        private const val KEY_READ_POSTS = "read_post_ids"
    }
}
