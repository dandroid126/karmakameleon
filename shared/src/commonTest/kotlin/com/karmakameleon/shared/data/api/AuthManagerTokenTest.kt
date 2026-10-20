package com.karmakameleon.shared.data.api

import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How [AuthManager] hands out access tokens: a saved one, a refreshed one, or an anonymous one. */
class AuthManagerTokenTest {
    private val settings = MapSettings()
    private val tokenSettings = MapSettings()
    private val requests = mutableListOf<HttpRequestData>()

    // The answer to each grant_type; a grant that isn't here is refused.
    private val answers = mutableMapOf<String, String>()

    private val forms: List<Parameters> get() = requests.map { (it.body as FormDataContent).formData }

    private fun createAuthManager(clientId: String = "my_client"): AuthManager {
        val client =
            HttpClient(
                MockEngine { request ->
                    requests += request
                    val grant = (request.body as FormDataContent).formData["grant_type"]
                    val answer = answers[grant]
                    if (answer != null) {
                        respond(answer, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                    } else {
                        respond("""{"error": "invalid_grant"}""", HttpStatusCode.BadRequest)
                    }
                },
            ) {
                install(ContentNegotiation) { json() }
            }
        return AuthManager(client, settings, tokenSettings).apply { setClientId(clientId) }
    }

    private fun saveToken(
        expiresAt: Long,
        refreshToken: String? = "saved_refresh",
    ) {
        val refresh = refreshToken?.let { "\"$it\"" } ?: "null"
        tokenSettings.putString(
            TOKEN_KEY,
            """{"accessToken":"saved_access","refreshToken":$refresh,"expiresAt":$expiresAt,"scope":"read"}""",
        )
    }

    @Test
    fun aSavedTokenIsUsedUntilItExpires() =
        runTest {
            saveToken(expiresAt = FAR_FUTURE)

            assertEquals("saved_access", createAuthManager().getAccessToken())
            assertTrue(requests.isEmpty())
        }

    @Test
    fun anExpiredTokenIsRefreshed() =
        runTest {
            saveToken(expiresAt = LONG_AGO)
            // Reddit doesn't send the refresh token again, so the saved one is kept.
            answers["refresh_token"] = """{"access_token":"new_access","token_type":"bearer","expires_in":3600,"scope":"read"}"""
            val authManager = createAuthManager()

            assertEquals("new_access", authManager.getAccessToken())

            assertEquals("saved_refresh", forms.single()["refresh_token"])
            assertEquals("Basic bXlfY2xpZW50Og==", requests.single().headers[HttpHeaders.Authorization])
            assertTrue(authManager.isLoggedIn())
            assertTrue(tokenSettings.getStringOrNull(TOKEN_KEY)!!.contains("\"refreshToken\":\"saved_refresh\""))
            // The refreshed token is used from then on.
            assertEquals("new_access", authManager.getAccessToken())
            assertEquals(1, requests.size)
        }

    @Test
    fun whenTheRefreshFails_logsOutAndUsesAnAnonymousToken() =
        runTest {
            saveToken(expiresAt = LONG_AGO)
            answers[ANONYMOUS_GRANT] = ANONYMOUS_TOKEN
            val authManager = createAuthManager()
            assertTrue(authManager.isLoggedInFlow.value)

            assertEquals("anonymous_access", authManager.getAccessToken())

            assertEquals(listOf("refresh_token", ANONYMOUS_GRANT), forms.map { it["grant_type"] })
            assertFalse(authManager.isLoggedIn())
            assertFalse(authManager.isLoggedInFlow.value)
            assertFalse(tokenSettings.hasKey(TOKEN_KEY))
        }

    @Test
    fun anExpiredAnonymousTokenIsReplaced() =
        runTest {
            saveToken(expiresAt = LONG_AGO, refreshToken = null)
            answers[ANONYMOUS_GRANT] = ANONYMOUS_TOKEN

            assertEquals("anonymous_access", createAuthManager().getAccessToken())
            assertEquals(listOf(ANONYMOUS_GRANT), forms.map { it["grant_type"] })
        }

    @Test
    fun withoutALogin_getsAnAnonymousTokenForThisDevice() =
        runTest {
            answers[ANONYMOUS_GRANT] = ANONYMOUS_TOKEN
            val authManager = createAuthManager()

            assertEquals("anonymous_access", authManager.getAccessToken())
            assertEquals("anonymous_access", authManager.getAccessToken())

            // The anonymous token is kept until it expires, but not saved, and it isn't a login.
            val deviceId = forms.single()["device_id"]!!
            assertTrue(Regex("[a-z0-9]{30}").matches(deviceId), deviceId)
            assertFalse(tokenSettings.hasKey(TOKEN_KEY))
            assertFalse(authManager.isLoggedIn())

            // The device ID is saved, so the next session uses the same one.
            createAuthManager().getAccessToken()
            assertEquals(deviceId, forms.last()["device_id"])
        }

    @Test
    fun withoutAClientId_thereIsNoToken() =
        runTest {
            assertNull(createAuthManager(clientId = "").getAccessToken())
            assertTrue(requests.isEmpty())
        }

    @Test
    fun whenRedditRefusesAnAnonymousToken_thereIsNoToken() =
        runTest {
            assertNull(createAuthManager().getAccessToken())
            assertEquals(1, requests.size)
        }

    @Test
    fun aSavedTokenThatCannotBeReadIsDropped() {
        tokenSettings.putString(TOKEN_KEY, "not json")

        val authManager = createAuthManager()

        assertFalse(authManager.isLoggedIn())
        assertFalse(tokenSettings.hasKey(TOKEN_KEY))
    }

    private companion object {
        const val TOKEN_KEY = "token_info"
        const val ANONYMOUS_GRANT = "https://oauth.reddit.com/grants/installed_client"
        const val ANONYMOUS_TOKEN = """{"access_token":"anonymous_access","token_type":"bearer","expires_in":3600,"scope":"*"}"""

        // 2100-01-01 and 2000-01-01, in seconds.
        const val FAR_FUTURE = 4102444800L
        const val LONG_AGO = 946684800L
    }
}
