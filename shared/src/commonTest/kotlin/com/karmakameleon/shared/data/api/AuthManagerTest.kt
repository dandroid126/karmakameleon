package com.karmakameleon.shared.data.api

import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
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
}
