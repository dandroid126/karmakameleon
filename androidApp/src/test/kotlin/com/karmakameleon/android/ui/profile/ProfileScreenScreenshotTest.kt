package com.karmakameleon.android.ui.profile

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.captureScreen
import com.karmakameleon.android.testing.saveScreenshot
import com.karmakameleon.android.testing.showScreen
import com.karmakameleon.android.ui.components.FakeImagesRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    @get:Rule
    val fakeReddit = FakeRedditRule()

    private fun showOwnProfile(darkTheme: Boolean = false) {
        fakeReddit.loggedIn = true
        respondWithProfile(fakeReddit, OWN_USERNAME)
        loadOwnAccount()
        composeRule.showScreen(darkTheme) { ProfileScreen(username = OWN_USERNAME, onPostClick = { _, _ -> }, onSubredditClick = {}) }
    }

    @Test
    fun ownProfile() {
        showOwnProfile()
        composeRule.saveScreenshot("profile_screen")
    }

    @Test
    fun ownProfileDark() {
        showOwnProfile(darkTheme = true)
        composeRule.saveScreenshot("profile_screen_dark")
    }

    @Test
    fun comments() {
        showOwnProfile()
        composeRule.onNodeWithText("Comments").performClick()
        composeRule.saveScreenshot("profile_screen_comments")
    }

    @Test
    fun about() {
        showOwnProfile()
        composeRule.onNodeWithText("About").performScrollTo().performClick()
        composeRule.saveScreenshot("profile_screen_about")
    }

    @Test
    fun signIn() {
        composeRule.captureScreen("profile_screen_sign_in") { ProfileScreen(onPostClick = { _, _ -> }, onSubredditClick = {}) }
    }
}
