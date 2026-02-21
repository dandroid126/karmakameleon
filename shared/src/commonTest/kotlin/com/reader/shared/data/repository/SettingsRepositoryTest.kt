package com.reader.shared.data.repository

import com.reader.shared.domain.model.NotificationInterval
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
}
