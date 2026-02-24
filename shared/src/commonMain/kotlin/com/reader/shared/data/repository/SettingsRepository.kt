package com.reader.shared.data.repository

import com.reader.shared.domain.model.NotificationInterval
import com.reader.shared.domain.model.NsfwHistoryMode
import com.reader.shared.domain.model.NsfwPreviewMode
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

    private val _nsfwEnabled = MutableStateFlow(settings.getBoolean(KEY_NSFW_ENABLED, false))
    val nsfwEnabled: StateFlow<Boolean> = _nsfwEnabled.asStateFlow()

    private val _nsfwCacheMedia = MutableStateFlow(settings.getBoolean(KEY_NSFW_CACHE_MEDIA, false))
    val nsfwCacheMedia: StateFlow<Boolean> = _nsfwCacheMedia.asStateFlow()

    private val _nsfwPreviewMode = MutableStateFlow(loadNsfwPreviewMode())
    val nsfwPreviewMode: StateFlow<NsfwPreviewMode> = _nsfwPreviewMode.asStateFlow()

    private val _nsfwSearchEnabled = MutableStateFlow(settings.getBoolean(KEY_NSFW_SEARCH_ENABLED, false))
    val nsfwSearchEnabled: StateFlow<Boolean> = _nsfwSearchEnabled.asStateFlow()

    private val _nsfwHistoryMode = MutableStateFlow(loadNsfwHistoryMode())
    val nsfwHistoryMode: StateFlow<NsfwHistoryMode> = _nsfwHistoryMode.asStateFlow()

    private val _spoilerPreviewsEnabled = MutableStateFlow(settings.getBoolean(KEY_SPOILER_PREVIEWS_ENABLED, false))
    val spoilerPreviewsEnabled: StateFlow<Boolean> = _spoilerPreviewsEnabled.asStateFlow()

    private val _subredditNsfwCache = MutableStateFlow(loadSubredditNsfwCache())
    val subredditNsfwCache: StateFlow<Map<String, Boolean>> = _subredditNsfwCache.asStateFlow()

    fun setSubredditNsfw(subredditName: String, isNsfw: Boolean) {
        val updated = _subredditNsfwCache.value.toMutableMap()
        updated[subredditName.lowercase()] = isNsfw
        _subredditNsfwCache.value = updated
        saveSubredditNsfwCache(updated)
    }

    fun getSubredditNsfw(subredditName: String): Boolean {
        return _subredditNsfwCache.value[subredditName.lowercase()] ?: false
    }

    fun isSubredditNsfwCached(subredditName: String): Boolean {
        return _subredditNsfwCache.value.containsKey(subredditName.lowercase())
    }

    fun clearSubredditNsfwCache() {
        _subredditNsfwCache.value = emptyMap()
        settings.remove(KEY_SUBREDDIT_NSFW_CACHE)
    }

    private fun loadSubredditNsfwCache(): Map<String, Boolean> {
        val stored = settings.getStringOrNull(KEY_SUBREDDIT_NSFW_CACHE) ?: return emptyMap()
        return stored.split(",").filter { it.isNotBlank() }.mapNotNull { raw ->
            val parts = raw.split("|")
            if (parts.size == 2) parts[0].lowercase() to (parts[1] == "1") else null
        }.toMap()
    }

    private fun saveSubredditNsfwCache(cache: Map<String, Boolean>) {
        settings[KEY_SUBREDDIT_NSFW_CACHE] = cache.entries.joinToString(",") { "${it.key}|${if (it.value) "1" else "0"}" }
    }

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

    fun setNsfwEnabled(enabled: Boolean) {
        _nsfwEnabled.value = enabled
        settings[KEY_NSFW_ENABLED] = enabled
    }

    fun setNsfwCacheMedia(enabled: Boolean) {
        _nsfwCacheMedia.value = enabled
        settings[KEY_NSFW_CACHE_MEDIA] = enabled
    }

    fun setNsfwPreviewMode(mode: NsfwPreviewMode) {
        _nsfwPreviewMode.value = mode
        settings[KEY_NSFW_PREVIEW_MODE] = mode.name
    }

    private fun loadNsfwPreviewMode(): NsfwPreviewMode {
        val stored = settings.getStringOrNull(KEY_NSFW_PREVIEW_MODE) ?: return NsfwPreviewMode.DO_NOT_PREFETCH
        return NsfwPreviewMode.entries.firstOrNull { it.name == stored } ?: NsfwPreviewMode.DO_NOT_PREFETCH
    }

    fun setNsfwSearchEnabled(enabled: Boolean) {
        _nsfwSearchEnabled.value = enabled
        settings[KEY_NSFW_SEARCH_ENABLED] = enabled
    }

    fun setNsfwHistoryMode(mode: NsfwHistoryMode) {
        _nsfwHistoryMode.value = mode
        settings[KEY_NSFW_HISTORY_MODE] = mode.name
    }

    fun setSpoilerPreviewsEnabled(enabled: Boolean) {
        _spoilerPreviewsEnabled.value = enabled
        settings[KEY_SPOILER_PREVIEWS_ENABLED] = enabled
    }

    private fun loadNsfwHistoryMode(): NsfwHistoryMode {
        val stored = settings.getStringOrNull(KEY_NSFW_HISTORY_MODE) ?: return NsfwHistoryMode.DONT_SAVE_ANY_NSFW
        return NsfwHistoryMode.entries.firstOrNull { it.name == stored } ?: NsfwHistoryMode.DONT_SAVE_ANY_NSFW
    }

    companion object {
        private const val KEY_INLINE_IMAGES = "inline_images_enabled"
        private const val KEY_BLOCKED_SUBREDDITS = "blocked_subreddits"
        private const val KEY_FAVORITE_SUBREDDITS = "favorite_subreddits"
        private const val KEY_NOTIFICATION_INTERVAL = "notification_interval"
        private const val KEY_NSFW_ENABLED = "nsfw_enabled"
        private const val KEY_NSFW_CACHE_MEDIA = "nsfw_cache_media"
        private const val KEY_NSFW_PREVIEW_MODE = "nsfw_preview_mode"
        private const val KEY_NSFW_SEARCH_ENABLED = "nsfw_search_enabled"
        private const val KEY_NSFW_HISTORY_MODE = "nsfw_history_mode"
        private const val KEY_SUBREDDIT_NSFW_CACHE = "subreddit_nsfw_cache"
        private const val KEY_SPOILER_PREVIEWS_ENABLED = "spoiler_previews_enabled"
    }
}
