package com.karmakameleon.shared.di

import com.karmakameleon.shared.data.api.AuthManager
import com.karmakameleon.shared.data.api.HttpClientFactory
import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.data.repository.CommentDraftRepository
import com.karmakameleon.shared.data.repository.CommentRepository
import com.karmakameleon.shared.data.repository.InboxPoller
import com.karmakameleon.shared.data.repository.MessageRepository
import com.karmakameleon.shared.data.repository.PostRepository
import com.karmakameleon.shared.data.repository.ReadPostsRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.data.repository.SubredditRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class SharedModuleTest {
    private val settings = MapSettings()
    private val tokenSettings = MapSettings()

    // The followRedirects of each client made, in order. A client that follows redirects never shows the share link's
    // redirect, so only one that doesn't gets it.
    private val clientsMade = mutableListOf<Boolean>()
    private val httpClientFactory =
        HttpClientFactory { followRedirects ->
            clientsMade += followRedirects
            HttpClient(
                MockEngine {
                    if (followRedirects) {
                        respondOk()
                    } else {
                        respond(
                            "",
                            HttpStatusCode.Found,
                            headersOf(HttpHeaders.Location, "https://www.reddit.com/r/Kotlin/comments/abc123/"),
                        )
                    }
                },
            ) { this.followRedirects = followRedirects }
        }

    // The platform's module needs a device, so in-memory settings and a fake engine stand in for it.
    private val app =
        koinApplication {
            allowOverride(true)
            modules(
                sharedModule,
                module {
                    single<Settings> { settings }
                    single<Settings>(TOKEN_SETTINGS) { tokenSettings }
                    single { httpClientFactory }
                },
            )
        }
    private val koin: Koin = app.koin

    @AfterTest
    fun tearDown() {
        app.close()
    }

    @Test
    fun everyDependencyResolves() {
        val types =
            listOf(
                HttpClient::class,
                AuthManager::class,
                SettingsRepository::class,
                RedditApi::class,
                PostRepository::class,
                SubredditRepository::class,
                UserRepository::class,
                CommentRepository::class,
                MessageRepository::class,
                InboxPoller::class,
                ReadPostsRepository::class,
                CommentDraftRepository::class,
            )

        types.forEach { type ->
            // Each is a single, so the app shares one of each.
            assertSame(koin.get<Any>(type), koin.get<Any>(type), type.simpleName)
        }
    }

    @Test
    fun authManagerKeepsTokensInTheTokenSettings() {
        // A token saved by an older version with the other settings is moved on startup.
        settings.putString("token_info", """{"accessToken":"a","refreshToken":"r","expiresAt":4102444800,"scope":"read"}""")

        val authManager = koin.get<AuthManager>()
        authManager.setClientId("my_client")

        assertEquals(true, authManager.isLoggedIn())
        assertFalse(settings.hasKey("token_info"))
        assertEquals(true, tokenSettings.hasKey("token_info"))
        assertEquals("my_client", settings.getStringOrNull("client_id"))
        assertFalse(tokenSettings.hasKey("client_id"))
    }

    @Test
    fun shareLinksUseAClientThatDoesNotFollowRedirects() =
        runTest {
            val api = koin.get<RedditApi>()

            assertEquals("Kotlin" to "abc123", api.resolveShareLink("kotlin", "AbCdEf"))
            // The main client follows redirects, and the share link client, made on first use, doesn't.
            assertEquals(listOf(true, false), clientsMade)
        }
}
