package com.karmakameleon.android.ui.components

import android.widget.EditText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withHint
import com.github.takahirom.roborazzi.captureRoboImage
import com.karmakameleon.android.testing.clickWithoutRipple
import com.karmakameleon.android.testing.saveScreenshotWithDialog
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.data.media.ImageFile
import com.karmakameleon.shared.ui.comment.ReplyImage
import com.karmakameleon.shared.ui.comment.ReplyImageStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplyBarScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    // The preview's parser comes from Koin.
    @get:Rule
    val navigation = NavigationRule()

    private val photo = ImageFile("photo.png", "image/png", byteArrayOf(1))

    private fun show(
        replyText: String,
        images: ReplyImageActions?,
        darkTheme: Boolean = false,
    ) {
        composeRule.setContent {
            // Dynamic colors come from the wallpaper, which would make the screenshots depend on the machine.
            KarmaKameleonTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ReplyBar(replyText = replyText, onReplyTextChange = {}, onSubmit = {}, onCancel = {}, images = images)
                }
            }
        }
    }

    private fun capture(
        name: String,
        replyText: String,
        images: ReplyImageActions,
    ) {
        show(replyText, images)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun showFormatting(
        replyText: String,
        darkTheme: Boolean,
        images: ReplyImageActions? = null,
    ) {
        show(replyText, images, darkTheme)
        composeRule.onNodeWithContentDescription("Formatting").clickWithoutRipple()
        composeRule.waitForIdle()
    }

    private fun captureFormattingBar(
        name: String,
        darkTheme: Boolean,
    ) {
        showFormatting("Hello world", darkTheme)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun formattingBar() = captureFormattingBar("reply_bar_formatting", darkTheme = false)

    @Test
    fun formattingBarDark() = captureFormattingBar("reply_bar_formatting_dark", darkTheme = true)

    private fun captureLinkDialog(
        name: String,
        darkTheme: Boolean,
    ) {
        showFormatting("Read the docs", darkTheme)
        var field: EditText? = null
        onView(withHint("Write a reply...")).check { view, _ -> field = view as EditText }
        composeRule.runOnUiThread { checkNotNull(field).setSelection(9, 13) }
        composeRule.onNodeWithContentDescription("Link").clickWithoutRipple()
        composeRule.waitForIdle()
        saveScreenshotWithDialog(name)
    }

    @Test
    fun linkDialog() = captureLinkDialog("reply_bar_link_dialog", darkTheme = false)

    @Test
    fun linkDialogDark() = captureLinkDialog("reply_bar_link_dialog_dark", darkTheme = true)

    private fun capturePreview(
        name: String,
        darkTheme: Boolean,
    ) {
        showFormatting(
            "_Italic_, **bold**, ~~struck~~, a >!spoiler!< and [a link](https://kotlinlang.org).\n\n![image](img-1)",
            darkTheme,
            images = images(listOf(ReplyImage("img-1", ReplyImageStatus.Uploading, photo))),
        )
        composeRule.onNodeWithText("Preview").clickWithoutRipple()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun preview() = capturePreview("reply_bar_preview", darkTheme = false)

    @Test
    fun previewDark() = capturePreview("reply_bar_preview_dark", darkTheme = true)

    private fun images(
        images: List<ReplyImage> = emptyList(),
        canAddImages: Boolean = true,
    ) = ReplyImageActions(
        images = images,
        showsButton = true,
        canAddImages = canAddImages,
        explainDisabled = {},
        attachImage = { _, _, _ -> null },
        attachImageFromUrl = { null },
        retryImage = {},
        removeImage = {},
    )

    // Where the subreddit allows no uploads, the image button is grayed out.
    @Test
    fun imagesDisabled() = capture("reply_bar_images_disabled", replyText = "", images = images(canAddImages = false))

    @Test
    fun withImages() {
        capture(
            "reply_bar_with_images",
            replyText = "Before and after:\n\n![image](img-1) ![image](img-2) ![image](img-3) ![image](img-4)",
            images =
                images(
                    listOf(
                        ReplyImage("img-1", ReplyImageStatus.Uploaded("asset1"), photo),
                        ReplyImage("img-2", ReplyImageStatus.Uploading, photo),
                        ReplyImage("img-3", ReplyImageStatus.Failed("Couldn't upload the image"), photo),
                        ReplyImage("img-4", ReplyImageStatus.Failed("That file isn't an image")),
                        // Its token was deleted from the text, so it's dimmed.
                        ReplyImage("img-5", ReplyImageStatus.Uploaded("asset5"), photo),
                    ),
                ),
        )
    }
}
