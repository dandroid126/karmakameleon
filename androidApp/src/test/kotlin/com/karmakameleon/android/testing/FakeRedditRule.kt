package com.karmakameleon.android.testing

import com.karmakameleon.android.di.androidModule
import com.karmakameleon.android.ui.components.threeHoursAgo
import com.karmakameleon.shared.data.api.AuthManager
import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.data.api.dto.TokenInfo
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.di.sharedModule
import com.karmakameleon.shared.testing.MockReddit
import com.karmakameleon.shared.testing.readFixture
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.junit.rules.ExternalResource
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.time.Duration.Companion.days

/**
 * Starts Koin with the app's own modules, so screens get their real view models and repositories, with Reddit replaced
 * by [reddit] and the settings kept in memory. Tests set [reddit]'s responses before showing a screen.
 */
class FakeRedditRule : ExternalResource() {
    // Read when a screen first asks for the login, so set it before showing one.
    var loggedIn = false
    val reddit = MockReddit()
    val settings = SettingsRepository(MapSettings())

    override fun before() {
        startKoin {
            modules(
                sharedModule,
                androidModule,
                // Replaces sharedModule's definitions. Requests run on the main thread, which the Compose test rule
                // waits for, so a screen has its responses by the time the test looks at it.
                module {
                    single<Settings> { MapSettings() }
                    single { settings }
                    single<AuthManager> { authManager(loggedIn) }
                    single<RedditApi> { reddit.api(settings, Dispatchers.Main) }
                },
            )
        }
    }

    override fun after() {
        stopKoin()
    }
}

/**
 * A real [AuthManager] with a saved login or none. The screens learn the login state from it, while [MockReddit]'s API
 * gets its tokens from a fake. Nothing reaches the network: the token is valid for a day.
 */
private fun authManager(loggedIn: Boolean): AuthManager {
    val tokenSettings = MapSettings()
    if (loggedIn) {
        val expiresAt = System.currentTimeMillis() / 1000 + 1.days.inWholeSeconds
        // The key AuthManager saves the login under.
        tokenSettings.putString("token_info", Json.encodeToString(TokenInfo("access", "refresh", expiresAt, "*")))
    }
    return AuthManager(offlineClient(), MapSettings(), tokenSettings)
}

/** A saved Reddit listing with every post three hours old, so screenshots show the same ages whenever they run. */
fun postListing(fixture: String = "post_listing.json"): String =
    readFixture(fixture).replace(Regex(""""(created(?:_utc)?)": *[0-9.]+""")) { "\"${it.groupValues[1]}\": $threeHoursAgo" }
