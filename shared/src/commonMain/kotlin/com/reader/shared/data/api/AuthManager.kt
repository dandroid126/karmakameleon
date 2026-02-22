package com.reader.shared.data.api

import com.reader.shared.data.api.dto.AccessTokenResponse
import com.reader.shared.data.api.dto.TokenInfo
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

open class AuthManager(
    private val httpClient: HttpClient,
    private val settings: Settings,
) {
    companion object {
        private const val KEY_TOKEN_INFO = "token_info"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_CLIENT_ID = "client_id"
        
        const val REDIRECT_URI = "reader://oauth"
        const val SCOPES = "identity edit flair history modconfig modflair modlog modposts modwiki mysubreddits privatemessages read report save submit subscribe vote wikiread"
        
        private const val AUTH_URL = "https://www.reddit.com/api/v1"
        private const val TOKEN_EXPIRY_BUFFER = 60 // seconds before expiry to refresh
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cachedToken: TokenInfo? = null
    
    private val _isLoggedInFlow = MutableStateFlow(false)
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

    init {
        loadCachedToken()
        _isLoggedInFlow.value = isLoggedIn()
    }

    private fun loadCachedToken() {
        val tokenJson: String? = settings[KEY_TOKEN_INFO]
        if (tokenJson != null) {
            try {
                cachedToken = json.decodeFromString(tokenJson)
            } catch (e: Exception) {
                Napier.e("Failed to load cached token", e)
                settings.remove(KEY_TOKEN_INFO)
            }
        }
    }

    open fun getClientId(): String = settings[KEY_CLIENT_ID] ?: ""

    open fun setClientId(clientId: String) {
        settings[KEY_CLIENT_ID] = clientId
    }

    open fun getAuthorizationUrl(state: String): String {
        val clientId = getClientId()
        return buildString {
            append("https://www.reddit.com/api/v1/authorize.compact")
            append("?client_id=$clientId")
            append("&response_type=code")
            append("&state=$state")
            append("&redirect_uri=${REDIRECT_URI.encodeURLParameter()}")
            append("&duration=permanent")
            append("&scope=${SCOPES.encodeURLParameter()}")
        }
    }

    open suspend fun exchangeCodeForToken(code: String): Boolean {
        return try {
            val response = httpClient.post("$AUTH_URL/access_token") {
                header(HttpHeaders.Authorization, "Basic ${encodeCredentials()}")
                setBody(FormDataContent(Parameters.build {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("redirect_uri", REDIRECT_URI)
                }))
            }

            if (response.status.isSuccess()) {
                val tokenResponse: AccessTokenResponse = response.body()
                saveToken(tokenResponse)
                true
            } else {
                Napier.e("Failed to exchange code: ${response.status}")
                false
            }
        } catch (e: Exception) {
            Napier.e("Failed to exchange code for token", e)
            false
        }
    }

    open suspend fun getAccessToken(): String? {
        return mutex.withLock {
            val token = cachedToken ?: return@withLock getAnonymousToken()
            
            val currentTime = System.currentTimeMillis() / 1000
            if (token.expiresAt - TOKEN_EXPIRY_BUFFER > currentTime) {
                return@withLock token.accessToken
            }

            // Token expired or about to expire, refresh it
            if (token.refreshToken != null) {
                val refreshed = refreshToken(token.refreshToken)
                if (refreshed) {
                    return@withLock cachedToken?.accessToken
                }
            }

            // Refresh failed, clear token and return anonymous
            clearToken()
            getAnonymousToken()
        }
    }

    private suspend fun refreshToken(refreshToken: String): Boolean {
        return try {
            val response = httpClient.post("$AUTH_URL/access_token") {
                header(HttpHeaders.Authorization, "Basic ${encodeCredentials()}")
                setBody(FormDataContent(Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refreshToken)
                }))
            }

            if (response.status.isSuccess()) {
                val tokenResponse: AccessTokenResponse = response.body()
                saveToken(tokenResponse, existingRefreshToken = refreshToken)
                true
            } else {
                Napier.e("Failed to refresh token: ${response.status}")
                false
            }
        } catch (e: Exception) {
            Napier.e("Failed to refresh token", e)
            false
        }
    }

    private suspend fun getAnonymousToken(): String? {
        if (getClientId().isEmpty()) {
            Napier.w("No client ID configured, cannot fetch anonymous token")
            return null
        }
        return try {
            val deviceId = getOrCreateDeviceId()
            val response = httpClient.post("$AUTH_URL/access_token") {
                header(HttpHeaders.Authorization, "Basic ${encodeCredentials()}")
                setBody(FormDataContent(Parameters.build {
                    append("grant_type", "https://oauth.reddit.com/grants/installed_client")
                    append("device_id", deviceId)
                }))
            }

            if (response.status.isSuccess()) {
                val tokenResponse: AccessTokenResponse = response.body()
                val tokenInfo = TokenInfo(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = null,
                    expiresAt = System.currentTimeMillis() / 1000 + tokenResponse.expiresIn,
                    scope = tokenResponse.scope
                )
                cachedToken = tokenInfo
                tokenResponse.accessToken
            } else {
                Napier.e("Failed to get anonymous token: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Napier.e("Failed to get anonymous token", e)
            null
        }
    }

    private fun saveToken(response: AccessTokenResponse, existingRefreshToken: String? = null) {
        val tokenInfo = TokenInfo(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken ?: existingRefreshToken,
            expiresAt = System.currentTimeMillis() / 1000 + response.expiresIn,
            scope = response.scope
        )
        cachedToken = tokenInfo
        settings[KEY_TOKEN_INFO] = json.encodeToString(tokenInfo)
        _isLoggedInFlow.value = isLoggedIn()
    }

    open fun clearToken() {
        cachedToken = null
        settings.remove(KEY_TOKEN_INFO)
        _isLoggedInFlow.value = false
    }

    open fun isLoggedIn(): Boolean {
        return cachedToken?.refreshToken != null
    }

    private fun getOrCreateDeviceId(): String {
        var deviceId: String? = settings[KEY_DEVICE_ID]
        if (deviceId == null) {
            deviceId = generateDeviceId()
            settings[KEY_DEVICE_ID] = deviceId
        }
        return deviceId
    }

    private fun generateDeviceId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..30).map { chars.random() }.joinToString("")
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun encodeCredentials(): String {
        val credentials = "${getClientId()}:"
        return Base64.encode(credentials.encodeToByteArray())
    }
}
