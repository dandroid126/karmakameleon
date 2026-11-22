package com.karmakameleon.shared.data.api

import io.ktor.http.HttpStatusCode

// Reddit answered with an error status. The message is shown to the person, so it says what happened in plain words;
// the status code is kept for logs and for code that handles a status differently.
class RedditHttpException(
    val status: Int,
) : Exception(messageFor(status)) {
    private companion object {
        fun messageFor(status: Int): String =
            when {
                status == HttpStatusCode.Unauthorized.value -> "Reddit didn't accept the login. Try signing in again."
                status == HttpStatusCode.Forbidden.value -> "Reddit doesn't allow access to this."
                status == HttpStatusCode.NotFound.value -> "Reddit couldn't find this."
                status == HttpStatusCode.TooManyRequests.value -> "Reddit got too many requests. Wait a minute and try again."
                status >= HttpStatusCode.InternalServerError.value -> "Reddit is unavailable right now. Try again later."
                else -> "Reddit couldn't answer the request (HTTP $status)."
            }
    }
}
