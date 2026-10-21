package com.karmakameleon.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.domain.model.Message
import com.karmakameleon.shared.domain.model.MessageType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageItemScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val navigation = NavigationRule()

    private fun capture(
        name: String,
        message: Message,
        isSelected: Boolean = true,
        isLoggedIn: Boolean = true,
        darkTheme: Boolean = false,
    ) {
        composeRule.setContent {
            // Dynamic colors come from the wallpaper, which would make the screenshots depend on the machine.
            KarmaKameleonTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MessageItem(
                        message = message,
                        isSelected = isSelected,
                        isLoggedIn = isLoggedIn,
                        actions = RecordedMessageActions().actions,
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun newPrivateMessage() = capture("message_private_new", testPrivateMessage(), isSelected = false)

    @Test
    fun readMentionDark() =
        capture(
            "message_mention_read_dark",
            inboxMessage(MessageType.USERNAME_MENTION, "username mention", "u/me might know").copy(isNew = false),
            isSelected = false,
            darkTheme = true,
        )

    @Test
    fun selectedCommentReply() = capture("message_comment_reply_selected", testCommentReply().copy(likes = true))

    @Test
    fun selectedLoggedOut() = capture("message_comment_reply_logged_out", testCommentReply(), isLoggedIn = false)
}
