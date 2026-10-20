package com.karmakameleon.shared.data.api

import com.karmakameleon.shared.data.api.dto.AccessTokenResponse
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Runs the OkHttp clients the app uses against a local server. */
class HttpClientFactoryTest {
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/redirect") { it.respond(302, "", "Location" to "/target") }
            createContext("/target") { it.respond(200, "target") }
            createContext("/set-cookies") {
                it.respond(
                    200,
                    "",
                    "Set-Cookie" to "session=abc; Path=/",
                    "Set-Cookie" to "theme=dark; Path=/",
                )
            }
            createContext("/replace-cookies") {
                it.respond(
                    200,
                    "",
                    "Set-Cookie" to "session=xyz; Path=/",
                    // Already expired, so it's dropped.
                    "Set-Cookie" to "theme=dark; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
                )
            }
            createContext("/echo-cookies") { it.respond(200, it.requestHeaders.getFirst("Cookie").orEmpty()) }
            createContext("/echo-user-agent") { it.respond(200, it.requestHeaders.getFirst("User-Agent").orEmpty()) }
            createContext("/token") {
                it.respond(
                    200,
                    """{"access_token":"a","token_type":"bearer","expires_in":3600,"scope":"read","unknown":1}""",
                    "Content-Type" to "application/json",
                )
            }
            start()
        }

    private val baseUrl = "http://127.0.0.1:${server.address.port}"

    @AfterTest
    fun tearDown() {
        server.stop(0)
    }

    private fun HttpExchange.respond(
        status: Int,
        body: String,
        vararg headers: Pair<String, String>,
    ) {
        headers.forEach { (name, value) -> responseHeaders.add(name, value) }
        val bytes = body.encodeToByteArray()
        sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) responseBody.use { it.write(bytes) }
        close()
    }

    @Test
    fun followsRedirectsByDefault() =
        runTest {
            createHttpClient().use { client ->
                assertEquals("target", client.get("$baseUrl/redirect").bodyAsText())
            }
        }

    @Test
    fun withoutFollowingRedirects_returnsTheRedirect() =
        runTest {
            // Share links are resolved this way, by reading where they redirect to.
            createHttpClient(followRedirects = false).use { client ->
                val response = client.get("$baseUrl/redirect")

                assertEquals(HttpStatusCode.Found, response.status)
                assertEquals("/target", response.headers["Location"])
            }
        }

    @Test
    fun keepsCookiesForLaterRequests() =
        runTest {
            createHttpClient().use { client ->
                client.get("$baseUrl/set-cookies")
                assertEquals("session=abc; theme=dark", client.get("$baseUrl/echo-cookies").bodyAsText())

                client.get("$baseUrl/replace-cookies")
                assertEquals("session=xyz", client.get("$baseUrl/echo-cookies").bodyAsText())
            }
        }

    @Test
    fun cookiesAreNotSharedBetweenClients() =
        runTest {
            createHttpClient().use { it.get("$baseUrl/set-cookies") }

            createHttpClient().use { client ->
                assertEquals("", client.get("$baseUrl/echo-cookies").bodyAsText())
            }
        }

    @Test
    fun configuredClient_sendsTheUserAgentAndReadsJson() =
        runTest {
            createHttpClientWithConfig().use { client ->
                assertEquals("KK/1.0.0", client.get("$baseUrl/echo-user-agent").bodyAsText())
                // Fields the app doesn't know are ignored.
                assertEquals("a", client.get("$baseUrl/token").body<AccessTokenResponse>().accessToken)
            }
        }
}
