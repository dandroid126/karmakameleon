package com.karmakameleon.android.ui.post

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.captureScreen
import com.karmakameleon.android.testing.postListing
import com.karmakameleon.android.testing.saveScreenshot
import com.karmakameleon.android.testing.showScreen
import com.karmakameleon.android.ui.components.FakeImagesRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PostDetailScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    @get:Rule
    val fakeReddit = FakeRedditRule().apply { loggedIn = true }

    @Before
    fun respond() {
        fakeReddit.reddit.respond(POST_PATH, postListing("post_with_comments.json"))
    }

    @Test
    fun postDetail() {
        composeRule.captureScreen("post_detail_screen") { PostDetailScreen(subreddit = "nba", postId = "1xawzaz", onBackClick = {}) }
    }

    @Test
    fun postDetailDark() {
        composeRule.captureScreen("post_detail_screen_dark", darkTheme = true) {
            PostDetailScreen(subreddit = "nba", postId = "1xawzaz", onBackClick = {})
        }
    }

    @Test
    fun replying() {
        composeRule.showScreen { PostDetailScreen(subreddit = "nba", postId = "1xawzaz", onBackClick = {}) }
        composeRule.onNodeWithContentDescription("Reply").performClick()
        composeRule.saveScreenshot("post_detail_screen_replying")
    }
}
