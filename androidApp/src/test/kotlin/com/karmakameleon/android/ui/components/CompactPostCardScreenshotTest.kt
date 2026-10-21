package com.karmakameleon.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import com.karmakameleon.shared.domain.model.Post
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactPostCardScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    private fun capture(
        name: String,
        post: Post,
        isExpanded: Boolean = false,
        darkTheme: Boolean = false,
        isLoggedIn: Boolean = true,
        isRead: Boolean = false,
        nsfwPreviewMode: NsfwPreviewMode = NsfwPreviewMode.DO_NOT_PREFETCH,
    ) {
        composeRule.setContent {
            // Dynamic colors come from the wallpaper, which would make the screenshots depend on the machine.
            KarmaKameleonTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    CompactPostCard(
                        post = post,
                        isExpanded = isExpanded,
                        isHighlighted = isExpanded,
                        onToggleExpansion = {},
                        actions = noPostActions,
                        isLoggedIn = isLoggedIn,
                        isRead = isRead,
                        nsfwPreviewMode = nsfwPreviewMode,
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun textPost() = capture("compact_post_text", testPost().copy(linkFlairText = "Discussion", linkFlairBackgroundColor = "#0079d3"))

    @Test
    fun expanded() =
        capture("compact_post_expanded", testLinkPost().copy(isCrosspost = true, likes = true, isSaved = true), isExpanded = true)

    @Test
    fun expandedDark() = capture("compact_post_expanded_dark", testLinkPost().copy(likes = false), isExpanded = true, darkTheme = true)

    @Test
    fun expandedLoggedOut() = capture("compact_post_expanded_logged_out", testPost(), isExpanded = true, isLoggedIn = false)

    @Test
    fun readAndLocked() = capture("compact_post_read_locked", testPost().copy(isLocked = true), isRead = true)

    @Test
    fun linkPost() = capture("compact_post_link", testLinkPost())

    @Test
    fun imagePost() = capture("compact_post_image", testImagePost())

    @Test
    fun galleryPost() = capture("compact_post_gallery", testGalleryPost())

    @Test
    fun videoPost() =
        capture("compact_post_video", testImagePost().copy(title = "A video", url = "https://v.redd.it/abc", postHint = "hosted:video"))

    @Test
    fun nsfwHidden() = capture("compact_post_nsfw_hidden", testImagePost().copy(isNsfw = true))

    @Test
    fun nsfwBlurred() =
        capture("compact_post_nsfw_blurred", testImagePost().copy(isNsfw = true), nsfwPreviewMode = NsfwPreviewMode.PREFETCH_AND_BLUR)

    @Test
    fun spoiler() = capture("compact_post_spoiler", testImagePost().copy(isSpoiler = true))
}
