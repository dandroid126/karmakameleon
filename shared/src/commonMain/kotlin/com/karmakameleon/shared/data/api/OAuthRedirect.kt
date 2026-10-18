package com.karmakameleon.shared.data.api

import io.ktor.http.Url

/** The parameters Reddit adds to the redirect URI after the user allows or denies access. */
data class OAuthRedirect(
    val code: String?,
    val state: String?,
    val error: String?,
) {
    companion object {
        /**
         * Returns the redirect's parameters if [url] is a redirect to [redirectUri], or null for any other page.
         * The scheme, host, port and path must match; Reddit appends its parameters as a query string.
         */
        fun parse(
            url: String,
            redirectUri: String,
        ): OAuthRedirect? {
            if (!redirectUri.contains("://")) return null
            val actual = parseUrl(url) ?: return null
            val expected = parseUrl(redirectUri) ?: return null
            val matches =
                actual.protocol.name.equals(expected.protocol.name, ignoreCase = true) &&
                    actual.host.equals(expected.host, ignoreCase = true) &&
                    actual.port == expected.port &&
                    actual.encodedPath.trimEnd('/') == expected.encodedPath.trimEnd('/')
            if (!matches) return null
            return OAuthRedirect(
                code = actual.parameters["code"],
                state = actual.parameters["state"],
                error = actual.parameters["error"],
            )
        }

        private fun parseUrl(url: String): Url? =
            try {
                Url(url.trim())
            } catch (_: Exception) {
                null
            }
    }
}
