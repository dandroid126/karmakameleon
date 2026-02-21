package com.reader.shared.data.repository

import com.reader.shared.FakeRedditApi
import com.reader.shared.createTestListing
import com.reader.shared.createTestSubreddit
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubredditRepositoryTest {

    private fun createRepo(
        api: FakeRedditApi = FakeRedditApi()
    ): Pair<SubredditRepository, FakeRedditApi> {
        val settingsRepo = SettingsRepository(MapSettings())
        return SubredditRepository(api, settingsRepo) to api
    }

    // ==================== getSubreddit ====================

    @Test
    fun getSubreddit_returnsSuccess() = runTest {
        val (repo, api) = createRepo()
        val sub = createTestSubreddit(displayName = "kotlin")
        api.subredditResult = sub

        val result = repo.getSubreddit("kotlin")
        assertTrue(result.isSuccess)
        assertEquals("kotlin", result.getOrThrow().displayName)
    }

    @Test
    fun getSubreddit_cachesResult() = runTest {
        val (repo, api) = createRepo()
        val sub = createTestSubreddit(displayName = "kotlin")
        api.subredditResult = sub

        repo.getSubreddit("kotlin")
        // Second call should use cache
        api.subredditResult = null
        val result = repo.getSubreddit("kotlin")
        assertTrue(result.isSuccess)
        assertEquals("kotlin", result.getOrThrow().displayName)
    }

    @Test
    fun getSubreddit_cacheIsCaseInsensitive() = runTest {
        val (repo, api) = createRepo()
        val sub = createTestSubreddit(displayName = "Kotlin")
        api.subredditResult = sub

        repo.getSubreddit("Kotlin")
        api.subredditResult = null
        val result = repo.getSubreddit("kotlin")
        assertTrue(result.isSuccess)
    }

    @Test
    fun getSubreddit_returnsFailureWhenNotFound() = runTest {
        val (repo, api) = createRepo()
        api.subredditResult = null

        val result = repo.getSubreddit("nonexistent")
        assertTrue(result.isFailure)
    }

    @Test
    fun getSubreddit_returnsFailureOnException() = runTest {
        val (repo, api) = createRepo()
        api.shouldThrow = Exception("Network error")

        val result = repo.getSubreddit("kotlin")
        assertTrue(result.isFailure)
    }

    // ==================== loadSubscribedSubreddits ====================

    @Test
    fun loadSubscribedSubreddits_returnsSuccess() = runTest {
        val (repo, api) = createRepo()
        val subs = listOf(
            createTestSubreddit(displayName = "beta"),
            createTestSubreddit(displayName = "alpha")
        )
        api.subscribedSubredditsResult = createTestListing(items = subs)

        val result = repo.loadSubscribedSubreddits()
        assertTrue(result.isSuccess)
        // Should be sorted alphabetically
        assertEquals("alpha", result.getOrThrow()[0].displayName)
        assertEquals("beta", result.getOrThrow()[1].displayName)
    }

    @Test
    fun loadSubscribedSubreddits_updatesStateFlow() = runTest {
        val (repo, api) = createRepo()
        val subs = listOf(createTestSubreddit(displayName = "testsub"))
        api.subscribedSubredditsResult = createTestListing(items = subs)

        repo.loadSubscribedSubreddits()
        assertEquals(1, repo.subscribedSubreddits.value.size)
    }

    @Test
    fun loadSubscribedSubreddits_returnsFailureOnError() = runTest {
        val (repo, api) = createRepo()
        api.shouldThrow = Exception("Network error")

        val result = repo.loadSubscribedSubreddits()
        assertTrue(result.isFailure)
    }

    // ==================== subscribe ====================

    @Test
    fun subscribe_subscribesToUnsubscribed() = runTest {
        val (repo, api) = createRepo()
        val sub = createTestSubreddit(displayName = "kotlin", isSubscribed = false)
        api.subscribeResult = true

        val result = repo.subscribe(sub)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isSubscribed)
    }

    @Test
    fun subscribe_unsubscribesFromSubscribed() = runTest {
        val (repo, api) = createRepo()
        val sub = createTestSubreddit(displayName = "kotlin", isSubscribed = true)
        api.unsubscribeResult = true

        val result = repo.subscribe(sub)
        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().isSubscribed)
    }

    @Test
    fun subscribe_failureReturnsError() = runTest {
        val (repo, api) = createRepo()
        val sub = createTestSubreddit(displayName = "kotlin", isSubscribed = false)
        api.subscribeResult = false

        val result = repo.subscribe(sub)
        assertTrue(result.isFailure)
    }

    @Test
    fun subscribe_addsToSubscribedList() = runTest {
        val (repo, api) = createRepo()
        val sub = createTestSubreddit(id = "s1", displayName = "kotlin", isSubscribed = false)
        api.subscribeResult = true

        repo.subscribe(sub)
        assertTrue(repo.subscribedSubreddits.value.any { it.displayName == "kotlin" })
    }

    @Test
    fun subscribe_removesFromSubscribedList() = runTest {
        val (repo, api) = createRepo()
        // First load subscribed
        val sub = createTestSubreddit(id = "s1", displayName = "kotlin", isSubscribed = true)
        api.subscribedSubredditsResult = createTestListing(items = listOf(sub))
        repo.loadSubscribedSubreddits()

        api.unsubscribeResult = true
        repo.subscribe(sub)
        assertFalse(repo.subscribedSubreddits.value.any { it.id == "s1" })
    }

    // ==================== searchSubreddits ====================

    @Test
    fun searchSubreddits_returnsResults() = runTest {
        val (repo, api) = createRepo()
        api.searchSubredditsResult = listOf(
            createTestSubreddit(displayName = "kotlin"),
            createTestSubreddit(displayName = "kotlindev")
        )

        val result = repo.searchSubreddits("kotlin")
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }

    @Test
    fun searchSubreddits_returnsFailureOnError() = runTest {
        val (repo, api) = createRepo()
        api.shouldThrow = Exception("Search failed")

        val result = repo.searchSubreddits("kotlin")
        assertTrue(result.isFailure)
    }

    // ==================== getPopularSubreddits ====================

    @Test
    fun getPopularSubreddits_returnsResults() = runTest {
        val (repo, api) = createRepo()
        api.popularSubredditsResult = createTestListing(
            items = listOf(createTestSubreddit(displayName = "popular1"))
        )

        val result = repo.getPopularSubreddits()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().items.size)
    }

    // ==================== getCachedSubreddit ====================

    @Test
    fun getCachedSubreddit_returnsNullWhenNotCached() {
        val (repo, _) = createRepo()
        assertNull(repo.getCachedSubreddit("nonexistent"))
    }

    @Test
    fun getCachedSubreddit_returnsCachedValue() = runTest {
        val (repo, api) = createRepo()
        val sub = createTestSubreddit(displayName = "kotlin")
        api.subredditResult = sub
        repo.getSubreddit("kotlin")

        val cached = repo.getCachedSubreddit("kotlin")
        assertEquals("kotlin", cached?.displayName)
    }
}
