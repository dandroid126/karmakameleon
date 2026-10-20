package com.karmakameleon.shared.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

actual fun createHttpClient(followRedirects: Boolean): HttpClient =
    HttpClient(OkHttp) {
        // OkHttp follows redirects itself, before Ktor sees the response, so it has to be told too.
        this.followRedirects = followRedirects
        engine {
            config {
                followRedirects(followRedirects)
                retryOnConnectionFailure(true)
                cookieJar(InMemoryCookieJar())
            }
        }
    }

private class InMemoryCookieJar : CookieJar {
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    @Synchronized
    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        val existing = cookieStore.getOrPut(url.host) { mutableListOf() }
        for (newCookie in cookies) {
            existing.removeAll { it.name == newCookie.name && it.domain == newCookie.domain && it.path == newCookie.path }
            existing.add(newCookie)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookies = cookieStore[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt <= now }
        return cookies.toList()
    }
}
