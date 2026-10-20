package com.karmakameleon.shared.data.api

import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.readFixture
import com.russhwolf.settings.MapSettings
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Checks how [RedditApi] looks up, in the background, whether the subreddits in a page of posts are NSFW. */
@OptIn(ExperimentalCoroutinesApi::class)
class RedditApiNsfwLookupTest {
    private val reddit = MockReddit().apply { respond("/hot", readFixture("post_listing.json")) }
    private val settings = SettingsRepository(MapSettings())

    private fun TestScope.api(): RedditApi = reddit.api(settings, StandardTestDispatcher(testScheduler))

    @Test
    fun looksUpTheSubredditsInOneRequest() =
        runTest {
            reddit.respond("/api/info.json", listingOf(subredditThing("pics", nsfw = false), subredditThing("aww", nsfw = true)))

            api().getPosts()
            advanceUntilIdle()

            val lookup = reddit.requests.single { it.url.encodedPath == "/api/info.json" }
            assertEquals(mapOf("sr_name" to "pics,aww"), lookup.query())
            assertTrue(settings.isSubredditNsfwCached("pics"))
            assertFalse(settings.getSubredditNsfw("pics"))
            assertTrue(settings.getSubredditNsfw("aww"))
        }

    @Test
    fun onlyLooksUpSubredditsItDoesNotKnow() =
        runTest {
            settings.setSubredditNsfw("pics", isNsfw = false)
            reddit.respond("/api/info.json", listingOf(subredditThing("aww", nsfw = false)))

            api().getPosts()
            advanceUntilIdle()

            assertEquals(
                "aww",
                reddit.requests
                    .single { it.url.encodedPath == "/api/info.json" }
                    .url.parameters["sr_name"],
            )
        }

    @Test
    fun skipsTheLookupWhenEverySubredditIsKnown() =
        runTest {
            settings.setSubredditNsfw("pics", isNsfw = false)
            settings.setSubredditNsfw("aww", isNsfw = false)

            api().getPosts()
            advanceUntilIdle()

            assertEquals(listOf("/hot"), reddit.requests.map { it.url.encodedPath })
        }

    @Test
    fun whenTheLookupFails_looksUpEachSubredditOnItsOwn() =
        runTest {
            reddit.respond("/api/info.json", "<html>Service unavailable</html>", HttpStatusCode.ServiceUnavailable)
            reddit.respond("/r/pics/about", subredditThing("pics", nsfw = true).toString())
            reddit.respond("/r/aww/about", "<html>Not found</html>", HttpStatusCode.NotFound)

            api().getPosts()
            advanceUntilIdle()

            assertEquals(
                listOf("/hot", "/api/info.json", "/r/pics/about", "/r/aww/about"),
                reddit.requests.map { it.url.encodedPath },
            )
            assertTrue(settings.getSubredditNsfw("pics"))
            // A subreddit that can't be looked up is taken as not NSFW, so it isn't looked up again.
            assertTrue(settings.isSubredditNsfwCached("aww"))
            assertFalse(settings.getSubredditNsfw("aww"))
        }
}
