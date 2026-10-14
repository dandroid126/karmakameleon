package com.karmakameleon.shared.data.api

import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.Parameters
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthManagerTest {

    private fun createAuthManager(settings: MapSettings = MapSettings()): AuthManager {
        val client = HttpClient(MockEngine { respondOk() })
        return AuthManager(client, settings)
    }

    @Test
    fun getClientId_returnsEmptyWhenNotSet() {
        val authManager = createAuthManager()
        assertEquals("", authManager.getClientId())
    }

    @Test
    fun setClientId_persistsValue() {
        val authManager = createAuthManager()
        authManager.setClientId("test_client_id")
        assertEquals("test_client_id", authManager.getClientId())
    }

    @Test
    fun isLoggedIn_returnsFalseByDefault() {
        val authManager = createAuthManager()
        assertFalse(authManager.isLoggedIn())
    }

    @Test
    fun clearToken_clearsState() {
        val authManager = createAuthManager()
        authManager.clearToken()
        assertFalse(authManager.isLoggedIn())
    }

    @Test
    fun getAuthorizationUrl_containsClientId() {
        val authManager = createAuthManager()
        authManager.setClientId("my_client")
        val url = authManager.getAuthorizationUrl("test_state")
        assertTrue(url.contains("client_id=my_client"))
        assertTrue(url.contains("state=test_state"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("duration=permanent"))
    }

    @Test
    fun getAuthorizationUrl_containsRedirectUri() {
        val authManager = createAuthManager()
        authManager.setClientId("my_client")
        val url = authManager.getAuthorizationUrl("state")
        assertTrue(url.contains("redirect_uri="))
    }

    @Test
    fun getAuthorizationUrl_containsScopes() {
        val authManager = createAuthManager()
        authManager.setClientId("my_client")
        val url = authManager.getAuthorizationUrl("state")
        assertTrue(url.contains("scope="))
    }

    @Test
    fun isLoggedIn_returnsFalseAfterClear() {
        val settings = MapSettings()
        val authManager = createAuthManager(settings)
        authManager.clearToken()
        assertFalse(authManager.isLoggedIn())
    }

    @Test
    fun setClientId_overwritesPreviousValue() {
        val authManager = createAuthManager()
        authManager.setClientId("first")
        authManager.setClientId("second")
        assertEquals("second", authManager.getClientId())
    }

    @Test
    fun clientId_persistsAcrossInstances() {
        val settings = MapSettings()
        val authManager1 = createAuthManager(settings)
        authManager1.setClientId("persistent_id")

        val authManager2 = createAuthManager(settings)
        assertEquals("persistent_id", authManager2.getClientId())
    }

    @Test
    fun getRedirectUri_returnsDefaultWhenNotSet() {
        val authManager = createAuthManager()
        assertEquals(AuthManager.DEFAULT_REDIRECT_URI, authManager.getRedirectUri())
    }

    @Test
    fun redirectUri_persistsAcrossInstances() {
        val settings = MapSettings()
        createAuthManager(settings).setRedirectUri("myclient://callback")

        assertEquals("myclient://callback", createAuthManager(settings).getRedirectUri())
    }

    @Test
    fun redirectUri_survivesClearToken() {
        val authManager = createAuthManager()
        authManager.setRedirectUri("myclient://callback")
        authManager.clearToken()
        assertEquals("myclient://callback", authManager.getRedirectUri())
    }

    @Test
    fun setRedirectUri_stripsWhitespace() {
        val authManager = createAuthManager()
        authManager.setRedirectUri(" redreader://rr_oauth_redir\r\n")
        assertEquals("redreader://rr_oauth_redir", authManager.getRedirectUri())
    }

    @Test
    fun setClientId_stripsWhitespace() {
        val authManager = createAuthManager()
        authManager.setClientId("my_client\n")
        assertEquals("my_client", authManager.getClientId())
    }

    @Test
    fun getters_trimValuesSavedWithWhitespace() {
        val settings = MapSettings()
        settings.putString("client_id", "my_client\r\n")
        settings.putString("redirect_uri", "redreader://rr_oauth_redir\r\n")
        val authManager = createAuthManager(settings)

        assertEquals("my_client", authManager.getClientId())
        assertEquals("redreader://rr_oauth_redir", authManager.getRedirectUri())
        assertTrue(authManager.getAuthorizationUrl("state").contains("redirect_uri=redreader%3A%2F%2Frr_oauth_redir&"))
    }

    @Test
    fun getAuthorizationUrl_usesSavedRedirectUri() {
        val authManager = createAuthManager()
        authManager.setClientId("my_client")
        authManager.setRedirectUri("http://127.0.0.1:65010/authorize_callback")
        val url = authManager.getAuthorizationUrl("state")
        assertTrue(url.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A65010%2Fauthorize_callback"), url)
    }

    @Test
    fun exchangeCodeForToken_sendsSavedRedirectUri() = runTest {
        var sentForm: Parameters? = null
        val client = HttpClient(MockEngine { request ->
            sentForm = (request.body as FormDataContent).formData
            respondOk()
        })
        val authManager = AuthManager(client, MapSettings())
        authManager.setClientId("my_client")
        authManager.setRedirectUri("myclient://callback")

        authManager.exchangeCodeForToken("the_code")

        assertEquals("authorization_code", sentForm?.get("grant_type"))
        assertEquals("the_code", sentForm?.get("code"))
        assertEquals("myclient://callback", sentForm?.get("redirect_uri"))
    }
}
