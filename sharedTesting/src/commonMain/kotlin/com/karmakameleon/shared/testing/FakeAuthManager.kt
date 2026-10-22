package com.karmakameleon.shared.testing

import com.karmakameleon.shared.data.api.AuthManager
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode

class FakeAuthManager(
    private val settings: MapSettings = MapSettings(),
) : AuthManager(
        httpClient = HttpClient(MockEngine { respond("", HttpStatusCode.OK) }),
        settings = settings,
        tokenSettings = MapSettings(),
    ) {
    var fakeAccessToken: String? = "fake_token"
    var fakeIsLoggedIn: Boolean = false
    var exchangeSucceeds: Boolean = true
    val exchangedCodes = mutableListOf<String>()

    override suspend fun getAccessToken(): String? = fakeAccessToken

    override fun isLoggedIn(): Boolean = fakeIsLoggedIn

    override suspend fun exchangeCodeForToken(code: String): Boolean {
        exchangedCodes += code
        return exchangeSucceeds
    }

    override fun clearToken() {
        fakeIsLoggedIn = false
        fakeAccessToken = null
    }
}
