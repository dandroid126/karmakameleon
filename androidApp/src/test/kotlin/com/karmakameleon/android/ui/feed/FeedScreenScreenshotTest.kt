package com.karmakameleon.android.ui.feed

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.captureScreen
import com.karmakameleon.android.testing.postListing
import com.karmakameleon.android.ui.components.FakeImagesRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    @get:Rule
    val fakeReddit = FakeRedditRule().apply { loggedIn = true }

    private fun captureFeed(
        name: String,
        darkTheme: Boolean = false,
    ) = composeRule.captureScreen(name, darkTheme) {
        FeedScreen(onPostClick = { _, _ -> }, onSubredditClick = {}, onUserClick = {})
    }

    @Test
    fun frontPage() {
        fakeReddit.reddit.respond("/hot", postListing())
        captureFeed("feed_screen")
    }

    @Test
    fun frontPageDark() {
        fakeReddit.reddit.respond("/hot", postListing())
        captureFeed("feed_screen_dark", darkTheme = true)
    }

    // No screenshot of the error yet: it shows a JSON parser error instead of a message, laid out edge to edge
    // (#87). FeedScreenTest checks that Retry works. Add the screenshot with the fix.
}
