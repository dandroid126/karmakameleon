package com.karmakameleon.shared.data.api

import com.karmakameleon.shared.domain.model.CommentSort
import com.karmakameleon.shared.domain.model.InboxFilter
import com.karmakameleon.shared.domain.model.PostSort
import com.karmakameleon.shared.domain.model.SearchSort
import com.karmakameleon.shared.domain.model.TimeFilter
import com.karmakameleon.shared.readFixture
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Checks the requests [RedditApi] makes to read from Reddit, and what it makes of the answers. */
class RedditApiReadTest {
    private val reddit = MockReddit()

    // Background work (looking up whether subreddits are NSFW) waits until the test advances the scheduler.
    private fun TestScope.api(): RedditApi = reddit.api(ioDispatcher = StandardTestDispatcher(testScheduler))

    private fun assertGet(
        path: String,
        query: Map<String, String> = emptyMap(),
    ) {
        val request = reddit.request
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("https://oauth.reddit.com$path", request.url.toString().substringBefore("?"))
        assertEquals(query, request.query())
        assertEquals("1", request.url.parameters["raw_json"])
        assertEquals("Bearer fake_token", request.headers[HttpHeaders.Authorization])
        assertEquals("KK/1.0.0 (Android)", request.headers[HttpHeaders.UserAgent])
    }

    // ==================== Requests ====================

    @Test
    fun withoutAToken_requestsFail() =
        runTest {
            reddit.authManager.fakeAccessToken = null

            val error = assertFailsWith<IllegalStateException> { api().getPosts() }

            assertEquals("No Reddit Client ID configured. Please enter your Client ID in the Profile tab.", error.message)
            assertTrue(reddit.requests.isEmpty())
        }

    // ==================== Posts ====================

    @Test
    fun getPosts_frontPage() =
        runTest {
            reddit.respond("/hot", readFixture("post_listing.json"))

            val posts = api().getPosts()

            assertGet("/hot", mapOf("limit" to "25"))
            assertEquals(4, posts.items.size)
        }

    @Test
    fun getPosts_subredditWithSortTimeAndPage() =
        runTest {
            api().getPosts(subreddit = "Kotlin", sort = PostSort.TOP, time = TimeFilter.WEEK, after = "t3_abc", limit = 10)

            assertGet("/r/Kotlin/top", mapOf("limit" to "10", "after" to "t3_abc", "t" to "week"))
        }

    @Test
    fun fetchPostsByIds() =
        runTest {
            reddit.respond("/api/info", readFixture("post_listing.json"))

            val posts = api().fetchPostsByIds(listOf("t3_1x65iks", "t3_1xavut5"))

            assertGet("/api/info", mapOf("id" to "t3_1x65iks,t3_1xavut5"))
            assertEquals(4, posts.size)
        }

    @Test
    fun fetchPostsByIds_emptyWhenTheRequestFails() =
        runTest {
            reddit.respond("/api/info", "Service unavailable", HttpStatusCode.ServiceUnavailable)

            assertTrue(api().fetchPostsByIds(listOf("t3_1x65iks")).isEmpty())
        }

    @Test
    fun getPost() =
        runTest {
            reddit.respond("/r/nba/comments/1xawzaz", readFixture("post_with_comments.json"))

            val post = api().getPost("nba", "1xawzaz")

            assertGet("/r/nba/comments/1xawzaz", mapOf("limit" to "1"))
            assertEquals("1xawzaz", post?.id)
        }

    @Test
    fun getPost_nullWhenRedditReturnsNoPost() =
        runTest {
            reddit.respond("/r/nba/comments/gone", "[${MockReddit.EMPTY_LISTING}]")

            assertNull(api().getPost("nba", "gone"))
        }

    @Test
    fun getPostWithComments() =
        runTest {
            reddit.respond("/r/nba/comments/1xawzaz", readFixture("post_with_comments.json"))

            val (post, comments) = api().getPostWithComments("nba", "1xawzaz")

            assertGet("/r/nba/comments/1xawzaz", mapOf("sort" to "confidence", "limit" to "200"))
            assertEquals("1xawzaz", post.id)
            assertEquals(4, comments.size)
        }

    @Test
    fun getPostWithComments_threadOfOneComment() =
        runTest {
            reddit.respond("/r/nba/comments/1xawzaz/comment/phx1a8k", readFixture("post_with_comments.json"))

            api().getPostWithComments("nba", "1xawzaz", sort = null, limit = 50, commentId = "phx1a8k", context = 3)

            assertGet("/r/nba/comments/1xawzaz/comment/phx1a8k", mapOf("limit" to "50", "context" to "3"))
        }

    @Test
    fun getPostWithComments_threadWithoutContext() =
        runTest {
            reddit.respond("/r/nba/comments/1xawzaz/comment/phx1a8k", readFixture("post_with_comments.json"))

            api().getPostWithComments("nba", "1xawzaz", commentId = "phx1a8k")

            assertEquals("0", reddit.request.url.parameters["context"])
        }

    @Test
    fun getPostWithComments_postWithoutComments() =
        runTest {
            val postOnly = readFixture("post_with_comments.json").let { RedditParser.json.parseToJsonElement(it) }
            reddit.respond("/r/nba/comments/1xawzaz", "[${(postOnly as JsonArray)[0]}]")

            val (post, comments) = api().getPostWithComments("nba", "1xawzaz")

            assertEquals("1xawzaz", post.id)
            assertTrue(comments.isEmpty())
        }

    @Test
    fun getMoreComments() =
        runTest {
            reddit.respond("/api/morechildren", readFixture("more_children.json"))

            val comments = api().getMoreComments("t3_1xa1fgb", listOf("phq1mw9", "phqv313"), CommentSort.TOP)

            assertGet(
                "/api/morechildren",
                mapOf("api_type" to "json", "link_id" to "t3_1xa1fgb", "children" to "phq1mw9,phqv313", "sort" to "top"),
            )
            assertEquals(3, comments.size)
        }

    @Test
    fun getMoreComments_emptyWithoutData() =
        runTest {
            reddit.respond("/api/morechildren", """{"json": {"errors": []}}""")

            assertTrue(api().getMoreComments("t3_1xa1fgb", listOf("phq1mw9")).isEmpty())
        }

    // ==================== Subreddits ====================

    @Test
    fun getSubreddit() =
        runTest {
            reddit.respond("/r/Kotlin/about", readFixture("subreddit_about.json"))

            val subreddit = api().getSubreddit("Kotlin")

            assertGet("/r/Kotlin/about")
            assertEquals("t5_2so2r", subreddit?.name)
        }

    @Test
    fun getSubscribedSubreddits() =
        runTest {
            reddit.respond("/subreddits/mine/subscriber", readFixture("subreddit_listing.json"))

            val subreddits = api().getSubscribedSubreddits(after = "t5_x")

            assertGet("/subreddits/mine/subscriber", mapOf("limit" to "100", "after" to "t5_x"))
            assertEquals(listOf("Home"), subreddits.items.map { it.displayName })
        }

    @Test
    fun getPopularSubreddits() =
        runTest {
            reddit.respond("/subreddits/popular", readFixture("subreddit_listing.json"))

            val subreddits = api().getPopularSubreddits()

            assertGet("/subreddits/popular", mapOf("limit" to "25"))
            assertEquals(1, subreddits.items.size)
        }

    @Test
    fun searchSubreddits() =
        runTest {
            reddit.respond(
                "/subreddits/search",
                listingOf(subredditThing("Kotlin", nsfw = false), subredditThing("KotlinNsfw", nsfw = true)),
            )

            val subreddits = api().searchSubreddits("kotlin")

            assertGet("/subreddits/search", mapOf("q" to "kotlin", "limit" to "10", "include_over_18" to "true"))
            assertEquals(listOf("Kotlin", "KotlinNsfw"), subreddits.map { it.displayName })
        }

    @Test
    fun searchSubreddits_withoutNsfw() =
        runTest {
            reddit.respond(
                "/subreddits/search",
                listingOf(subredditThing("Kotlin", nsfw = false), subredditThing("KotlinNsfw", nsfw = true)),
            )

            val subreddits = api().searchSubreddits("kotlin", limit = 5, includeOver18 = false)

            assertGet("/subreddits/search", mapOf("q" to "kotlin", "limit" to "5", "include_over_18" to "false"))
            assertEquals(listOf("Kotlin"), subreddits.map { it.displayName })
        }

    // ==================== Users ====================

    @Test
    fun getMe() =
        runTest {
            // /api/v1/me returns the user itself, not wrapped in a thing.
            val user = RedditParser.json.parseToJsonElement(readFixture("user_about.json")).jsonObject["data"]
            reddit.respond("/api/v1/me", user.toString())

            assertEquals("AutoModerator", api().getMe()?.name)
            assertGet("/api/v1/me")
        }

    @Test
    fun getMe_nullWhenNotLoggedIn() =
        runTest {
            reddit.respond("/api/v1/me", """{"message": "Forbidden", "error": 403}""", HttpStatusCode.Forbidden)

            assertNull(api().getMe())
        }

    @Test
    fun getUser() =
        runTest {
            reddit.respond("/user/AutoModerator/about", readFixture("user_about.json"))

            assertEquals("6l4z3", api().getUser("AutoModerator")?.id)
            assertGet("/user/AutoModerator/about")
        }

    @Test
    fun getUserPosts() =
        runTest {
            reddit.respond("/user/someone/submitted", readFixture("post_listing.json"))

            val posts = api().getUserPosts("someone")

            assertGet("/user/someone/submitted", mapOf("sort" to "new", "limit" to "25"))
            assertEquals(4, posts.items.size)
        }

    @Test
    fun getUserPosts_topSortHasATimeFilter() =
        runTest {
            api().getUserPosts("someone", sort = PostSort.TOP, after = "t3_a", limit = 5)

            assertGet("/user/someone/submitted", mapOf("sort" to "top", "t" to "all", "limit" to "5", "after" to "t3_a"))
        }

    @Test
    fun getUserComments() =
        runTest {
            reddit.respond("/user/someone/comments", readFixture("user_comments.json"))

            val comments = api().getUserComments("someone", sort = PostSort.CONTROVERSIAL, timeFilter = TimeFilter.MONTH)

            assertGet("/user/someone/comments", mapOf("sort" to "controversial", "t" to "month", "limit" to "25"))
            assertEquals(listOf("phx2cm1"), comments.items.map { it.id })
        }

    @Test
    fun getSavedPosts() =
        runTest {
            api().getSavedPosts("someone", after = "t3_a")

            assertGet("/user/someone/saved", mapOf("type" to "links", "limit" to "25", "after" to "t3_a"))
        }

    @Test
    fun getSavedComments() =
        runTest {
            reddit.respond("/user/someone/saved", readFixture("user_comments.json"))

            val comments = api().getSavedComments("someone")

            assertGet("/user/someone/saved", mapOf("type" to "comments", "limit" to "25"))
            assertEquals(1, comments.items.size)
        }

    @Test
    fun getUpvotedPosts() =
        runTest {
            api().getUpvotedPosts("someone")

            assertGet("/user/someone/upvoted", mapOf("limit" to "25"))
        }

    @Test
    fun getDownvotedPosts() =
        runTest {
            api().getDownvotedPosts("someone", limit = 3)

            assertGet("/user/someone/downvoted", mapOf("limit" to "3"))
        }

    // ==================== Search and inbox ====================

    @Test
    fun search_everywhere() =
        runTest {
            reddit.respond("/search", readFixture("post_listing.json"))

            val posts = api().search("halloween")

            assertGet(
                "/search",
                mapOf("q" to "halloween", "sort" to "relevance", "t" to "all", "type" to "link", "restrict_sr" to "false", "limit" to "25"),
            )
            assertEquals(4, posts.items.size)
        }

    @Test
    fun search_inASubreddit() =
        runTest {
            api().search("cats", subreddit = "aww", sort = SearchSort.NEW, time = TimeFilter.DAY, after = "t3_a", limit = 5)

            assertGet(
                "/r/aww/search",
                mapOf(
                    "q" to "cats",
                    "sort" to "new",
                    "t" to "day",
                    "type" to "link",
                    "restrict_sr" to "true",
                    "limit" to "5",
                    "after" to "t3_a",
                ),
            )
        }

    @Test
    fun getInbox() =
        runTest {
            reddit.respond("/message/unread", readFixture("message_listing.json"))

            val messages = api().getInbox(InboxFilter.UNREAD, after = "t4_a")

            assertGet("/message/unread", mapOf("limit" to "25", "after" to "t4_a"))
            assertEquals(4, messages.items.size)
        }

    @Test
    fun getInbox_everything() =
        runTest {
            api().getInbox()

            assertGet("/message/inbox", mapOf("limit" to "25"))
        }
}
