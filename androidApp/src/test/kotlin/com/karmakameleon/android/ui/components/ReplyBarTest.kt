package com.karmakameleon.android.ui.components

import android.os.Looper
import android.widget.EditText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.withHint
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.data.media.ImageFile
import com.karmakameleon.shared.ui.comment.ReplyImage
import com.karmakameleon.shared.ui.comment.ReplyImageStatus
import com.karmakameleon.shared.ui.comment.previewImageUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ReplyBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val navigation = NavigationRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    private var replyText by mutableStateOf("")
    private val sent = mutableListOf<String>()

    private fun show(
        text: String = "",
        images: ReplyImageActions? = null,
    ) {
        replyText = text
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                ReplyBar(
                    replyText = replyText,
                    onReplyTextChange = { replyText = it },
                    onSubmit = { sent += replyText },
                    onCancel = {},
                    images = images,
                )
            }
        }
    }

    // The reply field is a platform EditText, which Compose's test APIs can't reach.
    private fun field(): EditText {
        var field: EditText? = null
        onView(withHint("Write a reply...")).check { view, _ -> field = view as EditText }
        return checkNotNull(field)
    }

    private fun select(
        start: Int,
        end: Int = start,
    ) {
        val field = field()
        composeRule.runOnUiThread { field.setSelection(start, end) }
    }

    private fun tap(description: String) {
        composeRule.onNodeWithContentDescription(description).performClick()
        composeRule.waitForIdle()
    }

    // With a dialog open, Robolectric's Compose never goes idle, so the link tests move the clock themselves.
    private fun openLinkDialog() {
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription("Link").performClick()
        settle()
    }

    // The dialog's Display text field, then its URL field.
    private fun linkField(index: Int) = composeRule.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))[index]

    private fun settle() =
        repeat(FRAMES_TO_SETTLE) {
            composeRule.mainClock.advanceTimeByFrame()
            shadowOf(Looper.getMainLooper()).idle()
        }

    private fun assertSelection(
        start: Int,
        end: Int = start,
    ) {
        val field = field()
        assertEquals(start to end, field.selectionStart to field.selectionEnd)
    }

    @Test
    fun formattingBar_startsHiddenAndToggles() {
        show()
        composeRule.onNodeWithContentDescription("Italic").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Formatting").assertIsOff()

        tap("Formatting")
        composeRule.onNodeWithContentDescription("Formatting").assertIsOn()
        for (button in listOf("Italic", "Bold", "Strikethrough", "Spoiler", "Link")) {
            composeRule.onNodeWithContentDescription(button).assertIsEnabled()
        }

        tap("Formatting")
        composeRule.onNodeWithContentDescription("Italic").assertDoesNotExist()
    }

    @Test
    fun formattingButton_insertsAPairAtTheCursorAndKeepsTheFocus() {
        show("Hello ")
        select(6)

        tap("Formatting")
        tap("Italic")

        assertEquals("Hello __", replyText)
        assertSelection(7)
        assertTrue(field().hasFocus())
    }

    @Test
    fun formattingButton_wrapsTheSelectionAndUnwrapsItAgain() {
        show("Hello world")
        select(6, 11)
        tap("Formatting")

        tap("Bold")
        assertEquals("Hello **world**", replyText)
        assertSelection(8, 13)

        tap("Spoiler")
        assertEquals("Hello **>!world!<**", replyText)

        tap("Spoiler")
        tap("Bold")
        assertEquals("Hello world", replyText)
        assertSelection(6, 11)
    }

    @Test
    fun linkDialog_startsWithTheSelectionAndInsertsTheLink() {
        show("Read the docs now")
        select(9, 13)
        tap("Formatting")

        openLinkDialog()
        linkField(0).assertTextEquals("docs")
        linkField(1).assertIsFocused().performTextInput("https://e.com/a (b)")
        composeRule.onNodeWithText("Insert").performClick()
        settle()

        val link = "[docs](https://e.com/a%20%28b%29)"
        assertEquals("Read the $link now", replyText)
        assertSelection(9 + link.length)
    }

    @Test
    fun linkDialog_insertsTheLabelsOfEmptyFields() {
        show("See ")
        select(4)
        tap("Formatting")

        openLinkDialog()
        linkField(0).assertIsFocused()
        composeRule.onNodeWithText("Insert").assertIsEnabled().performClick()
        settle()

        assertEquals("See [display text](url)", replyText)
    }

    @Test
    fun linkDialog_cancelLeavesTheText() {
        show("See ")
        tap("Formatting")

        openLinkDialog()
        composeRule.onNodeWithText("Cancel").performClick()
        settle()

        composeRule.onNodeWithText("Insert link").assertDoesNotExist()
        assertEquals("See ", replyText)
    }

    @Test
    fun preview_showsTheRenderedReplyInPlaceOfTheField() {
        show("Some **bold** and [a link](https://kotlinlang.org)")
        select(4)
        tap("Formatting")

        composeRule.onNodeWithText("Preview").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Preview").assertIsSelected()
        composeRule.onNodeWithText("Some bold and a link").assertExists()
        onView(withHint("Write a reply...")).check(doesNotExist())
        composeRule.onNodeWithContentDescription("Italic").assertIsNotEnabled()

        // Links don't open, so a mistap doesn't leave the reply.
        composeRule
            .onNodeWithText("Some bold and a link")
            .performFirstLinkClick({ link: AnnotatedString.Range<LinkAnnotation> -> true })
        assertEquals(emptyList<String>(), navigation.calls)

        composeRule.onNodeWithText("Preview").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Preview").assertIsNotSelected()
        composeRule.onNodeWithText("Some bold and a link").assertDoesNotExist()
        assertSelection(4)
    }

    @Test
    fun preview_ofNothing_saysSo() {
        show()
        tap("Formatting")

        composeRule.onNodeWithText("Preview").performClick()

        composeRule.onNodeWithText("Nothing to preview").assertExists()
    }

    @Test
    fun preview_canBeSent() {
        show("Done")
        tap("Formatting")
        composeRule.onNodeWithText("Preview").performClick()

        tap("Send")

        assertEquals(listOf("Done"), sent)
    }

    @Test
    fun hidingTheFormattingBar_leavesThePreview() {
        show("Some text")
        tap("Formatting")
        composeRule.onNodeWithText("Preview").performClick()
        composeRule.waitForIdle()

        tap("Formatting")

        composeRule.onNodeWithText("Some text").assertDoesNotExist()
        assertEquals("Some text", field().text.toString())

        // It starts without the preview when it's shown again.
        tap("Formatting")
        composeRule.onNodeWithText("Preview").assertIsNotSelected()
    }

    private val photo = ImageFile("photo.png", "image/png", byteArrayOf(1, 2, 3))

    private fun images() =
        ReplyImageActions(
            images = listOf(ReplyImage("img-1", ReplyImageStatus.Uploading, photo)),
            showsButton = true,
            canAddImages = true,
            explainDisabled = {},
            attachImage = { _, _, _ -> null },
            attachImageFromUrl = { null },
            retryImage = {},
            removeImage = {},
        )

    @Test
    fun preview_showsImagesFromTheDevice() {
        show("Look:\n\n![image](img-1)", images = images())
        tap("Formatting")
        fakeImages.requested.clear()

        composeRule.onNodeWithText("Preview").performClick()
        composeRule.waitForIdle()

        // The thumbnail in the tray asks for the same bytes, so the preview's request is told apart by its count.
        assertEquals(1, fakeImages.requested.count { it === photo.bytes })
        assertTrue(fakeImages.requested.none { it == previewImageUrl("img-1") })
    }

    @Test
    fun addingAnImage_leavesThePreview() {
        show("Some text", images = images())
        tap("Formatting")
        composeRule.onNodeWithText("Preview").performClick()
        composeRule.waitForIdle()

        tap("Add image")
        // Closes the menu of where the image comes from.
        pressBack()

        composeRule.onNodeWithText("Preview").assertIsNotSelected()
        assertEquals("Some text", field().text.toString())
    }
}

private const val FRAMES_TO_SETTLE = 10
