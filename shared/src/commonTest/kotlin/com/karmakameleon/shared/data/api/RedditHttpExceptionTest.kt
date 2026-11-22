package com.karmakameleon.shared.data.api

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class RedditHttpExceptionTest {
    private fun message(
        status: HttpStatusCode,
        isLoggedIn: Boolean = true,
    ) = RedditHttpException(status.value, isLoggedIn).message

    @Test
    fun messageSaysWhatHappened() {
        assertEquals("Reddit doesn't allow access to this.", message(HttpStatusCode.Forbidden))
        assertEquals("Reddit couldn't find this.", message(HttpStatusCode.NotFound))
        assertEquals("Reddit got too many requests. Wait a minute and try again.", message(HttpStatusCode.TooManyRequests))
    }

    // Logged out, requests use an anonymous token, so there's no login to redo.
    @Test
    fun unauthorizedOnlyMentionsSigningInWhenLoggedIn() {
        assertEquals("Reddit didn't accept your login. Try signing in again.", message(HttpStatusCode.Unauthorized))
        assertEquals("Reddit didn't accept the request. Try again.", message(HttpStatusCode.Unauthorized, isLoggedIn = false))
    }

    @Test
    fun anyServerErrorMeansRedditIsUnavailable() {
        listOf(HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway, HttpStatusCode.GatewayTimeout).forEach {
            assertEquals("Reddit is unavailable right now. Try again later.", message(it))
        }
    }

    @Test
    fun otherStatusesNameTheStatus() {
        assertEquals("Reddit couldn't answer the request (HTTP 400).", message(HttpStatusCode.BadRequest))
    }
}
