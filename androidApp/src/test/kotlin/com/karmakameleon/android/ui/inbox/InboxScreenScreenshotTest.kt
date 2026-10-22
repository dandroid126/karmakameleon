package com.karmakameleon.android.ui.inbox

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
class InboxScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeReddit = FakeRedditRule()

    @Test
    fun inbox() {
        fakeReddit.loggedIn = true
        fakeReddit.reddit.respond("/message/inbox", messageListing())
        composeRule.captureScreen("inbox_screen") { InboxScreen() }
    }

    @Test
    fun inboxDark() {
        fakeReddit.loggedIn = true
        fakeReddit.reddit.respond("/message/inbox", messageListing())
        composeRule.captureScreen("inbox_screen_dark", darkTheme = true) { InboxScreen() }
    }

    @Test
    fun loggedOut() {
        composeRule.captureScreen("inbox_screen_logged_out") { InboxScreen() }
    }
}
