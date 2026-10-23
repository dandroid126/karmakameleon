package com.karmakameleon.android.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performFirstLinkClick
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

private val tableMarkdown =
    """
    | Feature | Since |
    |---|---|
    | **Sealed** classes | 1.0 |
    | Value classes | 1.5 |
    | Short row |
    """.trimIndent()

private const val MID_LINE_SPOILER_MARKDOWN =
    "Before >!the secret part, long enough to wrap from one line onto the next,!< and after it the sentence goes on."

private const val IMAGES_MARKDOWN = "A picture:\n\n![img](https://i.redd.it/cat.jpg)\n\nAnd a GIF: ![gif](giphy|abc123)"

// Only markdown that renders correctly today, so no screenshot records a bug as the expected look. Numbered lists,
// superscript, nested lists and fenced code are left out until #83 fixes them.
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
    ) {
        composeRule.setContent {
            // Dynamic colors come from the wallpaper, which would make the screenshots depend on the machine.
            KarmaKameleonTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MarkdownText(markdown = markdown, renderInlineImages = renderInlineImages, modifier = Modifier.padding(8.dp))
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

    @Test
    fun midLineSpoilerRevealed() {
        show(MID_LINE_SPOILER_MARKDOWN)
        composeRule.onNodeWithText("Before", substring = true).performFirstLinkClick()
        capture("markdown_spoiler_mid_line_revealed")
    }

    @Test
    fun table() {
        show(tableMarkdown)
        capture("markdown_table")
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
