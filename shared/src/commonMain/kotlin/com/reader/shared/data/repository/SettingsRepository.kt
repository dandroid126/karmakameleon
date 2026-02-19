package com.reader.shared.data.repository

import com.reader.shared.domain.model.NotificationInterval
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(private val settings: Settings) {

    private val _inlineImagesEnabled = MutableStateFlow(settings.getBoolean(KEY_INLINE_IMAGES, true))
    val inlineImagesEnabled: StateFlow<Boolean> = _inlineImagesEnabled.asStateFlow()

    private val _notificationInterval = MutableStateFlow(loadNotificationInterval())
    val notificationInterval: StateFlow<NotificationInterval> = _notificationInterval.asStateFlow()

    private val _blockedSubreddits = MutableStateFlow(loadBlockedSubreddits())
    val blockedSubreddits: StateFlow<Set<String>> = _blockedSubreddits.asStateFlow()

    private val _favoriteSubreddits = MutableStateFlow(loadFavoriteSubreddits())
    val favoriteSubreddits: StateFlow<Set<String>> = _favoriteSubreddits.asStateFlow()

    fun setInlineImagesEnabled(enabled: Boolean) {
        _inlineImagesEnabled.value = enabled
        settings[KEY_INLINE_IMAGES] = enabled
    }

    fun setNotificationInterval(interval: NotificationInterval) {
        _notificationInterval.value = interval
        settings[KEY_NOTIFICATION_INTERVAL] = interval.name
    }

    private fun loadNotificationInterval(): NotificationInterval {
        val stored = settings.getStringOrNull(KEY_NOTIFICATION_INTERVAL) ?: return NotificationInterval.OFF
        return NotificationInterval.entries.firstOrNull { it.name == stored } ?: NotificationInterval.OFF
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

    fun toggleFavoriteSubreddit(subreddit: String) {
        val name = subreddit.lowercase()
        val updated = if (name in _favoriteSubreddits.value) {
            _favoriteSubreddits.value - name
        } else {
            _favoriteSubreddits.value + name
        }
        _favoriteSubreddits.value = updated
        saveFavoriteSubreddits(updated)
    }

    fun isFavoriteSubreddit(subreddit: String): Boolean {
        return subreddit.lowercase() in _favoriteSubreddits.value
    }

    private fun loadBlockedSubreddits(): Set<String> {
        val stored = settings.getStringOrNull(KEY_BLOCKED_SUBREDDITS) ?: return emptySet()
        return stored.split(",").filter { it.isNotBlank() }.toSet()
    }

    private fun saveBlockedSubreddits(subreddits: Set<String>) {
        settings[KEY_BLOCKED_SUBREDDITS] = subreddits.joinToString(",")
    }

    private fun loadFavoriteSubreddits(): Set<String> {
        val stored = settings.getStringOrNull(KEY_FAVORITE_SUBREDDITS) ?: return emptySet()
        return stored.split(",").filter { it.isNotBlank() }.toSet()
    }

    private fun saveFavoriteSubreddits(subreddits: Set<String>) {
        settings[KEY_FAVORITE_SUBREDDITS] = subreddits.joinToString(",")
    }

    companion object {
        private const val KEY_INLINE_IMAGES = "inline_images_enabled"
        private const val KEY_BLOCKED_SUBREDDITS = "blocked_subreddits"
        private const val KEY_FAVORITE_SUBREDDITS = "favorite_subreddits"
        private const val KEY_NOTIFICATION_INTERVAL = "notification_interval"
    }
}
