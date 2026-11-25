package com.karmakameleon.android.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import com.karmakameleon.android.data.ContentResolverImageFiles
import com.karmakameleon.android.di.androidModule
import com.karmakameleon.android.ui.components.threeHoursAgo
import com.karmakameleon.shared.data.api.AuthManager
import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.data.api.dto.TokenInfo
import com.karmakameleon.shared.data.backup.BackupFiles
import com.karmakameleon.shared.data.media.ImageFiles
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.di.sharedModule
import com.karmakameleon.shared.testing.FakeBackupFiles
import com.karmakameleon.shared.testing.MockReddit
import com.karmakameleon.shared.testing.readFixture
import com.karmakameleon.shared.util.markdown.MarkdownParser
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.rules.ExternalResource
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RuntimeEnvironment
import kotlin.time.Duration.Companion.days

/**
 * Starts Koin with the app's own modules, so screens get their real view models and repositories, with Reddit replaced
 * by [reddit], the settings kept in memory and the export files in [backupFiles]. Tests set [reddit]'s responses
 * before showing a screen.
 */
class FakeRedditRule : ExternalResource() {
    // Read when a screen first asks for the login, so set it before showing one.
    var loggedIn = false
    val reddit = MockReddit()
    val settings = SettingsRepository(MapSettings())
    val backupFiles = FakeBackupFiles()

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
                    single<BackupFiles> { backupFiles }
                    single<AuthManager> { authManager(loggedIn) }
                    single<RedditApi> { reddit.api(settings, Dispatchers.Main) }
                    single<HttpClient> { reddit.httpClient(Dispatchers.Main) }
                    single<ImageFiles> { ContentResolverImageFiles(RuntimeEnvironment.getApplication().contentResolver, Dispatchers.Main) }
                    single { MarkdownParser(Dispatchers.Main) }
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
 * gets its tokens from a fake. Nothing reaches the network: the token is valid for a day, and signing in gets one from
 * [tokenClient].
 */
private fun authManager(loggedIn: Boolean): AuthManager {
    val tokenSettings = MapSettings()
    if (loggedIn) {
        val expiresAt = System.currentTimeMillis() / 1000 + 1.days.inWholeSeconds
        // The key AuthManager saves the login under.
        tokenSettings.putString("token_info", Json.encodeToString(TokenInfo("access", "refresh", expiresAt, "*")))
    }
    return AuthManager(tokenClient(), MapSettings(), tokenSettings)
}

/** A saved Reddit listing with every post three hours old, so screenshots show the same ages whenever they run. */
fun postListing(fixture: String = "post_listing.json"): String =
    readFixture(fixture).replace(Regex(""""(created(?:_utc)?)": *[0-9.]+""")) { "\"${it.groupValues[1]}\": $threeHoursAgo" }

/** [listing] without a next page. The fake server answers every page of a path alike, so the next page would repeat it. */
fun lastPage(listing: String): String = listing.replace(Regex(""""after": *"[^"]*""""), "\"after\": null")

/**
 * [listing] with its items in the reverse order. Answering a new search, sort or refresh with it gives new contents
 * that share every item with the old ones, so a list that kept its place would land partway down them (#127).
 */
fun reversedListing(listing: String): String {
    val json = Json.parseToJsonElement(listing).jsonObject
    val data = json.getValue("data").jsonObject
    val children = JsonArray(data.getValue("children").jsonArray.reversed())
    return JsonObject(json + ("data" to JsonObject(data + ("children" to children)))).toString()
}

/**
 * Scrolls the list on screen halfway into the item first on screen, which spans [itemTop] to [itemBottom], like
 * scrolling down to its actions at its bottom.
 */
fun ComposeContentTestRule.scrollIntoItem(
    itemTop: Dp,
    itemBottom: Dp,
) {
    val list = onNode(hasScrollToIndexAction())
    val distance = with(density) { ((itemBottom - itemTop) / 2 + itemTop - list.getUnclippedBoundsInRoot().top).toPx() }
    list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, distance) }
    waitForIdle()
}

/** Checks that the node's top is on screen, not above the list, where a removed item used to leave the next one (#179). */
fun ComposeContentTestRule.assertTopIsInList(node: SemanticsNodeInteraction) {
    // Clipped bounds stop at the list's edge, so they'd put a node partly above the list at its top.
    val listTop = onNode(hasScrollToIndexAction()).getUnclippedBoundsInRoot().top
    val top = node.assertIsDisplayed().getUnclippedBoundsInRoot().top
    check(top >= listTop) { "The node's top, $top, is above the list's, $listTop" }
}
