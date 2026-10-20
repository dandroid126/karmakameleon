package com.karmakameleon.shared.data.api

import com.karmakameleon.shared.FakeAuthManager
import com.karmakameleon.shared.data.repository.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A Reddit API server for [RedditApi] tests. It records each request and answers with the response set for its path,
 * or an empty listing.
 */
internal class MockReddit {
    val requests = mutableListOf<HttpRequestData>()

    private val responses = mutableMapOf<String, Pair<HttpStatusCode, String>>()

    val authManager = FakeAuthManager()

    /** The first request made. A page of posts is followed by a lookup of whether its subreddits are NSFW. */
    val request: HttpRequestData get() = requests.first()

    fun respond(
        path: String,
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) {
        responses[path] = status to body
    }

    fun api(
        settingsRepository: SettingsRepository? = null,
        ioDispatcher: CoroutineDispatcher,
    ): RedditApi = RedditApi(client(ioDispatcher), authManager, settingsRepository, ioDispatcher)

    // Runs on the API's dispatcher, so a test dispatcher also waits for the requests made in the background.
    private fun client(dispatcher: CoroutineDispatcher): HttpClient =
        HttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    this.dispatcher = dispatcher
                    addHandler { request ->
                        requests += request
                        val (status, body) = responses[request.url.encodedPath] ?: (HttpStatusCode.OK to EMPTY_LISTING)
                        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                },
            ),
        )

    companion object {
        const val EMPTY_LISTING = """{"kind": "Listing", "data": {"children": []}}"""
    }
}

/** The fields of a form a request posted. */
internal fun HttpRequestData.form(): Parameters = (body as FormDataContent).formData

/** A request's query parameters, without raw_json, which every GET has. */
internal fun HttpRequestData.query(): Map<String, String> =
    url.parameters
        .entries()
        .associate { (name, values) -> name to values.single() } - "raw_json"

/** A subreddit as /api/info returns it, with only the fields Reddit always sends. */
internal fun subredditThing(
    name: String,
    nsfw: Boolean,
): JsonObject =
    buildJsonObject {
        put("kind", "t5")
        put(
            "data",
            buildJsonObject {
                put("id", name.lowercase())
                put("name", "t5_${name.lowercase()}")
                put("display_name", name)
                put("display_name_prefixed", "r/$name")
                put("title", name)
                put("created", 1.0)
                put("created_utc", 1.0)
                put("over18", nsfw)
                put("url", "/r/$name/")
            },
        )
    }

/** A listing of these things. */
internal fun listingOf(vararg things: JsonObject): String =
    buildJsonObject {
        put("kind", "Listing")
        put("data", buildJsonObject { put("children", JsonArray(things.toList())) })
    }.toString()
