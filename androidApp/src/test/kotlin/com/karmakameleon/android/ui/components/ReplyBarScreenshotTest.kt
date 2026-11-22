package com.karmakameleon.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
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

    private val photo = ImageFile("photo.png", "image/png", byteArrayOf(1))

    private fun capture(
        name: String,
        replyText: String,
        images: ReplyImageActions,
    ) {
        composeRule.setContent {
            // Dynamic colors come from the wallpaper, which would make the screenshots depend on the machine.
            KarmaKameleonTheme(dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ReplyBar(replyText = replyText, onReplyTextChange = {}, onSubmit = {}, onCancel = {}, images = images)
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

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
