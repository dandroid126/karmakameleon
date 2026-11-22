package com.karmakameleon.android.ui.search

import android.os.Looper
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTextInput
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.captureScreen
import com.karmakameleon.android.testing.postListing
import com.karmakameleon.android.testing.saveScreenshot
import com.karmakameleon.android.testing.showScreen
import com.karmakameleon.android.ui.components.FakeImagesRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    @get:Rule
    val fakeReddit = FakeRedditRule()

    // Shows the screen, types the query, waits for the search to run and saves a screenshot.
    private fun captureSearch(
        name: String,
        query: String,
    ) {
        composeRule.showScreen { SearchScreen(onPostClick = { _, _ -> }, onSubredditClick = {}, onUserClick = {}) }
        composeRule.onNode(hasSetTextAction()).performTextInput(query)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        composeRule.saveScreenshot(name)
    }

    @Test
    fun prompt() {
        composeRule.captureScreen("search_screen") { SearchScreen(onPostClick = { _, _ -> }, onSubredditClick = {}, onUserClick = {}) }
    }

    @Test
    fun results() {
        fakeReddit.reddit.respond("/search", postListing())
        captureSearch("search_screen_results", "halloween")
    }

    @Test
    fun redditLink() {
        captureSearch("search_screen_link", "https://www.reddit.com/r/Kotlin/")
    }

    // A link too long for the card loses its middle, so the end, which says the most, still shows (#87).
    @Test
    fun longRedditLink() {
        captureSearch(
            "search_screen_long_link",
            "https://www.reddit.com/r/Kotlin/comments/1abc23/kotlin_2_4_is_out_with_a_new_compiler/",
        )
    }
}
