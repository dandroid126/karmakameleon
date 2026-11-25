package com.karmakameleon.shared.data.api

import com.karmakameleon.shared.testing.FakeAuthManager
import com.karmakameleon.shared.util.RedditLink
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RedditApiShareLinkTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun createApi(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): RedditApi =
        RedditApi(
            httpClient = HttpClient(MockEngine { respondOk() }),
            authManager = FakeAuthManager(),
            createShareLinkClient = {
                HttpClient(
                    MockEngine { request ->
                        requests += request
                        handler(request)
                    },
                ) { followRedirects = false }
            },
        )

    private fun MockRequestHandleScope.redirectTo(location: String) =
        respond("", HttpStatusCode.MovedPermanently, headersOf(HttpHeaders.Location, location))

    @Test
    fun resolveShareLink_returnsThePostItRedirectsTo() =
        runTest {
            val api = createApi { redirectTo("https://www.reddit.com/r/Kotlin/comments/abc123/some_title/?share_id=x") }

            assertEquals(RedditLink.Post("Kotlin", "abc123"), api.resolveShareLink("kotlin", "AbCdEf"))
            assertEquals("https://www.reddit.com/r/kotlin/s/AbCdEf", requests.single().url.toString())
        }

    @Test
    fun resolveShareLink_returnsTheCommentItRedirectsTo() =
        runTest {
            val api = createApi { redirectTo("https://www.reddit.com/r/Kotlin/comments/abc123/comment/def456/?share_id=x") }

            assertEquals(RedditLink.Comment("Kotlin", "abc123", "def456"), api.resolveShareLink("kotlin", "AbCdEf"))
        }

    // Reddit only serves a profile's share links under /u/<name>/: /r/u_<name>/s/<id> redirects to the profile.
    @Test
    fun resolveShareLink_asksForAProfileShareLinkUnderTheProfile() =
        runTest {
            val api =
                createApi {
                    redirectTo("https://www.reddit.com/user/testuser/comments/abc123/comment/def456/?share_id=x")
                }

            assertEquals(RedditLink.Comment("u_testuser", "abc123", "def456"), api.resolveShareLink("u_testuser", "AbCdEf"))
            assertEquals("https://www.reddit.com/u/testuser/s/AbCdEf", requests.single().url.toString())
        }

    @Test
    fun resolveShareLink_returnsNullWithoutARedirect() =
        runTest {
            val api = createApi { respondOk() }

            assertNull(api.resolveShareLink("kotlin", "AbCdEf"))
        }

    @Test
    fun resolveShareLink_returnsNullWhenTheRedirectIsNotAPostOrComment() =
        runTest {
            val api = createApi { redirectTo("https://www.reddit.com/r/Kotlin/") }

            assertNull(api.resolveShareLink("kotlin", "AbCdEf"))
        }

    @Test
    fun resolveShareLink_returnsNullWhenTheRequestFails() =
        runTest {
            val api = createApi { error("No network") }

            assertNull(api.resolveShareLink("kotlin", "AbCdEf"))
        }
}
