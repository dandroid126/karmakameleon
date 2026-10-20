package com.karmakameleon.shared.data.api

import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Checks the forms [RedditApi] posts to act on Reddit, and how it reads the answers. */
class RedditApiActionTest {
    private val reddit = MockReddit()

    private fun TestScope.api(): RedditApi = reddit.api(ioDispatcher = StandardTestDispatcher(testScheduler))

    private fun assertPost(
        path: String,
        form: Map<String, String>,
    ) {
        val request = reddit.request
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://oauth.reddit.com$path", request.url.toString())
        assertEquals(form, request.form().entries().associate { (name, values) -> name to values.single() })
        assertEquals("Bearer fake_token", request.headers[HttpHeaders.Authorization])
    }

    private val rateLimited = """{"json": {"errors": [["RATELIMIT", "Take a break for 3 minutes.", "ratelimit"]]}}"""

    // ==================== Votes, saves and hides ====================

    @Test
    fun vote() =
        runTest {
            assertTrue(api().vote("t3_abc", 1))

            assertPost("/api/vote", mapOf("id" to "t3_abc", "dir" to "1"))
        }

    @Test
    fun vote_throwsTheErrorRedditReports() =
        runTest {
            reddit.respond("/api/vote", rateLimited, HttpStatusCode.BadRequest)

            val error = assertFailsWith<RedditApiException> { api().vote("t3_abc", -1) }

            assertEquals("RATELIMIT", error.errorCode)
        }

    @Test
    fun vote_falseWhenItFailsWithoutAnError() =
        runTest {
            reddit.respond("/api/vote", "<html>Bad gateway</html>", HttpStatusCode.BadGateway)

            assertFalse(api().vote("t3_abc", 0))
        }

    @Test
    fun saveAndHide() =
        runTest {
            val api = api()
            val actions =
                mapOf<String, suspend () -> Boolean>(
                    "/api/save" to { api.save("t3_abc") },
                    "/api/unsave" to { api.unsave("t3_abc") },
                    "/api/hide" to { api.hide("t3_abc") },
                    "/api/unhide" to { api.unhide("t3_abc") },
                    "/api/del" to { api.deleteComment("t3_abc") },
                    "/api/read_message" to { api.markMessageRead("t3_abc") },
                    "/api/unread_message" to { api.markMessageUnread("t3_abc") },
                )

            actions.forEach { (path, action) ->
                reddit.requests.clear()
                assertTrue(action(), path)
                assertPost(path, mapOf("id" to "t3_abc"))
            }
        }

    @Test
    fun save_falseWhenItFails() =
        runTest {
            reddit.respond("/api/save", "", HttpStatusCode.InternalServerError)

            assertFalse(api().save("t3_abc"))
        }

    // ==================== Subreddits ====================

    @Test
    fun subscribe() =
        runTest {
            assertTrue(api().subscribe("Kotlin"))

            assertPost("/api/subscribe", mapOf("action" to "sub", "sr_name" to "Kotlin"))
        }

    @Test
    fun unsubscribe() =
        runTest {
            assertTrue(api().unsubscribe("Kotlin"))

            assertPost("/api/subscribe", mapOf("action" to "unsub", "sr_name" to "Kotlin"))
        }

    // ==================== Comments ====================

    @Test
    fun submitComment() =
        runTest {
            val comment = thing("user_comments.json", "phx2cm1")
            reddit.respond("/api/comment", """{"json": {"errors": [], "data": {"things": [{"kind": "t1", "data": $comment}]}}}""")

            val result = api().submitComment("t1_parent", "Nice post")

            assertPost("/api/comment", mapOf("api_type" to "json", "thing_id" to "t1_parent", "text" to "Nice post"))
            assertEquals("phx2cm1", result?.id)
        }

    @Test
    fun submitComment_throwsAnErrorReportedWithASuccessStatus() =
        runTest {
            reddit.respond("/api/comment", rateLimited)

            val error = assertFailsWith<RedditApiException> { api().submitComment("t1_parent", "Nice post") }

            assertEquals("Take a break for 3 minutes.", error.message)
        }

    @Test
    fun submitComment_nullWhenItFails() =
        runTest {
            reddit.respond("/api/comment", "", HttpStatusCode.InternalServerError)

            assertNull(api().submitComment("t1_parent", "Nice post"))
        }

    @Test
    fun editComment() =
        runTest {
            assertTrue(api().editComment("t1_abc", "Edited"))

            assertPost("/api/editusertext", mapOf("api_type" to "json", "thing_id" to "t1_abc", "text" to "Edited"))
        }

    // ==================== Messages ====================

    @Test
    fun sendMessage() =
        runTest {
            assertTrue(api().sendMessage("someone", "Hi", "Hello there"))

            assertPost("/api/compose", mapOf("api_type" to "json", "to" to "someone", "subject" to "Hi", "text" to "Hello there"))
        }

    @Test
    fun blockUser() =
        runTest {
            assertTrue(api().blockUser("someone"))

            assertPost("/api/block_user", mapOf("name" to "someone"))
        }

    @Test
    fun markAllMessagesRead_postsNoForm() =
        runTest {
            assertTrue(api().markAllMessagesRead())

            assertEquals(HttpMethod.Post, reddit.request.method)
            assertEquals("https://oauth.reddit.com/api/read_all_messages", reddit.request.url.toString())
            assertFalse(reddit.request.body is FormDataContent)
        }

    // ==================== Submit ====================

    @Test
    fun submitPost_selfPostLeavesOutUnsetFields() =
        runTest {
            reddit.respond(
                "/api/submit",
                """{"json": {"errors": [], "data": {"url": "https://www.reddit.com/r/test/comments/abc/hi/", "name": "t3_abc"}}}""",
            )

            val name = api().submitPost("test", "Hi", "self", text = "Body")

            assertPost(
                "/api/submit",
                mapOf(
                    "api_type" to "json",
                    "sr" to "test",
                    "title" to "Hi",
                    "kind" to "self",
                    "text" to "Body",
                    "nsfw" to "false",
                    "spoiler" to "false",
                ),
            )
            assertEquals("t3_abc", name)
        }

    @Test
    fun submitPost_linkWithFlair() =
        runTest {
            api().submitPost(
                "test",
                "Look",
                "link",
                url = "https://example.com",
                nsfw = true,
                spoiler = true,
                flairId = "f1",
                flairText = "News",
            )

            assertPost(
                "/api/submit",
                mapOf(
                    "api_type" to "json",
                    "sr" to "test",
                    "title" to "Look",
                    "kind" to "link",
                    "url" to "https://example.com",
                    "nsfw" to "true",
                    "spoiler" to "true",
                    "flair_id" to "f1",
                    "flair_text" to "News",
                ),
            )
        }

    @Test
    fun submitPost_nullWhenItFails() =
        runTest {
            reddit.respond("/api/submit", "", HttpStatusCode.Forbidden)

            assertNull(api().submitPost("test", "Hi", "self"))
        }
}
