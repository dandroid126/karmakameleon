package com.karmakameleon.android.ui.subreddit

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.captureScreen
import com.karmakameleon.android.testing.postListing
import com.karmakameleon.android.ui.components.FakeImagesRule
import com.karmakameleon.shared.testing.readFixture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubredditScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    @get:Rule
    val fakeReddit = FakeRedditRule().apply { loggedIn = true }

    @Test
    fun subreddit() {
        fakeReddit.reddit.respond("/r/Kotlin/about", readFixture("subreddit_about.json"))
        fakeReddit.reddit.respond("/r/Kotlin/hot", postListing())
        composeRule.captureScreen("subreddit_screen") {
            SubredditScreen(subredditName = "Kotlin", onBackClick = {}, onPostClick = { _, _ -> }, onUserClick = {})
        }
    }
}
