package com.karmakameleon.shared.data.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val REQUEST_TIMEOUT_MILLIS = 30_000L
private const val CONNECT_TIMEOUT_MILLIS = 15_000L
private const val SOCKET_TIMEOUT_MILLIS = 30_000L

// Reddit's API rules ask for "<platform>:<app ID>:<version> (by /u/<username>)". This is off spec in two ways, on purpose:
// it has no "by" part, to keep the developer's username out of the public repo, and "kk" isn't the app's real ID (see below).
// If requests start failing with a 403 HTML page saying "You've been blocked by network security", check this value first.
//
// Don't put "kameleo" in it, even as part of the app's name. In October 2026, a User-Agent containing that text got no
// rate limit headers from Reddit and 429s after about 14 requests. Without it, /api/info and /api/morechildren answered
// with that 403 for every value tried, in Reddit's format or not, so the app doesn't use them to load comments.
internal const val REDDIT_USER_AGENT = "android:kk:v1.0.0"

// Makes a client on the platform's engine. Each platform's Koin module provides one.
fun interface HttpClientFactory {
    fun create(followRedirects: Boolean): HttpClient
}

fun createHttpClientWithConfig(factory: HttpClientFactory): HttpClient =
    factory.create(followRedirects = true).config {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                    encodeDefaults = true
                },
            )
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.HEADERS
        }

        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
        }

        defaultRequest {
            headers.append(HttpHeaders.UserAgent, REDDIT_USER_AGENT)
        }
    }
