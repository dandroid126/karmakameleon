package com.karmakameleon.android.ui.subreddit

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTextInput
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.captureScreen
import com.karmakameleon.android.testing.saveScreenshot
import com.karmakameleon.android.testing.showScreen
import com.karmakameleon.shared.testing.listingOf
import com.karmakameleon.shared.testing.subredditThing
import io.ktor.http.HttpStatusCode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubredditListScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeReddit = FakeRedditRule()

    @Test
    fun subscriptions() {
        fakeReddit.loggedIn = true
        fakeReddit.reddit.respond("/subreddits/mine/subscriber", subscriptions)
        // Favorites come first.
        fakeReddit.settings.toggleFavoriteSubreddit("pics")
        composeRule.captureScreen("subreddit_list_screen") { SubredditListScreen(onSubredditClick = {}) }
    }

    @Test
    fun loggedOut() {
        composeRule.captureScreen("subreddit_list_screen_logged_out") { SubredditListScreen(onSubredditClick = {}) }
    }

    private fun showSearch(query: String) {
        fakeReddit.loggedIn = true
        fakeReddit.reddit.respond("/subreddits/mine/subscriber", subscriptions)
        composeRule.showScreen { SubredditListScreen(onSubredditClick = {}) }
        composeRule.onNode(hasSetTextAction()).performTextInput(query)
        composeRule.waitForIdle()
    }

    // The matching subscriptions show above all that Reddit found.
    @Test
    fun searchResults() {
        fakeReddit.reddit.respond(
            "/subreddits/search",
            listingOf(
                subredditThing("Kotlin", nsfw = false, subscribers = 108_935),
                subredditThing("KotlinAndroid", nsfw = false, subscribers = 7_000),
            ),
        )
        showSearch("kot")
        composeRule.saveScreenshot("subreddit_list_screen_search_results")
    }

    @Test
    fun searchFailed() {
        fakeReddit.reddit.respond("/subreddits/search", "", HttpStatusCode.TooManyRequests)
        showSearch("kot")
        composeRule.saveScreenshot("subreddit_list_screen_search_failed")
    }
}
