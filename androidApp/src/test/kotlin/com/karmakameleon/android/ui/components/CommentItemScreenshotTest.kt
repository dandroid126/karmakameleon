package com.karmakameleon.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.karmakameleon.android.testing.FakeAuthManager
import com.karmakameleon.android.testing.FakeRedditApi
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.data.repository.CommentRepository
import com.karmakameleon.shared.domain.model.Comment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CommentItemScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val navigation = NavigationRule()

    private fun capture(
        name: String,
        comment: Comment,
        isSelected: Boolean = false,
        isHidden: Boolean = false,
        isLoggedIn: Boolean = true,
        darkTheme: Boolean = false,
        showSubreddit: Boolean = false,
    ) {
        composeRule.setContent {
            // Dynamic colors come from the wallpaper, which would make the screenshots depend on the machine.
            KarmaKameleonTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    CommentItem(
                        comment = comment,
                        isSelected = isSelected,
                        isHidden = isHidden,
                        actions = RecordedCommentActions().actions,
                        isLoggedIn = isLoggedIn,
                        loggedInUsername = "me",
                        showSubreddit = showSubreddit,
                        commentRepository = CommentRepository(FakeRedditApi(FakeAuthManager())),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun topLevel() =
        capture("comment_top_level", testComment().copy(authorFlairText = "Kotlin dev", authorFlairBackgroundColor = "#7f52ff"))

    @Test
    fun topLevelDark() = capture("comment_top_level_dark", testComment().copy(likes = true), darkTheme = true)

    @Test
    fun selected() = capture("comment_selected", testComment().copy(likes = false), isSelected = true)

    @Test
    fun selectedReply() = capture("comment_selected_reply", testComment().copy(depth = 2, parentId = "t1_parent"), isSelected = true)

    @Test
    fun selectedOwnComment() = capture("comment_selected_own", testComment().copy(author = "me", edited = 1L), isSelected = true)

    @Test
    fun selectedLoggedOut() = capture("comment_selected_logged_out", testComment(), isSelected = true, isLoggedIn = false)

    @Test
    fun submitter() = capture("comment_submitter", testComment().copy(isSubmitter = true, depth = 1))

    @Test
    fun moderator() = capture("comment_moderator", testComment().copy(distinguished = "moderator", scoreHidden = true))

    @Test
    fun admin() = capture("comment_admin", testComment().copy(distinguished = "admin"))

    @Test
    fun hidden() = capture("comment_hidden", testComment(), isHidden = true)

    @Test
    fun withSubreddit() = capture("comment_with_subreddit", testComment(), showSubreddit = true)
}
