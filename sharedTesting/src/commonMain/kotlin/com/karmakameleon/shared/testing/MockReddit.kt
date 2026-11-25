package com.karmakameleon.shared.testing

import com.karmakameleon.shared.data.api.RedditApi
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
import io.ktor.http.headers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A Reddit API server for [RedditApi] tests. It records each request and answers with the response set for its path,
 * or an empty listing.
 */
class MockReddit {
    val requests = mutableListOf<HttpRequestData>()

    private class Response(
        val status: HttpStatusCode,
        val body: String,
        val contentType: String,
        val location: String? = null,
    )

    private val responses = mutableMapOf<String, Response>()

    private val failures = mutableMapOf<String, Throwable>()

    // The paths whose requests wait for letThrough, each holding one permit per call.
    private val heldPaths = mutableMapOf<String, Channel<Unit>>()

    val authManager = FakeAuthManager()

    /** The first request made. A page of posts is followed by a lookup of whether its subreddits are NSFW. */
    val request: HttpRequestData get() = requests.first()

    fun respond(
        path: String,
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        contentType: String = "application/json",
    ) {
        // The latest call for a path wins, so a test can fail a request and then let a retry succeed.
        failures.remove(path)
        responses[path] = Response(status, body, contentType)
    }

    /** Answers requests for [path] with a redirect to [location], the way Reddit answers a share link. */
    fun redirect(
        path: String,
        location: String,
    ) {
        failures.remove(path)
        responses[path] = Response(HttpStatusCode.MovedPermanently, "", "text/html", location)
    }

    /**
     * Answers an image upload: the lease gives [assetId] and an upload URL at [UPLOAD_URL], the way Reddit's lacks a
     * scheme, and the upload there succeeds.
     */
    fun respondToImageUpload(assetId: String) {
        respond(
            "/api/media/asset.json",
            """
            {"args": {"action": "${UPLOAD_URL.removePrefix("https:")}",
                      "fields": [{"name": "key", "value": "uploads/$assetId"}, {"name": "policy", "value": "signed"}]},
             "asset": {"asset_id": "$assetId"}}
            """.trimIndent(),
        )
        respond(UPLOAD_PATH, "", HttpStatusCode.Created)
    }

    /** Makes requests for [path] throw [error] instead of getting a response, like a dropped connection does. */
    fun fail(
        path: String,
        error: Throwable,
    ) {
        responses.remove(path)
        failures[path] = error
    }

    /** Makes requests for [path] wait until [letThrough] is called, one request per call, to test what shows while one loads. */
    fun holdRequests(path: String) {
        heldPaths[path] = Channel(Channel.UNLIMITED)
    }

    /** Lets the next held request for [path] get its response. */
    fun letThrough(path: String) {
        heldPaths.getValue(path).trySend(Unit)
    }

    fun api(
        settingsRepository: SettingsRepository? = null,
        ioDispatcher: CoroutineDispatcher,
    ): RedditApi =
        RedditApi(
            client(ioDispatcher),
            authManager,
            settingsRepository,
            ioDispatcher,
            // Comments are read on it too, so a test dispatcher waits for that as well.
            parseDispatcher = ioDispatcher,
            createShareLinkClient = { client(ioDispatcher, followRedirects = false) },
        )

    /** A plain client on this server, like the one the app downloads linked images with. */
    fun httpClient(dispatcher: CoroutineDispatcher): HttpClient = client(dispatcher)

    // Runs on the API's dispatcher, so a test dispatcher also waits for the requests made in the background.
    private fun client(
        dispatcher: CoroutineDispatcher,
        followRedirects: Boolean = true,
    ): HttpClient =
        HttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    this.dispatcher = dispatcher
                    addHandler { request ->
                        requests += request
                        heldPaths[request.url.encodedPath]?.receive()
                        failures[request.url.encodedPath]?.let { throw it }
                        val response = responses[request.url.encodedPath] ?: Response(HttpStatusCode.OK, EMPTY_LISTING, "application/json")
                        val headers =
                            headers {
                                append(HttpHeaders.ContentType, response.contentType)
                                response.location?.let { append(HttpHeaders.Location, it) }
                            }
                        respond(response.body, response.status, headers)
                    }
                },
            ),
        ) { this.followRedirects = followRedirects }

    companion object {
        const val EMPTY_LISTING = """{"kind": "Listing", "data": {"children": []}}"""

        /** Where [respondToImageUpload] has images uploaded to, standing in for Reddit's bucket on Amazon S3. */
        const val UPLOAD_PATH = "/uploads"
        const val UPLOAD_URL = "https://uploads.test$UPLOAD_PATH"
    }
}

/** The fields of a form a request posted. */
fun HttpRequestData.form(): Parameters = (body as FormDataContent).formData

/** A request's query parameters, without raw_json, which every GET has. */
fun HttpRequestData.query(): Map<String, String> =
    url.parameters
        .entries()
        .associate { (name, values) -> name to values.single() } - "raw_json"

/** A subreddit as /api/info returns it, with only the fields Reddit always sends, plus its member count if given. */
fun subredditThing(
    name: String,
    nsfw: Boolean,
    subscribers: Long? = null,
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
                subscribers?.let { put("subscribers", it) }
            },
        )
    }

/** A listing of these things. */
fun listingOf(vararg things: JsonObject): String =
    buildJsonObject {
        put("kind", "Listing")
        put("data", buildJsonObject { put("children", JsonArray(things.toList())) })
    }.toString()
