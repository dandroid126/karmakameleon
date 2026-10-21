package com.karmakameleon.android.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import com.karmakameleon.shared.domain.model.Post
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

val noPostActions =
    PostActions(onClick = {}, onSubredditClick = {}, onUserClick = {}, onUpvote = {}, onDownvote = {}, onSave = {}, onHide = {})

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PostCardScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    private fun capture(
        name: String,
        darkTheme: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            // Dynamic colors come from the wallpaper, which would make the screenshots depend on the machine.
            KarmaKameleonTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(vertical = 4.dp)) {
                    content()
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun capturePostCard(
        name: String,
        post: Post,
        darkTheme: Boolean = false,
        isLoggedIn: Boolean = true,
        isRead: Boolean = false,
        nsfwPreviewMode: NsfwPreviewMode = NsfwPreviewMode.DO_NOT_PREFETCH,
    ) = capture(name, darkTheme) {
        PostCard(post = post, actions = noPostActions, isLoggedIn = isLoggedIn, isRead = isRead, nsfwPreviewMode = nsfwPreviewMode)
    }

    @Test
    fun textPost() = capturePostCard("post_card_text", testPost().copy(linkFlairText = "Discussion", linkFlairBackgroundColor = "#0079d3"))

    @Test
    fun textPostDark() =
        capturePostCard(
            "post_card_text_dark",
            testPost().copy(linkFlairText = "Discussion", linkFlairBackgroundColor = "#0079d3"),
            darkTheme = true,
        )

    @Test
    fun upvotedAndSaved() = capturePostCard("post_card_upvoted_saved", testPost().copy(likes = true, isSaved = true, isStickied = true))

    @Test
    fun downvoted() = capturePostCard("post_card_downvoted", testPost().copy(likes = false))

    @Test
    fun readAndLoggedOut() = capturePostCard("post_card_read_logged_out", testPost(), isLoggedIn = false, isRead = true)

    @Test
    fun locked() = capturePostCard("post_card_locked", testPost().copy(isLocked = true))

    @Test
    fun imagePost() = capturePostCard("post_card_image", testImagePost())

    @Test
    fun galleryPost() = capturePostCard("post_card_gallery", testGalleryPost())

    @Test
    fun linkCrosspost() = capturePostCard("post_card_link_crosspost", testLinkPost().copy(isCrosspost = true))

    @Test
    fun nsfwHidden() = capturePostCard("post_card_nsfw_hidden", testImagePost().copy(isNsfw = true))

    @Test
    fun nsfwBlurred() =
        capturePostCard("post_card_nsfw_blurred", testImagePost().copy(isNsfw = true), nsfwPreviewMode = NsfwPreviewMode.PREFETCH_AND_BLUR)

    @Test
    fun nsfwShown() =
        capturePostCard("post_card_nsfw_shown", testImagePost().copy(isNsfw = true), nsfwPreviewMode = NsfwPreviewMode.SHOW_PREVIEWS)

    @Test
    fun spoiler() = capturePostCard("post_card_spoiler", testImagePost().copy(isSpoiler = true))
}
