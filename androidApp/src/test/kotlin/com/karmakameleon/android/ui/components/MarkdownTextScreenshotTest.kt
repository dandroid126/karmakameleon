package com.karmakameleon.android.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

private val blocksMarkdown =
    """
    # Heading 1
    ## Heading 2
    ### Heading 3
    #### Heading 4
    ##### Heading 5
    ###### Heading 6
    A paragraph of plain text.

    - A bullet
    - Another bullet

    > A quote
    > > A nested quote

        val code = "block"

    ---
    The end.
    """.trimIndent()

private const val INLINE_MARKDOWN = "**Bold**, *italic*, ***both***, ~~struck~~, `code` and a [link](https://example.com).\n\n>!A secret!<"

// The titles are tooltips on Reddit, so only the links' text shows, without a stray parenthesis.
private const val LINK_TITLES_MARKDOWN =
    "A [link](https://example.com \"title\"), [another](https://example.com 'title') and [a third](https://example.com (title))."

// The definitions aren't shown, and leave one paragraph break between the text around them.
private val referenceLinksMarkdown =
    """
    Read [the guide][guide], [Reddit][] or [a cell].

    [guide]: https://support.reddithelp.com/hc/
    [reddit]: https://www.reddit.com
    [a cell]: https://example.com

    | Table |
    |---|
    | Also in [a cell] |
    """.trimIndent()

// Headings like "# Heading 1" and "## Heading 2", and --- under text isn't a horizontal rule.
private val underlinedHeadingsMarkdown =
    """
    Heading 1
    ===
    Heading 2
    ---
    Text under them.
    """.trimIndent()

// The lines without a > or a marker continue the quote and the item.
private val lazyContinuationMarkdown =
    """
    > A quote that goes on
    without a marker.

    - An item that goes on
    on the next line.
    - Another item
    """.trimIndent()

// Shown without the angle brackets.
private const val AUTOLINKS_MARKDOWN = "Read <https://www.reddit.com/wiki/markdown> or mail <mod@example.com>."

private val tableMarkdown =
    """
    | Feature | Since |
    |---|---|
    | **Sealed** classes | 1.0 |
    | Value classes | 1.5 |
    | Short row |
    """.trimIndent()

// Escaped pipes are text in their cells, inside inline code too.
private val escapedPipeTableMarkdown =
    """
    | Operator | Means |
    |---|---|
    | `a \| b` | a or b |
    | x \|\| y | x or y |
    """.trimIndent()

// Like a bot's comment in r/hockey, with empty spacer columns and made-up usernames. Too wide for a phone, so it
// scrolls.
private val wideTableMarkdown =
    """
    |Stat|Count|.|Most Comments (user)|Count|.|Most Commenters (flair)|Count|
    |:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
    |Number of comments|191||/u/example_fan|13||SJS - NHL|35|
    |Number of unique commenters|72||/u/ExampleViewer|13||OTT - NHL|16|
    |Average comments per user|2.65||/u/ExampleCommenter|11||None|6|
    |Number of unique flairs|13||/u/user4|11||MTL - NHL|5|
    |Number of NHL teams|9||/u/user05|10||EDM - NHL|2|
    |Average number of words per comment|13.26||/u/example_poster|7||Boston Fleet - PWHL|1|
    |Dirtiest mouth: /u/example_swearer|2||/u/example_user|7||DAL - NHL|1|
    """.trimIndent()

// Fits once its text wraps: the long column wraps between words while the short ones stay as narrow as their
// text. The last note is one word longer than a column may be, so it breaks inside the word.
private val wrappingTableMarkdown =
    """
    | Team | GP | W | Notes |
    |---|---|---|---|
    | Sharks | 82 | 41 | A long note that has to wrap onto a few lines to fit beside the other columns |
    | Senators | 82 | 39 | Pneumonoultramicroscopicsilicovolcanoconiosis |
    """.trimIndent()

// Underscores inside words stay as typed, while ones around a word still make emphasis.
private const val UNDERSCORES_MARKDOWN = "snake_case_names, foo__bar__baz and 1_000_000 stay as typed, but _foo_bar_ and __bold__ don't."

// Line breaks and indents are kept, markdown inside isn't parsed, and a longer fence holds a shorter one.
private val fencedCodeMarkdown =
    """
    Some code:
    ```kotlin
    fun main() {
        println("**not bold**")
    }
    ```
    ~~~~
    ```
    A fence inside tildes
    ```
    ~~~~
    """.trimIndent()

private const val MID_LINE_SPOILER_MARKDOWN =
    "Before >!the secret part, long enough to wrap from one line onto the next,!< and after it the sentence goes on."

private val listsMarkdown =
    """
    - A bullet
    - A bullet long enough to wrap, so its second line should start under its text, not under the bullet

    1. First
    2. Second, also long enough to wrap onto a second line under its text
    9. Ninth
    10. Tenth
    """.trimIndent()

private val nestedListsMarkdown =
    """
    - Top
        - Nested four spaces in
        - A nested item long enough to wrap, so its second line should start under its own text
            - Third level
                - Fourth level
    - Back to top

    1. First
       1. Nested three spaces in
       2. Second nested
          - A bullet in a numbered list
    2. Second
    """.trimIndent()

private const val SUPERSCRIPT_MARKDOWN =
    "E = mc^2, ^(two words) up, ^and ^be ^reddited, a [^raised ^link](https://example.com), ^^double and 2^2^2.\n\nA tower past the deepest level: a^b^c^d^e^f^g^h^i^j\n\n## A heading^2"

// A tower on the first line and one in the middle of a wrapped paragraph, so the screenshot shows whether they draw
// over the text above them.
private const val SUPERSCRIPT_TOWERS_MARKDOWN =
    "a^b^c^d^e^f^g^h^i^j starts this paragraph, which goes on long enough to wrap, and then has " +
        "another tower x^y^z^w^v^u^t^s in the middle of it, so it should still wrap onto a few lines below."

private const val IMAGES_MARKDOWN = "A picture:\n\n![img](https://i.redd.it/cat.jpg)\n\nAnd a GIF: ![gif](giphy|abc123)"

// Only markdown that renders correctly today, so no screenshot records a bug as the expected look.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownTextScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val navigation = NavigationRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    private fun show(
        markdown: String,
        darkTheme: Boolean = false,
        renderInlineImages: Boolean = true,
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            val density = Density(LocalDensity.current.density, fontScale)
            // Dynamic colors come from the wallpaper, which would make the screenshots depend on the machine.
            KarmaKameleonTheme(darkTheme = darkTheme, dynamicColor = false) {
                CompositionLocalProvider(LocalDensity provides density) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        MarkdownText(markdown = markdown, renderInlineImages = renderInlineImages, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }

    private fun capture(name: String) = composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    @Test
    fun blocks() {
        show(blocksMarkdown)
        capture("markdown_blocks")
    }

    @Test
    fun blocksDark() {
        show(blocksMarkdown, darkTheme = true)
        capture("markdown_blocks_dark")
    }

    @Test
    fun inlineStyles() {
        show(INLINE_MARKDOWN)
        capture("markdown_inline")
    }

    @Test
    fun linkTitles() {
        show(LINK_TITLES_MARKDOWN)
        capture("markdown_link_titles")
    }

    @Test
    fun referenceLinks() {
        show(referenceLinksMarkdown)
        capture("markdown_reference_links")
    }

    @Test
    fun angleAutolinks() {
        show(AUTOLINKS_MARKDOWN)
        capture("markdown_autolinks")
    }

    @Test
    fun underlinedHeadings() {
        show(underlinedHeadingsMarkdown)
        capture("markdown_underlined_headings")
    }

    @Test
    fun lazyContinuation() {
        show(lazyContinuationMarkdown)
        capture("markdown_lazy_continuation")
    }

    @Test
    fun underscoresInsideWords() {
        show(UNDERSCORES_MARKDOWN)
        capture("markdown_underscores")
    }

    @Test
    fun fencedCode() {
        show(fencedCodeMarkdown)
        capture("markdown_code_fenced")
    }

    @Test
    fun revealedSpoiler() {
        show(INLINE_MARKDOWN)
        composeRule.onNodeWithText("A secret").performFirstLinkClick()
        capture("markdown_spoiler_revealed")
    }

    @Test
    fun midLineSpoiler() {
        show(MID_LINE_SPOILER_MARKDOWN)
        capture("markdown_spoiler_mid_line")
    }

    // The spoiler is in the link's text, so the link's color mustn't show the hidden text.
    @Test
    fun spoilerInLink() {
        show("Spoils [>!the ending!<](https://example.com) here")
        capture("markdown_spoiler_in_link")
    }

    @Test
    fun midLineSpoilerRevealed() {
        show(MID_LINE_SPOILER_MARKDOWN)
        composeRule.onNodeWithText("Before", substring = true).performFirstLinkClick()
        capture("markdown_spoiler_mid_line_revealed")
    }

    @Test
    fun lists() {
        show(listsMarkdown)
        capture("markdown_lists")
    }

    @Test
    fun nestedLists() {
        show(nestedListsMarkdown)
        capture("markdown_lists_nested")
    }

    // The markers' column grows with the font, so 9 and 10 still line up.
    @Test
    fun listsLargeFont() {
        show(listsMarkdown, fontScale = 1.5f)
        capture("markdown_lists_large_font")
    }

    @Test
    fun superscript() {
        show(SUPERSCRIPT_MARKDOWN)
        capture("markdown_superscript")
    }

    @Test
    fun superscriptTowers() {
        show(SUPERSCRIPT_TOWERS_MARKDOWN)
        capture("markdown_superscript_towers")
    }

    @Test
    fun table() {
        show(tableMarkdown)
        capture("markdown_table")
    }

    @Test
    fun tableWithEscapedPipes() {
        show(escapedPipeTableMarkdown)
        capture("markdown_table_escaped_pipes")
    }

    @Test
    fun wideTable() {
        show(wideTableMarkdown)
        capture("markdown_table_wide")
    }

    @Test
    fun wideTableScrolledToEnd() {
        show(wideTableMarkdown)
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).performTouchInput {
            repeat(3) { swipeLeft() }
        }
        capture("markdown_table_wide_scrolled_to_end")
    }

    @Test
    fun wrappingTable() {
        show(wrappingTableMarkdown)
        capture("markdown_table_wrapping")
    }

    @Test
    fun inlineImages() {
        show(IMAGES_MARKDOWN)
        capture("markdown_images")
    }

    @Test
    fun imagesAsLinks() {
        show(IMAGES_MARKDOWN, renderInlineImages = false)
        capture("markdown_images_as_links")
    }
}
