package com.karmakameleon.shared.data.api

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class RedditHttpExceptionTest {
    private fun message(status: HttpStatusCode) = RedditHttpException(status.value).message

    @Test
    fun messageSaysWhatHappened() {
        assertEquals("Reddit didn't accept the login. Try signing in again.", message(HttpStatusCode.Unauthorized))
        assertEquals("Reddit doesn't allow access to this.", message(HttpStatusCode.Forbidden))
        assertEquals("Reddit couldn't find this.", message(HttpStatusCode.NotFound))
        assertEquals("Reddit got too many requests. Wait a minute and try again.", message(HttpStatusCode.TooManyRequests))
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
