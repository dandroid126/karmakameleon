package com.karmakameleon.android.ui.subreddit

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.captureScreen
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
}
