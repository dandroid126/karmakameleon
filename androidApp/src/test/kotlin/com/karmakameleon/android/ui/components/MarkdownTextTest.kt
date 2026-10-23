package com.karmakameleon.android.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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

    private val isSpoiler = { link: AnnotatedString.Range<LinkAnnotation> -> (link.item as LinkAnnotation.Clickable).tag == "spoiler" }

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
    fun tappingSpoilerRevealsIt() {
        show("Before >!a secret!< and after")
        val text = composeRule.onNodeWithText("Before a secret and after")
        text.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Hidden spoiler"))

        text.performFirstLinkClick()

        text.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun spoilersInOneParagraphAreRevealedOneAtATime() {
        show("A >!one!< and >!two!<")
        val text = composeRule.onNodeWithText("A one and two")
        val hiddenState = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Hidden spoiler")

        text.performFirstLinkClick(isSpoiler)

        text.assert(hiddenState)
        val spoilersLeft =
            text
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .single()
                .let { it.getLinkAnnotations(0, it.length) }
                .filter(isSpoiler)
        assertEquals(listOf("two"), spoilersLeft.map { "A one and two".substring(it.start, it.end) })

        text.performFirstLinkClick(isSpoiler)

        text.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun linkInHiddenSpoilerRevealsItBeforeOpening() {
        show(">!See [r/Kotlin](https://www.reddit.com/r/Kotlin)!<")
        val text = composeRule.onNodeWithText("See r/Kotlin")

        text.performFirstLinkClick(linkTo("reddit.com"))
        assertEquals(emptyList<String>(), navigation.calls)

        text.performFirstLinkClick(linkTo("reddit.com"))
        assertEquals(listOf("subreddit Kotlin"), navigation.calls)
    }
}
