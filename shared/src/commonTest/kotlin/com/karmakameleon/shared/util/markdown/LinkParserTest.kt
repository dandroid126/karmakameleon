package com.karmakameleon.shared.util.markdown

import com.karmakameleon.shared.domain.markdown.MarkdownInline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Links, URLs and images, split from InlineParserTest to keep each class a manageable size.
class LinkParserTest {
    private fun parse(text: String) = parseInlineMarkdown(text)

    // ==================== Links ====================

    @Test
    fun link_inlineStyle() {
        val result = parse("[text](https://example.com)")
        assertEquals(1, result.size)
        val link = assertIs<MarkdownInline.Link>(result[0])
        assertEquals("https://example.com", link.url)
        assertEquals(listOf(MarkdownInline.Text("text")), link.text)
    }

    @Test
    fun link_titleInDoubleQuotes_isDropped() {
        val link = assertIs<MarkdownInline.Link>(parse("[Reddit](https://www.reddit.com \"title\")").single())
        assertEquals("https://www.reddit.com", link.url)
        assertEquals(listOf(MarkdownInline.Text("Reddit")), link.text)
    }

    @Test
    fun link_titleInSingleQuotes_isDropped() {
        val link = assertIs<MarkdownInline.Link>(parse("[Reddit](https://www.reddit.com 'a \"quoted\" title')").single())
        assertEquals("https://www.reddit.com", link.url)
    }

    @Test
    fun link_titleInParentheses_isDroppedWithItsParenthesis() {
        val result = parse("[Reddit](https://www.reddit.com (title)) after")
        val link = assertIs<MarkdownInline.Link>(result[0])
        assertEquals("https://www.reddit.com", link.url)
        assertEquals(listOf(MarkdownInline.Text(" after")), result.drop(1))
    }

    @Test
    fun link_spacesAroundUrlAndTitle() {
        val link = assertIs<MarkdownInline.Link>(parse("[a](  https://example.com   \"title\"  )").single())
        assertEquals("https://example.com", link.url)
    }

    // A quote in the URL itself isn't a title.
    @Test
    fun link_quoteInsideUrl_staysInUrl() {
        val link = assertIs<MarkdownInline.Link>(parse("[a](https://example.com/it's)").single())
        assertEquals("https://example.com/it's", link.url)
    }

    // Without a closing quote it isn't a title, so the link keeps everything up to the ), as before.
    @Test
    fun link_unclosedTitle_keepsTextInUrl() {
        val link = assertIs<MarkdownInline.Link>(parse("[a](https://example.com \"title)").single())
        assertEquals("https://example.com \"title", link.url)
    }

    @Test
    fun link_spaceInUrl_keepsWholeUrl() {
        val link = assertIs<MarkdownInline.Link>(parse("[a](two words)").single())
        assertEquals("two words", link.url)
    }

    private val references = mapOf("ref" to "https://example.com/ref", "cursus lectus" to "https://example.com/cl")

    @Test
    fun referenceLink_full_usesDefinitionUrl() {
        val link = assertIs<MarkdownInline.Link>(parseInlineMarkdown("[*text*][ref]", references).single())
        assertEquals("https://example.com/ref", link.url)
        assertEquals(listOf(MarkdownInline.Italic(listOf(MarkdownInline.Text("text")))), link.text)
    }

    @Test
    fun referenceLink_collapsedAndShortcut_useTextAsLabel() {
        val result = parseInlineMarkdown("[Cursus  Lectus][] and [ref].", references)
        assertEquals(
            listOf(
                MarkdownInline.Link(listOf(MarkdownInline.Text("Cursus  Lectus")), "https://example.com/cl"),
                MarkdownInline.Text(" and "),
                MarkdownInline.Link(listOf(MarkdownInline.Text("ref")), "https://example.com/ref"),
                MarkdownInline.Text("."),
            ),
            result,
        )
    }

    @Test
    fun referenceLink_labelIgnoresCaseAndSpacing() {
        val link = assertIs<MarkdownInline.Link>(parseInlineMarkdown("[text][ CURSUS\tlectus ]", references).single())
        assertEquals("https://example.com/cl", link.url)
    }

    @Test
    fun referenceLink_undefined_staysText() {
        assertEquals(
            listOf(MarkdownInline.Text("[text][missing] and [missing]")),
            parseInlineMarkdown("[text][missing] and [missing]", references),
        )
        assertEquals(listOf(MarkdownInline.Text("[text][ref]")), parse("[text][ref]"))
    }

    @Test
    fun bareUrl_https() {
        val result = parse("https://example.com")
        assertEquals(1, result.size)
        val link = assertIs<MarkdownInline.Link>(result[0])
        assertEquals("https://example.com", link.url)
    }

    @Test
    fun bareUrl_httpUpgradedToHttps() {
        val result = parse("http://example.com")
        assertEquals(1, result.size)
        val link = assertIs<MarkdownInline.Link>(result[0])
        assertEquals("https://example.com", link.url)
    }

    // Reddit escapes markdown characters in a comment's raw text, even inside URLs.
    @Test
    fun bareUrl_undoesBackslashEscapes() {
        val url = "https://example.com/?utm_medium=ios"
        assertEquals(listOf(MarkdownInline.Link(listOf(MarkdownInline.Text(url)), url)), parse("https://example.com/?utm\\_medium=ios"))
    }

    // An escaped parenthesis doesn't end the URL, so the link ends at the first one that isn't escaped.
    @Test
    fun bareUrl_escapedParenthesesStayInUrl() {
        val url = "https://en.wikipedia.org/wiki/Mercury_(planet)"
        assertEquals(
            listOf(
                MarkdownInline.Text("("),
                MarkdownInline.Link(listOf(MarkdownInline.Text(url)), url),
                MarkdownInline.Text(")"),
            ),
            parse("(https://en.wikipedia.org/wiki/Mercury\\_\\(planet\\))"),
        )
    }

    @Test
    fun bareUrl_backslashBeforeOtherCharacterStays() {
        val url = "https://example.com/a\\b"
        assertEquals(listOf(MarkdownInline.Link(listOf(MarkdownInline.Text(url)), url)), parse(url))
    }

    @Test
    fun bareUrl_previewImageUndoesBackslashEscapes() {
        assertEquals(
            listOf(MarkdownInline.Image("https://preview.redd.it/a_b.png?width=640")),
            parse("https://preview.redd.it/a\\_b.png?width=640"),
        )
    }

    @Test
    fun link_undoesBackslashEscapesInUrl() {
        val link = assertIs<MarkdownInline.Link>(parse("[a](https://example.com/?utm\\_medium=ios&amp;x=1)").single())
        assertEquals("https://example.com/?utm_medium=ios&x=1", link.url)
    }

    @Test
    fun link_escapedParenthesesStayInUrl() {
        val link = assertIs<MarkdownInline.Link>(parse("[a](https://en.wikipedia.org/wiki/Mercury\\_\\(planet\\))").single())
        assertEquals("https://en.wikipedia.org/wiki/Mercury_(planet)", link.url)
        val spaced = assertIs<MarkdownInline.Link>(parse("[a](two \\(words\\))").single())
        assertEquals("two (words)", spaced.url)
    }

    @Test
    fun angleAutolink_dropsBrackets() {
        val result = parse("see <https://www.reddit.com/> now")
        assertEquals(
            listOf(
                MarkdownInline.Text("see "),
                MarkdownInline.Link(listOf(MarkdownInline.Text("https://www.reddit.com/")), "https://www.reddit.com/"),
                MarkdownInline.Text(" now"),
            ),
            result,
        )
    }

    @Test
    fun angleAutolink_httpUpgradedAndOtherSchemes() {
        assertEquals("https://example.com", assertIs<MarkdownInline.Link>(parse("<http://example.com>").single()).url)
        val mailto = assertIs<MarkdownInline.Link>(parse("<mailto:me@example.com>").single())
        assertEquals("mailto:me@example.com", mailto.url)
        assertEquals(listOf(MarkdownInline.Text("me@example.com")), mailto.text)
        assertEquals("steam://run/440", assertIs<MarkdownInline.Link>(parse("<steam://run/440>").single()).url)
    }

    @Test
    fun angleAutolink_email_linksToMailto() {
        val link = assertIs<MarkdownInline.Link>(parse("<me@example.com>").single())
        assertEquals("mailto:me@example.com", link.url)
        assertEquals(listOf(MarkdownInline.Text("me@example.com")), link.text)
    }

    // Other schemes, or a space inside the brackets, aren't autolinks, so the brackets stay as text. A URL after the
    // bracket is still a bare URL.
    @Test
    fun angleAutolink_notAUrl_staysText() {
        assertEquals(listOf(MarkdownInline.Text("<foo://bar>")), parse("<foo://bar>"))
        assertEquals(listOf(MarkdownInline.Text("<me@example>")), parse("<me@example>"))
        assertEquals(listOf(MarkdownInline.Text("<foo://bar@x.com>")), parse("<foo://bar@x.com>"))
        assertEquals(
            listOf(
                MarkdownInline.Text("<"),
                MarkdownInline.Link(listOf(MarkdownInline.Text("https://a")), "https://a"),
                MarkdownInline.Text(" b>"),
            ),
            parse("<https://a b>"),
        )
    }

    // ==================== Images ====================

    @Test
    fun inlineImage_imgTag() {
        val result = parse("![img](https://example.com/img.jpg)")
        assertEquals(1, result.size)
        val img = assertIs<MarkdownInline.Image>(result[0])
        assertEquals("https://example.com/img.jpg", img.url)
    }

    @Test
    fun inlineImage_shortId() {
        val result = parse("![img](abc123)")
        assertEquals(1, result.size)
        val img = assertIs<MarkdownInline.Image>(result[0])
        assertTrue(img.url.contains("preview.redd.it"))
        assertTrue(img.url.contains("abc123"))
    }

    @Test
    fun inlineGif_giphy() {
        val result = parse("![gif](giphy|abc123)")
        assertEquals(1, result.size)
        val img = assertIs<MarkdownInline.Image>(result[0])
        assertTrue(img.url.contains("giphy.com"))
        assertTrue(img.url.contains("abc123"))
    }

    @Test
    fun inlineGif_giphyDownsized() {
        val result = parse("![gif](giphy|abc123|downsized)")
        assertEquals(1, result.size)
        val img = assertIs<MarkdownInline.Image>(result[0])
        assertTrue(img.url.contains("giphy-downsized.gif"))
    }

    @Test
    fun bareUrl_previewImageIsShownAsAnImage() {
        val result = parse("look http://preview.redd.it/abc.png?width=640")

        assertEquals(MarkdownInline.Image("https://preview.redd.it/abc.png?width=640"), result.last())
    }
}
