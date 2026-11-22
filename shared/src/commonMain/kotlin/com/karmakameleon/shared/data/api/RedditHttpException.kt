package com.karmakameleon.shared.data.api

import io.ktor.http.HttpStatusCode

// Reddit answered with an error status. The message is shown to the person, so it says what happened in plain words;
// the status code is kept for logs and for code that handles a status differently.
class RedditHttpException(
    val status: Int,
    isLoggedIn: Boolean,
) : Exception(messageFor(status, isLoggedIn)) {
    private companion object {
        fun messageFor(
            status: Int,
            isLoggedIn: Boolean,
        ): String =
            when {
                // Logged out, requests use an anonymous token, and RedditApi drops one Reddit rejected, so trying again gets a new one.
                status == HttpStatusCode.Unauthorized.value -> {
                    if (isLoggedIn) {
                        "Reddit didn't accept your login. Try signing in again."
                    } else {
                        "Reddit didn't accept the request. Try again."
                    }
                }

                status == HttpStatusCode.Forbidden.value -> {
                    "Reddit doesn't allow access to this."
                }

                status == HttpStatusCode.NotFound.value -> {
                    "Reddit couldn't find this."
                }

                status == HttpStatusCode.TooManyRequests.value -> {
                    "Reddit got too many requests. Wait a minute and try again."
                }

                status >= HttpStatusCode.InternalServerError.value -> {
                    "Reddit is unavailable right now. Try again later."
                }

                else -> {
                    "Reddit couldn't answer the request (HTTP $status)."
                }
            }
    }
}
