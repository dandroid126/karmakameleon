package com.reader.shared.data.repository

import com.reader.shared.domain.model.NotificationInterval
import com.reader.shared.domain.model.NsfwHistoryMode
import com.reader.shared.domain.model.NsfwPreviewMode
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRepositoryTest {

    private fun createRepo(settings: MapSettings = MapSettings()) = SettingsRepository(settings)

    // ==================== Inline Images ====================

    @Test
    fun inlineImagesEnabled_defaultsToTrue() {
        val repo = createRepo()
        assertTrue(repo.inlineImagesEnabled.value)
    }

    @Test
    fun setInlineImagesEnabled_updatesState() {
        val repo = createRepo()
        repo.setInlineImagesEnabled(false)
        assertFalse(repo.inlineImagesEnabled.value)
    }

    @Test
    fun setInlineImagesEnabled_persistsAcrossInstances() {
        val settings = MapSettings()
        val repo1 = SettingsRepository(settings)
        repo1.setInlineImagesEnabled(false)

        val repo2 = SettingsRepository(settings)
        assertFalse(repo2.inlineImagesEnabled.value)
    }

    // ==================== Notification Interval ====================

    @Test
    fun notificationInterval_defaultsToOff() {
        val repo = createRepo()
        assertEquals(NotificationInterval.OFF, repo.notificationInterval.value)
    }

    @Test
    fun setNotificationInterval_updatesState() {
        val repo = createRepo()
        repo.setNotificationInterval(NotificationInterval.FIFTEEN_MINUTES)
        assertEquals(NotificationInterval.FIFTEEN_MINUTES, repo.notificationInterval.value)
    }

    @Test
    fun setNotificationInterval_persistsAcrossInstances() {
        val settings = MapSettings()
        val repo1 = SettingsRepository(settings)
        repo1.setNotificationInterval(NotificationInterval.ONE_HOUR)

        val repo2 = SettingsRepository(settings)
        assertEquals(NotificationInterval.ONE_HOUR, repo2.notificationInterval.value)
    }

    @Test
    fun setNotificationInterval_allValues() {
        val repo = createRepo()
        for (interval in NotificationInterval.entries) {
            repo.setNotificationInterval(interval)
            assertEquals(interval, repo.notificationInterval.value)
        }
    }

    // ==================== Blocked Subreddits ====================

    @Test
    fun blockedSubreddits_defaultsToEmpty() {
        val repo = createRepo()
        assertTrue(repo.blockedSubreddits.value.isEmpty())
    }

    @Test
    fun addBlockedSubreddit_addsToSet() {
        val repo = createRepo()
        repo.addBlockedSubreddit("TestSub")
        assertTrue(repo.blockedSubreddits.value.contains("testsub"))
    }

    @Test
    fun addBlockedSubreddit_lowercasesName() {
        val repo = createRepo()
        repo.addBlockedSubreddit("MixedCase")
        assertTrue(repo.blockedSubreddits.value.contains("mixedcase"))
    }

    @Test
    fun removeBlockedSubreddit_removesFromSet() {
        val repo = createRepo()
        repo.addBlockedSubreddit("testsub")
        repo.removeBlockedSubreddit("testsub")
        assertFalse(repo.blockedSubreddits.value.contains("testsub"))
    }

    @Test
    fun blockedSubreddits_persistsAcrossInstances() {
        val settings = MapSettings()
        val repo1 = SettingsRepository(settings)
        repo1.addBlockedSubreddit("sub1")
        repo1.addBlockedSubreddit("sub2")

        val repo2 = SettingsRepository(settings)
        assertTrue(repo2.blockedSubreddits.value.contains("sub1"))
        assertTrue(repo2.blockedSubreddits.value.contains("sub2"))
    }

    @Test
    fun addBlockedSubreddit_multipleSubreddits() {
        val repo = createRepo()
        repo.addBlockedSubreddit("sub1")
        repo.addBlockedSubreddit("sub2")
        repo.addBlockedSubreddit("sub3")
        assertEquals(3, repo.blockedSubreddits.value.size)
    }

    // ==================== Favorite Subreddits ====================

    @Test
    fun favoriteSubreddits_defaultsToEmpty() {
        val repo = createRepo()
        assertTrue(repo.favoriteSubreddits.value.isEmpty())
    }

    @Test
    fun toggleFavoriteSubreddit_addsWhenNotPresent() {
        val repo = createRepo()
        repo.toggleFavoriteSubreddit("TestSub")
        assertTrue(repo.favoriteSubreddits.value.contains("testsub"))
    }

    @Test
    fun toggleFavoriteSubreddit_removesWhenPresent() {
        val repo = createRepo()
        repo.toggleFavoriteSubreddit("testsub")
        repo.toggleFavoriteSubreddit("testsub")
        assertFalse(repo.favoriteSubreddits.value.contains("testsub"))
    }

    @Test
    fun isFavoriteSubreddit_returnsTrueWhenFavorited() {
        val repo = createRepo()
        repo.toggleFavoriteSubreddit("testsub")
        assertTrue(repo.isFavoriteSubreddit("testsub"))
    }

    @Test
    fun isFavoriteSubreddit_returnsFalseWhenNotFavorited() {
        val repo = createRepo()
        assertFalse(repo.isFavoriteSubreddit("testsub"))
    }

    @Test
    fun isFavoriteSubreddit_caseInsensitive() {
        val repo = createRepo()
        repo.toggleFavoriteSubreddit("TestSub")
        assertTrue(repo.isFavoriteSubreddit("TESTSUB"))
    }

    @Test
    fun favoriteSubreddits_persistsAcrossInstances() {
        val settings = MapSettings()
        val repo1 = SettingsRepository(settings)
        repo1.toggleFavoriteSubreddit("fav1")
        repo1.toggleFavoriteSubreddit("fav2")

        val repo2 = SettingsRepository(settings)
        assertTrue(repo2.isFavoriteSubreddit("fav1"))
        assertTrue(repo2.isFavoriteSubreddit("fav2"))
    }

    // ==================== NSFW Enabled ====================

    @Test
    fun nsfwEnabled_defaultsToFalse() {
        val repo = createRepo()
        assertFalse(repo.nsfwEnabled.value)
    }

    @Test
    fun setNsfwEnabled_updatesState() {
        val repo = createRepo()
        repo.setNsfwEnabled(true)
        assertTrue(repo.nsfwEnabled.value)
    }

    @Test
    fun setNsfwEnabled_persistsAcrossInstances() {
        val settings = MapSettings()
        val repo1 = SettingsRepository(settings)
        repo1.setNsfwEnabled(true)

        val repo2 = SettingsRepository(settings)
        assertTrue(repo2.nsfwEnabled.value)
    }

    // ==================== NSFW Cache Media ====================

    @Test
    fun nsfwCacheMedia_defaultsToFalse() {
        val repo = createRepo()
        assertFalse(repo.nsfwCacheMedia.value)
    }

    @Test
    fun setNsfwCacheMedia_updatesState() {
        val repo = createRepo()
        repo.setNsfwCacheMedia(true)
        assertTrue(repo.nsfwCacheMedia.value)
    }

    @Test
    fun setNsfwCacheMedia_persistsAcrossInstances() {
        val settings = MapSettings()
        val repo1 = SettingsRepository(settings)
        repo1.setNsfwCacheMedia(true)

        val repo2 = SettingsRepository(settings)
        assertTrue(repo2.nsfwCacheMedia.value)
    }

    // ==================== NSFW Preview Mode ====================

    @Test
    fun nsfwPreviewMode_defaultsToDoNotPrefetch() {
        val repo = createRepo()
        assertEquals(NsfwPreviewMode.DO_NOT_PREFETCH, repo.nsfwPreviewMode.value)
    }

    @Test
    fun setNsfwPreviewMode_updatesState() {
        val repo = createRepo()
        repo.setNsfwPreviewMode(NsfwPreviewMode.PREFETCH_AND_BLUR)
        assertEquals(NsfwPreviewMode.PREFETCH_AND_BLUR, repo.nsfwPreviewMode.value)
    }

    @Test
    fun setNsfwPreviewMode_persistsAcrossInstances() {
        val settings = MapSettings()
        val repo1 = SettingsRepository(settings)
        repo1.setNsfwPreviewMode(NsfwPreviewMode.SHOW_PREVIEWS)

        val repo2 = SettingsRepository(settings)
        assertEquals(NsfwPreviewMode.SHOW_PREVIEWS, repo2.nsfwPreviewMode.value)
    }

    @Test
    fun setNsfwPreviewMode_allValues() {
        val repo = createRepo()
        for (mode in NsfwPreviewMode.entries) {
            repo.setNsfwPreviewMode(mode)
            assertEquals(mode, repo.nsfwPreviewMode.value)
        }
    }

    // ==================== NSFW Search Enabled ====================

    @Test
    fun nsfwSearchEnabled_defaultsToFalse() {
        val repo = createRepo()
        assertFalse(repo.nsfwSearchEnabled.value)
    }

    @Test
    fun setNsfwSearchEnabled_updatesState() {
        val repo = createRepo()
        repo.setNsfwSearchEnabled(true)
        assertTrue(repo.nsfwSearchEnabled.value)
    }

    @Test
    fun setNsfwSearchEnabled_persistsAcrossInstances() {
        val settings = MapSettings()
        val repo1 = SettingsRepository(settings)
        repo1.setNsfwSearchEnabled(true)

        val repo2 = SettingsRepository(settings)
        assertTrue(repo2.nsfwSearchEnabled.value)
    }

    // ==================== NSFW History Mode ====================

    @Test
    fun nsfwHistoryMode_defaultsToDontSaveAnyNsfw() {
        val repo = createRepo()
        assertEquals(NsfwHistoryMode.DONT_SAVE_ANY_NSFW, repo.nsfwHistoryMode.value)
    }

    @Test
    fun setNsfwHistoryMode_updatesState() {
        val repo = createRepo()
        repo.setNsfwHistoryMode(NsfwHistoryMode.SAVE_ALL)
        assertEquals(NsfwHistoryMode.SAVE_ALL, repo.nsfwHistoryMode.value)
    }

    @Test
    fun setNsfwHistoryMode_persistsAcrossInstances() {
        val settings = MapSettings()
        val repo1 = SettingsRepository(settings)
        repo1.setNsfwHistoryMode(NsfwHistoryMode.DONT_SAVE_NSFW_SUBREDDITS)

        val repo2 = SettingsRepository(settings)
        assertEquals(NsfwHistoryMode.DONT_SAVE_NSFW_SUBREDDITS, repo2.nsfwHistoryMode.value)
    }

    @Test
    fun setNsfwHistoryMode_allValues() {
        val repo = createRepo()
        for (mode in NsfwHistoryMode.entries) {
            repo.setNsfwHistoryMode(mode)
            assertEquals(mode, repo.nsfwHistoryMode.value)
        }
    }

    // ==================== Subreddit NSFW Cache ====================

    @Test
    fun subredditNsfwCache_defaultsToEmpty() {
        val repo = createRepo()
        assertTrue(repo.subredditNsfwCache.value.isEmpty())
    }

    @Test
    fun setSubredditNsfw_addsToCache() {
        val repo = createRepo()
        repo.setSubredditNsfw("TestSub", true)
        assertTrue(repo.subredditNsfwCache.value.containsKey("testsub"))
        assertTrue(repo.subredditNsfwCache.value["testsub"] == true)
    }

    @Test
    fun setSubredditNsfw_lowercasesSubredditName() {
        val repo = createRepo()
        repo.setSubredditNsfw("MixedCaseSub", true)
        assertTrue(repo.subredditNsfwCache.value.containsKey("mixedcasesub"))
    }

    @Test
    fun setSubredditNsfw_marksAsNonNsfw() {
        val repo = createRepo()
        repo.setSubredditNsfw("testsub", false)
        assertTrue(repo.subredditNsfwCache.value.containsKey("testsub"))
        assertFalse(repo.subredditNsfwCache.value["testsub"] == true)
    }

    @Test
    fun setSubredditNsfw_updatesExistingEntry() {
        val repo = createRepo()
        repo.setSubredditNsfw("testsub", true)
        repo.setSubredditNsfw("testsub", false)
        assertFalse(repo.subredditNsfwCache.value["testsub"] == true)
    }

    @Test
    fun setSubredditNsfw_persistsAcrossInstances() {
        val settings = MapSettings()
        val repo1 = SettingsRepository(settings)
        repo1.setSubredditNsfw("nsfw_sub", true)
        repo1.setSubredditNsfw("safe_sub", false)

        val repo2 = SettingsRepository(settings)
        assertTrue(repo2.subredditNsfwCache.value["nsfw_sub"] == true)
        assertFalse(repo2.subredditNsfwCache.value["safe_sub"] == true)
    }

    @Test
    fun getSubredditNsfw_returnsTrue_whenMarkedNsfw() {
        val repo = createRepo()
        repo.setSubredditNsfw("testsub", true)
        assertTrue(repo.getSubredditNsfw("testsub"))
    }

    @Test
    fun getSubredditNsfw_returnsFalse_whenMarkedSafe() {
        val repo = createRepo()
        repo.setSubredditNsfw("testsub", false)
        assertFalse(repo.getSubredditNsfw("testsub"))
    }

    @Test
    fun getSubredditNsfw_returnsFalse_whenNotCached() {
        val repo = createRepo()
        assertFalse(repo.getSubredditNsfw("unknown_sub"))
    }

    @Test
    fun getSubredditNsfw_caseInsensitive() {
        val repo = createRepo()
        repo.setSubredditNsfw("TestSub", true)
        assertTrue(repo.getSubredditNsfw("TESTSUB"))
        assertTrue(repo.getSubredditNsfw("testsub"))
    }

    @Test
    fun isSubredditNsfwCached_returnsTrueWhenCached() {
        val repo = createRepo()
        repo.setSubredditNsfw("testsub", true)
        assertTrue(repo.isSubredditNsfwCached("testsub"))
    }

    @Test
    fun isSubredditNsfwCached_returnsFalseWhenNotCached() {
        val repo = createRepo()
        assertFalse(repo.isSubredditNsfwCached("unknown_sub"))
    }

    @Test
    fun isSubredditNsfwCached_caseInsensitive() {
        val repo = createRepo()
        repo.setSubredditNsfw("TestSub", true)
        assertTrue(repo.isSubredditNsfwCached("TESTSUB"))
        assertTrue(repo.isSubredditNsfwCached("testsub"))
    }

    @Test
    fun clearSubredditNsfwCache_removesAllEntries() {
        val repo = createRepo()
        repo.setSubredditNsfw("sub1", true)
        repo.setSubredditNsfw("sub2", false)
        repo.setSubredditNsfw("sub3", true)
        
        repo.clearSubredditNsfwCache()
        
        assertTrue(repo.subredditNsfwCache.value.isEmpty())
    }

    @Test
    fun clearSubredditNsfwCache_persistsAcrossInstances() {
        val settings = MapSettings()
        val repo1 = SettingsRepository(settings)
        repo1.setSubredditNsfw("sub1", true)
        repo1.setSubredditNsfw("sub2", false)
        repo1.clearSubredditNsfwCache()

        val repo2 = SettingsRepository(settings)
        assertTrue(repo2.subredditNsfwCache.value.isEmpty())
    }

    @Test
    fun subredditNsfwCache_multipleSubreddits() {
        val repo = createRepo()
        repo.setSubredditNsfw("sub1", true)
        repo.setSubredditNsfw("sub2", false)
        repo.setSubredditNsfw("sub3", true)
        repo.setSubredditNsfw("sub4", false)
        
        assertEquals(4, repo.subredditNsfwCache.value.size)
        assertTrue(repo.getSubredditNsfw("sub1"))
        assertFalse(repo.getSubredditNsfw("sub2"))
        assertTrue(repo.getSubredditNsfw("sub3"))
        assertFalse(repo.getSubredditNsfw("sub4"))
    }

    // ==================== NSFW Preview/History Mode Edge Cases ====================

    @Test
    fun loadNsfwPreviewMode_handlesInvalidStoredValue() {
        val settings = MapSettings()
        settings.putString("nsfw_preview_mode", "INVALID_MODE")
        val repo = SettingsRepository(settings)
        assertEquals(NsfwPreviewMode.DO_NOT_PREFETCH, repo.nsfwPreviewMode.value)
    }

    @Test
    fun loadNsfwHistoryMode_handlesInvalidStoredValue() {
        val settings = MapSettings()
        settings.putString("nsfw_history_mode", "INVALID_MODE")
        val repo = SettingsRepository(settings)
        assertEquals(NsfwHistoryMode.DONT_SAVE_ANY_NSFW, repo.nsfwHistoryMode.value)
    }

    // ==================== Spoiler Previews Enabled ====================

    @Test
    fun spoilerPreviewsEnabled_defaultsToFalse() {
        val repo = createRepo()
        assertFalse(repo.spoilerPreviewsEnabled.value)
    }

    @Test
    fun setSpoilerPreviewsEnabled_updatesState() {
        val repo = createRepo()
        repo.setSpoilerPreviewsEnabled(true)
        assertTrue(repo.spoilerPreviewsEnabled.value)
    }

    @Test
    fun setSpoilerPreviewsEnabled_toFalse_updatesState() {
        val repo = createRepo()
        repo.setSpoilerPreviewsEnabled(true)
        repo.setSpoilerPreviewsEnabled(false)
        assertFalse(repo.spoilerPreviewsEnabled.value)
    }

    @Test
    fun setSpoilerPreviewsEnabled_persistsAcrossInstances() {
        val settings = MapSettings()
        val repo1 = SettingsRepository(settings)
        repo1.setSpoilerPreviewsEnabled(true)

        val repo2 = SettingsRepository(settings)
        assertTrue(repo2.spoilerPreviewsEnabled.value)
    }
}
