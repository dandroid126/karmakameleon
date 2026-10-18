package com.karmakameleon.shared.data.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OAuthRedirectTest {
    @Test
    fun parse_customScheme_returnsCodeAndState() {
        val redirect = OAuthRedirect.parse("karmakameleon://oauth?state=abc&code=xyz", "karmakameleon://oauth")
        assertEquals(OAuthRedirect(code = "xyz", state = "abc", error = null), redirect)
    }

    @Test
    fun parse_ignoresRedditFragment() {
        // Reddit appends "#_" to the redirect.
        val redirect = OAuthRedirect.parse("karmakameleon://oauth?state=abc&code=xyz#_", "karmakameleon://oauth")
        assertEquals("xyz", redirect?.code)
    }

    @Test
    fun parse_returnsError() {
        val redirect = OAuthRedirect.parse("karmakameleon://oauth?state=abc&error=access_denied", "karmakameleon://oauth")
        assertEquals(OAuthRedirect(code = null, state = "abc", error = "access_denied"), redirect)
    }

    @Test
    fun parse_otherClientScheme() {
        val redirect = OAuthRedirect.parse("myclient://callback?state=s&code=c", "myclient://callback")
        assertEquals("c", redirect?.code)
    }

    @Test
    fun parse_httpsRedirectWithPath() {
        val redirect =
            OAuthRedirect.parse(
                "https://example.com/reddit/callback?state=s&code=c",
                "https://example.com/reddit/callback",
            )
        assertEquals("c", redirect?.code)
    }

    @Test
    fun parse_loopbackWithPort() {
        val redirect =
            OAuthRedirect.parse(
                "http://127.0.0.1:65010/authorize_callback?state=s&code=c",
                "http://127.0.0.1:65010/authorize_callback",
            )
        assertEquals("c", redirect?.code)
    }

    @Test
    fun parse_ignoresTrailingSlashAndCase() {
        assertNotNull(OAuthRedirect.parse("http://localhost/?state=s&code=c", "http://localhost"))
        assertNotNull(OAuthRedirect.parse("KarmaKameleon://OAuth?state=s&code=c", "karmakameleon://oauth"))
    }

    @Test
    fun parse_ignoresSurroundingWhitespaceInRedirectUri() {
        assertNotNull(OAuthRedirect.parse("karmakameleon://oauth?state=s&code=c", "  karmakameleon://oauth "))
    }

    @Test
    fun parse_redditPage_returnsNull() {
        assertNull(
            OAuthRedirect.parse(
                "https://www.reddit.com/api/v1/authorize.compact?client_id=id&redirect_uri=karmakameleon%3A%2F%2Foauth",
                "karmakameleon://oauth",
            ),
        )
    }

    @Test
    fun parse_differentScheme_returnsNull() {
        assertNull(OAuthRedirect.parse("http://oauth?state=s&code=c", "karmakameleon://oauth"))
    }

    @Test
    fun parse_differentHost_returnsNull() {
        assertNull(OAuthRedirect.parse("karmakameleon://other?state=s&code=c", "karmakameleon://oauth"))
    }

    @Test
    fun parse_differentPath_returnsNull() {
        assertNull(OAuthRedirect.parse("https://example.com/other?code=c", "https://example.com/callback"))
    }

    @Test
    fun parse_differentPort_returnsNull() {
        assertNull(OAuthRedirect.parse("http://127.0.0.1:8080/cb?code=c", "http://127.0.0.1:65010/cb"))
    }

    @Test
    fun parse_redirectUriWithoutScheme_returnsNull() {
        assertNull(OAuthRedirect.parse("http://localhost/oauth?code=c", "oauth"))
    }
}
