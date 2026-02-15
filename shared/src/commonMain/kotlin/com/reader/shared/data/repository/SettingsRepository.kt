package com.reader.shared.data.repository

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(private val settings: Settings) {

    private val _inlineImagesEnabled = MutableStateFlow(settings.getBoolean(KEY_INLINE_IMAGES, true))
    val inlineImagesEnabled: StateFlow<Boolean> = _inlineImagesEnabled.asStateFlow()

    private val _blockedSubreddits = MutableStateFlow(loadBlockedSubreddits())
    val blockedSubreddits: StateFlow<Set<String>> = _blockedSubreddits.asStateFlow()

    fun setInlineImagesEnabled(enabled: Boolean) {
        _inlineImagesEnabled.value = enabled
        settings[KEY_INLINE_IMAGES] = enabled
    }

    fun addBlockedSubreddit(subreddit: String) {
        val updated = _blockedSubreddits.value + subreddit.lowercase()
        _blockedSubreddits.value = updated
        saveBlockedSubreddits(updated)
    }

    fun removeBlockedSubreddit(subreddit: String) {
        val updated = _blockedSubreddits.value - subreddit.lowercase()
        _blockedSubreddits.value = updated
        saveBlockedSubreddits(updated)
    }

    private fun loadBlockedSubreddits(): Set<String> {
        val stored = settings.getStringOrNull(KEY_BLOCKED_SUBREDDITS) ?: return emptySet()
        return stored.split(",").filter { it.isNotBlank() }.toSet()
    }

    private fun saveBlockedSubreddits(subreddits: Set<String>) {
        settings[KEY_BLOCKED_SUBREDDITS] = subreddits.joinToString(",")
    }

    companion object {
        private const val KEY_INLINE_IMAGES = "inline_images_enabled"
        private const val KEY_BLOCKED_SUBREDDITS = "blocked_subreddits"
    }
}
