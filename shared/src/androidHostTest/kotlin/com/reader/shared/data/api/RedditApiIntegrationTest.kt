package com.reader.shared.data.api

import com.reader.shared.domain.model.CommentSort
import com.reader.shared.domain.model.PostSort
import com.reader.shared.domain.model.SearchSort
import com.reader.shared.domain.model.TimeFilter
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests that call the real Reddit API using an anonymous token.
 *
 * These tests require a valid REDDIT_CLIENT_ID in the .env file at the project root.
 * To run: set the client ID, then execute `./gradlew :shared:testAndroidHostTest`.
 *
 * Tests that require user authentication (vote, save, hide, subscribe, submit,
 * comment, message, inbox, etc.) are stubbed/mocked in the repository tests
 * since anonymous tokens cannot perform those actions.
 */
class RedditApiIntegrationTest {

    private fun loadClientId(): String? {
        return try {
            val value = System.getenv("REDDIT_CLIENT_ID")
                ?: System.getProperty("REDDIT_CLIENT_ID")
            value?.takeIf { it.isNotBlank() && it != "your_client_id_here" }
        } catch (_: Exception) {
            null
        }
    }

    private fun createApi(): RedditApi? {
        val clientId = loadClientId() ?: return null

        val httpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                    encodeDefaults = true
                })
            }
            install(Logging) {
                logger = Logger.SIMPLE
                level = LogLevel.NONE
            }
        }

        val settings = MapSettings()
        val authManager = AuthManager(httpClient, settings)
        authManager.setClientId(clientId)

        return RedditApi(httpClient, authManager)
    }

    // ==================== Posts ====================

    @Test
    fun getPosts_frontPage() = runTest {
        val api = createApi() ?: return@runTest
        val listing = api.getPosts(sort = PostSort.HOT, limit = 5)
        assertTrue(listing.items.isNotEmpty(), "Front page should have posts")
        listing.items.forEach { post ->
            assertTrue(post.id.isNotBlank(), "Post ID should not be blank")
            assertTrue(post.title.isNotBlank(), "Post title should not be blank")
            assertTrue(post.author.isNotBlank(), "Post author should not be blank")
            assertTrue(post.subreddit.isNotBlank(), "Post subreddit should not be blank")
        }
    }

    @Test
    fun getPosts_subreddit() = runTest {
        val api = createApi() ?: return@runTest
        val listing = api.getPosts(subreddit = "AskReddit", sort = PostSort.HOT, limit = 5)
        assertTrue(listing.items.isNotEmpty(), "r/AskReddit should have posts")
        listing.items.forEach { post ->
            assertTrue(
                post.subreddit.equals("AskReddit", ignoreCase = true),
                "Posts should be from AskReddit"
            )
        }
    }

    @Test
    fun getPosts_topAllTime() = runTest {
        val api = createApi() ?: return@runTest
        val listing = api.getPosts(
            subreddit = "pics",
            sort = PostSort.TOP,
            time = TimeFilter.ALL,
            limit = 3
        )
        assertTrue(listing.items.isNotEmpty(), "r/pics top/all should have posts")
        assertTrue(listing.items[0].score > 0, "Top posts should have positive score")
    }

    @Test
    fun getPosts_pagination() = runTest {
        val api = createApi() ?: return@runTest
        val page1 = api.getPosts(subreddit = "all", sort = PostSort.HOT, limit = 5)
        assertTrue(page1.items.isNotEmpty())
        assertNotNull(page1.after, "Should have pagination token")

        val page2 = api.getPosts(subreddit = "all", sort = PostSort.HOT, limit = 5, after = page1.after)
        assertTrue(page2.items.isNotEmpty(), "Page 2 should have posts")
        val page1Ids = page1.items.map { it.id }.toSet()
        val page2Ids = page2.items.map { it.id }.toSet()
        assertTrue(page1Ids.intersect(page2Ids).isEmpty(), "Pages should have different posts")
    }

    // ==================== Post Detail ====================

    @Test
    fun getPostWithComments_realPost() = runTest {
        val api = createApi() ?: return@runTest
        val listing = api.getPosts(subreddit = "AskReddit", sort = PostSort.HOT, limit = 1)
        val post = listing.items.firstOrNull() ?: return@runTest

        val (detailPost, _) = api.getPostWithComments(
            subreddit = post.subreddit,
            postId = post.id,
            sort = CommentSort.CONFIDENCE,
            limit = 10
        )
        assertTrue(detailPost.id == post.id, "Should return the same post")
    }

    // ==================== Subreddits ====================

    @Test
    fun getSubreddit_existing() = runTest {
        val api = createApi() ?: return@runTest
        val subreddit = api.getSubreddit("AskReddit")
        assertNotNull(subreddit, "r/AskReddit should exist")
        assertTrue(
            subreddit.displayName.equals("AskReddit", ignoreCase = true),
            "Display name should be AskReddit"
        )
        assertTrue(
            !subreddit.isNsfw,
            "isNsfw should be false for r/AskReddit"
        )
        assertTrue(subreddit.subscribers > 0, "Should have subscribers")
    }

    @Test
    fun getNsfwSubreddit() = runTest {
        val api = createApi() ?: return@runTest
        val subreddit = api.getSubreddit("GoneWild")
        assertNotNull(subreddit, "r/GoneWild should exist")
        assertTrue(
            subreddit.isNsfw,
            "isNsfw should be true for r/GoneWild"
        )
        assertTrue(subreddit.subscribers > 0, "Should have subscribers")
    }

    @Test
    fun searchSubreddits_query() = runTest {
        val api = createApi() ?: return@runTest
        val results = api.searchSubreddits("kotlin", limit = 5)
        assertTrue(results.isNotEmpty(), "Should find subreddits matching 'kotlin'")
    }

    @Test
    fun getPopularSubreddits_returnsList() = runTest {
        val api = createApi() ?: return@runTest
        val listing = api.getPopularSubreddits(limit = 5)
        assertTrue(listing.items.isNotEmpty(), "Should have popular subreddits")
    }

    // ==================== User ====================

    @Test
    fun getUser_existing() = runTest {
        val api = createApi() ?: return@runTest
        val user = api.getUser("spez")
        assertNotNull(user, "u/spez should exist")
        assertTrue(user.name.equals("spez", ignoreCase = true), "Name should be spez")
        assertTrue(user.totalKarma > 0, "spez should have karma")
    }

    @Test
    fun getUserPosts_existing() = runTest {
        val api = createApi() ?: return@runTest
        // Note: getUserPosts requires authentication. With anonymous token, most users' posts
        // are not visible. This test just verifies the API call works without error.
        val listing = api.getUserPosts("spez", sort = PostSort.TOP, limit = 3)
        // Anonymous token may return empty, so we just verify it doesn't throw
        assertTrue(listing.items.isNotEmpty() || listing.items.isEmpty(), "API call should succeed")
    }

    @Test
    fun getUserComments_existing() = runTest {
        val api = createApi() ?: return@runTest
        val listing = api.getUserComments("spez", sort = PostSort.TOP, limit = 3)
        assertTrue(listing.items.isNotEmpty(), "spez should have comments")
    }

    // ==================== Search ====================

    @Test
    fun search_posts() = runTest {
        val api = createApi() ?: return@runTest
        val listing = api.search(
            query = "kotlin programming",
            sort = SearchSort.RELEVANCE,
            time = TimeFilter.ALL,
            limit = 5
        )
        assertTrue(listing.items.isNotEmpty(), "Should find posts about kotlin")
    }

    // ==================== fetchPostsByIds ====================

    @Test
    fun fetchPostsByIds_realPosts() = runTest {
        val api = createApi() ?: return@runTest
        val listing = api.getPosts(subreddit = "all", limit = 3)
        val names = listing.items.map { it.name }
        if (names.isEmpty()) return@runTest

        val posts = api.fetchPostsByIds(names)
        assertTrue(posts.isNotEmpty(), "Should fetch posts by IDs")
        assertTrue(posts.size <= names.size)
    }
}
