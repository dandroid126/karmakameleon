package com.karmakameleon.android.ui.components

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MarkdownTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val navigation = NavigationRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    private val calls = mutableListOf<String>()

    private fun show(
        markdown: String,
        renderInlineImages: Boolean = true,
    ) {
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                MarkdownText(
                    markdown = markdown,
                    onTextClick = { calls += "text" },
                    renderInlineImages = renderInlineImages,
                    onImageClick = { calls += "image $it" },
                )
            }
        }
    }

    // Picks a link by part of its URL, which the link's tag holds.
    private fun linkTo(urlPart: String) =
        { link: AnnotatedString.Range<LinkAnnotation> -> (link.item as LinkAnnotation.Clickable).tag.contains(urlPart) }

    @Test
    fun tappingLinkOpensIt() {
        show("Ask in [r/Kotlin](https://www.reddit.com/r/Kotlin) or [the docs](https://kotlinlang.org/docs).")

        composeRule.onNodeWithText("Ask in", substring = true).performFirstLinkClick(linkTo("reddit.com"))
        composeRule.onNodeWithText("Ask in", substring = true).performFirstLinkClick(linkTo("kotlinlang"))

        assertEquals(listOf("subreddit Kotlin", "external https://kotlinlang.org/docs"), navigation.calls)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun linkInQuoteOpensIt() {
        show("> See [this](https://www.reddit.com/u/kotlinfan)")
        composeRule.onNodeWithText("See", substring = true).performFirstLinkClick()
        assertEquals(listOf("user kotlinfan"), navigation.calls)
    }

    @Test
    fun tappingTextOutsideLinks() {
        show("Just text, with a **bold** word.\n\n| A | B |\n|---|---|\n| cell | cell |")
        composeRule.onNodeWithText("Just text", substring = true).performClick()
        composeRule.onNodeWithText("A").performClick()
        assertEquals(listOf("text", "text"), calls)
    }

    @Test
    fun tappingInlineImageOpensIt() {
        show("![img](https://i.redd.it/cat.jpg)")
        composeRule.onNode(hasClickAction()).performClick()
        assertEquals(listOf("image https://i.redd.it/cat.jpg"), calls)
    }

    @Test
    fun imagesShownAsLinksOpenWhenTapped() {
        show("![img](https://i.redd.it/cat.jpg) and ![gif](giphy|abc123)", renderInlineImages = false)
        composeRule.onNodeWithText("[img]", substring = true).performFirstLinkClick(linkTo("cat.jpg"))
        composeRule.onNodeWithText("[gif]", substring = true).performFirstLinkClick(linkTo("i.giphy.com/abc123"))
        assertEquals(listOf("image https://i.redd.it/cat.jpg", "image https://i.giphy.com/abc123/giphy.gif"), calls)
    }

    @Test
    fun spoilerTextIsInItsOwnBox() {
        show("Before >!secret!< after")
        // The spoiler toggles itself instead of passing the tap on.
        composeRule.onNodeWithText("secret").performClick()
        composeRule.onNodeWithText("secret").performClick()
        assertEquals(emptyList<String>(), calls)
    }
}
