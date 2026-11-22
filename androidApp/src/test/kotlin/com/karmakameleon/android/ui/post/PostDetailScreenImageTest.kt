package com.karmakameleon.android.ui.post

import android.content.ClipData
import android.net.Uri
import android.os.Looper
import android.view.View
import android.widget.EditText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withHint
import com.karmakameleon.android.data.FakeImageProvider
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.postListing
import com.karmakameleon.android.ui.components.FakeImagesRule
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.testing.MockReddit
import com.karmakameleon.shared.testing.form
import com.karmakameleon.shared.testing.readFixture
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

/** Adding images to a reply on the post screen: from the keyboard, from a link, and where the subreddit refuses them. */
@RunWith(RobolectricTestRunner::class)
class PostDetailScreenImageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    @get:Rule
    val fakeReddit = FakeRedditRule().apply { loggedIn = true }

    private val provider = Robolectric.setupContentProvider(FakeImageProvider::class.java, AUTHORITY)

    private val reddit get() = fakeReddit.reddit

    // Shows the post, in a subreddit whose comments allow these media, and starts a reply to it.
    private fun startReply(allowedMedia: String = """"static", "animated"""") {
        reddit.respond(POST_PATH, postListing("post_with_comments.json"))
        reddit.respond(
            "/r/nba/about",
            """
            {"kind": "t5", "data": {"id": "2qo4s", "name": "t5_2qo4s", "display_name": "nba", "display_name_prefixed": "r/nba",
             "title": "NBA", "created": 1.0, "created_utc": 1.0, "url": "/r/nba/", "allowed_media_in_comments": [$allowedMedia]}}
            """.trimIndent(),
        )
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                PostDetailScreen(subreddit = "nba", postId = "1xawzaz", onBackClick = {})
            }
        }
        composeRule.onNodeWithContentDescription("Reply").performClick()
        composeRule.waitForIdle()
    }

    private fun replyField(): EditText {
        var field: EditText? = null
        onView(withHint("Write a reply...")).perform(viewAction { field = it as EditText })
        return field!!
    }

    // Sends an image the way a keyboard does, with the link keyboards give alongside their GIFs.
    private fun sendFromKeyboard(
        name: String,
        linkUrl: String? = null,
    ) {
        val uri = Uri.parse("content://$AUTHORITY/$name")
        val clip = ClipData.newUri(RuntimeEnvironment.getApplication().contentResolver, name, uri)
        val payload =
            ContentInfoCompat
                .Builder(clip, ContentInfoCompat.SOURCE_INPUT_METHOD)
                .apply { linkUrl?.let { setLinkUri(Uri.parse(it)) } }
                .build()
        onView(withHint("Write a reply...")).perform(viewAction { ViewCompat.performReceiveContent(it, payload) })
        composeRule.waitForIdle()
    }

    private fun settle() =
        repeat(FRAMES_TO_SETTLE) {
            composeRule.mainClock.advanceTimeByFrame()
            shadowOf(Looper.getMainLooper()).idle()
        }

    private fun requests(path: String) = reddit.requests.filter { it.url.encodedPath == path }

    @Test
    fun addImageButtonIsDisabledWhereTheSubredditAllowsNoUploads() {
        startReply(allowedMedia = """"giphy", "video"""")

        composeRule.onNodeWithContentDescription("Add image, disabled in this subreddit").performClick()
        composeRule.waitForIdle()

        assertEquals("Inline images are disabled in r/nba", ShadowToast.getTextOfLatestToast())
        composeRule.onNodeWithText("Choose from device").assertDoesNotExist()
    }

    @Test
    fun aKeyboardImageIsUploadedAndSentWithTheReply() {
        provider.file("cat.png").writeBytes(byteArrayOf(1, 2, 3))
        reddit.respondToImageUpload("asset1")
        reddit.respond("/api/convert_rte_body_format", """{"output": {"document": [{"e": "img", "id": "asset1"}]}}""")
        reddit.respond("/api/comment", readFixture("user_comments.json").let(::firstCommentData))
        startReply()
        composeRule.onNodeWithContentDescription("Add image").assertExists()

        sendFromKeyboard("cat.png")

        assertEquals("![image](img-1)", replyField().text.toString())
        composeRule.onNodeWithContentDescription("Image 1").assertExists()
        assertEquals("cat.png", requests("/api/media/asset.json").single().form()["filepath"])

        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitForIdle()

        assertEquals("![img](asset1)", requests("/api/convert_rte_body_format").single().form()["markdown_text"])
        assertTrue(requests("/api/comment").single().form()["richtext_json"]!!.contains("asset1"))
    }

    @Test
    fun aKeyboardGifTheSubredditRefusesGoesInAsItsLink() {
        provider.file("dance.gif").writeBytes(byteArrayOf(1))
        startReply(allowedMedia = """"static"""")

        sendFromKeyboard("dance.gif", linkUrl = "https://tenor.com/view/dance")

        assertEquals("[GIF](https://tenor.com/view/dance)", replyField().text.toString())
        assertEquals("r/nba doesn't allow GIFs in comments, so it was added as a link", ShadowToast.getTextOfLatestToast())
        assertTrue(requests("/api/media/asset.json").isEmpty())
    }

    @Test
    fun removingAnImageTakesOutItsToken() {
        provider.file("cat.png").writeBytes(byteArrayOf(1))
        reddit.respondToImageUpload("asset1")
        startReply()
        sendFromKeyboard("cat.png")

        composeRule.onNodeWithContentDescription("Remove image 1").performClick()
        composeRule.waitForIdle()

        assertEquals("", replyField().text.toString())
        composeRule.onNodeWithContentDescription("Image 1").assertDoesNotExist()
    }

    @Test
    fun anImageFromALinkIsDownloadedAndUploaded() {
        reddit.respond("/pics/cat.png", "PNGDATA", contentType = "image/png")
        reddit.respondToImageUpload("asset1")
        startReply()

        // With this dialog open, Robolectric's Compose never goes idle, so from here the test moves the clock itself.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription("Add image").performClick()
        settle()
        composeRule.onNodeWithText("Image from link").performClick()
        settle()
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement("https://example.com/pics/cat.png")
        settle()
        composeRule.onNodeWithText("Add").performClick()
        settle()

        assertEquals("![image](img-1)", replyField().text.toString())
        assertEquals("cat.png", requests("/api/media/asset.json").single().form()["filepath"])
    }

    @Test
    fun aFailedUploadCanBeRetried() {
        provider.file("cat.png").writeBytes(byteArrayOf(1))
        reddit.respondToImageUpload("asset1")
        reddit.respond(MockReddit.UPLOAD_PATH, "", HttpStatusCode.InternalServerError)
        startReply()
        sendFromKeyboard("cat.png")
        assertEquals("Couldn't upload the image (HTTP 500)", ShadowToast.getTextOfLatestToast())

        reddit.respondToImageUpload("asset1")
        composeRule.onNodeWithContentDescription("Retry upload").performClick()
        composeRule.waitForIdle()

        assertEquals(2, requests(MockReddit.UPLOAD_PATH).size)
        composeRule.onNodeWithContentDescription("Image 1").assertExists()
    }

    private companion object {
        const val AUTHORITY = "com.karmakameleon.test.keyboard"
        const val FRAMES_TO_SETTLE = 10
    }
}

// The first comment in a listing fixture, as the bare object Reddit answers a richtext comment with.
private fun firstCommentData(listing: String): String =
    Json
        .parseToJsonElement(listing)
        .jsonObject["data"]!!
        .jsonObject["children"]!!
        .jsonArray[0]
        .jsonObject["data"]
        .toString()

private fun viewAction(action: (View) -> Unit) =
    object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)

        override fun getDescription() = "act on the view"

        override fun perform(
            uiController: UiController,
            view: View,
        ) = action(view)
    }
