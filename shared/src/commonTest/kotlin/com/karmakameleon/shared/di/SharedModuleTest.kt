package com.karmakameleon.shared.di

import com.karmakameleon.shared.data.api.AuthManager
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

    // The platform's settings need a device, so in-memory ones stand in for them.
    private val app =
        koinApplication {
            allowOverride(true)
            modules(
                sharedModule,
                module {
                    single<Settings> { settings }
                    single<Settings>(TOKEN_SETTINGS) { tokenSettings }
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
}
