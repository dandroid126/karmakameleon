package com.reader.shared.util.markdown

import com.reader.shared.domain.markdown.MarkdownInline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InlineParserTest {

    private fun parse(text: String) = parseInlineMarkdown(text)

    // ==================== Plain text ====================

    @Test
    fun plainText_returnsText() {
        val result = parse("hello world")
        assertEquals(listOf(MarkdownInline.Text("hello world")), result)
    }

    @Test
    fun emptyString_returnsEmpty() {
        val result = parse("")
        assertEquals(emptyList(), result)
    }

    // ==================== Bold ====================

    @Test
    fun bold_doubleAsterisk() {
        val result = parse("**bold**")
        assertEquals(1, result.size)
        val bold = assertIs<MarkdownInline.Bold>(result[0])
        assertEquals(listOf(MarkdownInline.Text("bold")), bold.children)
    }

    @Test
    fun bold_doubleUnderscore() {
        val result = parse("__bold__")
        assertEquals(1, result.size)
        val bold = assertIs<MarkdownInline.Bold>(result[0])
        assertEquals(listOf(MarkdownInline.Text("bold")), bold.children)
    }

    @Test
    fun bold_withSurroundingText() {
        val result = parse("before **bold** after")
        assertEquals(3, result.size)
        assertIs<MarkdownInline.Text>(result[0])
        assertIs<MarkdownInline.Bold>(result[1])
        assertIs<MarkdownInline.Text>(result[2])
    }

    // ==================== Italic ====================

    @Test
    fun italic_singleAsterisk() {
        val result = parse("*italic*")
        assertEquals(1, result.size)
        val italic = assertIs<MarkdownInline.Italic>(result[0])
        assertEquals(listOf(MarkdownInline.Text("italic")), italic.children)
    }

    @Test
    fun italic_singleUnderscore() {
        val result = parse("_italic_")
        assertEquals(1, result.size)
        val italic = assertIs<MarkdownInline.Italic>(result[0])
        assertEquals(listOf(MarkdownInline.Text("italic")), italic.children)
    }

    // ==================== Bold+Italic ====================

    @Test
    fun boldItalic_tripleAsterisk() {
        val result = parse("***bolditalic***")
        assertEquals(1, result.size)
        val bi = assertIs<MarkdownInline.BoldItalic>(result[0])
        assertEquals(listOf(MarkdownInline.Text("bolditalic")), bi.children)
    }

    @Test
    fun boldItalic_tripleUnderscore() {
        val result = parse("___bolditalic___")
        assertEquals(1, result.size)
        val bi = assertIs<MarkdownInline.BoldItalic>(result[0])
        assertEquals(listOf(MarkdownInline.Text("bolditalic")), bi.children)
    }

    // ==================== Strikethrough ====================

    @Test
    fun strikethrough_doubleTilde() {
        val result = parse("~~strike~~")
        assertEquals(1, result.size)
        val s = assertIs<MarkdownInline.Strikethrough>(result[0])
        assertEquals(listOf(MarkdownInline.Text("strike")), s.children)
    }

    // ==================== Code ====================

    @Test
    fun inlineCode_backtick() {
        val result = parse("`code`")
        assertEquals(1, result.size)
        val code = assertIs<MarkdownInline.Code>(result[0])
        assertEquals("code", code.text)
    }

    @Test
    fun inlineCode_doesNotParseInnerMarkdown() {
        val result = parse("`**not bold**`")
        assertEquals(1, result.size)
        val code = assertIs<MarkdownInline.Code>(result[0])
        assertEquals("**not bold**", code.text)
    }

    // ==================== Superscript ====================

    @Test
    fun superscript_caretParens() {
        val result = parse("^(sup)")
        assertEquals(1, result.size)
        val sup = assertIs<MarkdownInline.Superscript>(result[0])
        assertEquals(listOf(MarkdownInline.Text("sup")), sup.children)
    }

    @Test
    fun superscript_caretWord() {
        val result = parse("^word")
        assertEquals(1, result.size)
        val sup = assertIs<MarkdownInline.Superscript>(result[0])
        assertEquals(listOf(MarkdownInline.Text("word")), sup.children)
    }

    @Test
    fun superscript_caretWordStopsAtWhitespace() {
        val result = parse("^word rest")
        assertEquals(2, result.size)
        assertIs<MarkdownInline.Superscript>(result[0])
        assertEquals(MarkdownInline.Text(" rest"), result[1])
    }

    // ==================== Spoiler ====================

    @Test
    fun spoiler_gtBangBang() {
        val result = parse(">!spoiler!<")
        assertEquals(1, result.size)
        val spoiler = assertIs<MarkdownInline.Spoiler>(result[0])
        assertEquals(listOf(MarkdownInline.Text("spoiler")), spoiler.children)
    }

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
    fun link_refStyle() {
        val result = parse("[text][ref]")
        assertEquals(1, result.size)
        val link = assertIs<MarkdownInline.Link>(result[0])
        assertEquals("text", link.url)
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

    // ==================== Reddit references ====================

    @Test
    fun subredditRef_withSlash() {
        val result = parse("/r/kotlin")
        assertEquals(1, result.size)
        val link = assertIs<MarkdownInline.Link>(result[0])
        assertTrue(link.url.contains("reddit.com"))
        assertTrue(link.url.contains("r/kotlin"))
    }

    @Test
    fun subredditRef_withoutSlash() {
        val result = parse("r/kotlin")
        assertEquals(1, result.size)
        val link = assertIs<MarkdownInline.Link>(result[0])
        assertTrue(link.url.contains("reddit.com"))
    }

    @Test
    fun userRef_withSlash() {
        val result = parse("/u/testuser")
        assertEquals(1, result.size)
        val link = assertIs<MarkdownInline.Link>(result[0])
        assertTrue(link.url.contains("reddit.com"))
        assertTrue(link.url.contains("u/testuser"))
    }

    @Test
    fun escapedSubreddit_noLink() {
        val result = parse("\\/r/kotlin")
        assertEquals(1, result.size)
        val text = assertIs<MarkdownInline.Text>(result[0])
        assertEquals("/r/kotlin", text.text)
    }

    @Test
    fun escapedUser_noLink() {
        val result = parse("\\/u/testuser")
        assertEquals(1, result.size)
        val text = assertIs<MarkdownInline.Text>(result[0])
        assertEquals("/u/testuser", text.text)
    }

    // ==================== Backslash escape ====================

    @Test
    fun backslashEscape_asterisk() {
        val result = parse("\\*not italic\\*")
        assertEquals(3, result.size)
        assertEquals(MarkdownInline.Text("*"), result[0])
        assertEquals(MarkdownInline.Text("not italic"), result[1])
        assertEquals(MarkdownInline.Text("*"), result[2])
    }

    // ==================== Recursive / Nested ====================

    @Test
    fun nested_italicInsideBold() {
        val result = parse("**bold *italic* bold**")
        assertEquals(1, result.size)
        val bold = assertIs<MarkdownInline.Bold>(result[0])
        assertEquals(3, bold.children.size)
        assertIs<MarkdownInline.Text>(bold.children[0])
        assertIs<MarkdownInline.Italic>(bold.children[1])
        assertIs<MarkdownInline.Text>(bold.children[2])
    }

    @Test
    fun nested_superscriptInsideItalic() {
        val result = parse("*italic ^superscript^*")
        assertEquals(1, result.size)
        val italic = assertIs<MarkdownInline.Italic>(result[0])
        assertEquals(2, italic.children.size)
        assertEquals(MarkdownInline.Text("italic "), italic.children[0])
        assertIs<MarkdownInline.Superscript>(italic.children[1])
    }

    @Test
    fun nested_strikethroughWithItalic() {
        val result = parse("~~strike *italic*~~")
        assertEquals(1, result.size)
        val strike = assertIs<MarkdownInline.Strikethrough>(result[0])
        assertEquals(2, strike.children.size)
        assertIs<MarkdownInline.Text>(strike.children[0])
        assertIs<MarkdownInline.Italic>(strike.children[1])
    }

    @Test
    fun nested_boldLinkText() {
        val result = parse("[**bold**](https://example.com)")
        assertEquals(1, result.size)
        val link = assertIs<MarkdownInline.Link>(result[0])
        assertEquals("https://example.com", link.url)
        assertEquals(1, link.text.size)
        assertIs<MarkdownInline.Bold>(link.text[0])
    }

    @Test
    fun nested_superscriptInsideSpoiler() {
        val result = parse(">!spoiler ^sup^!<")
        assertEquals(1, result.size)
        val spoiler = assertIs<MarkdownInline.Spoiler>(result[0])
        assertTrue(spoiler.children.any { it is MarkdownInline.Superscript })
    }

    @Test
    fun nested_multipleFormatsInParagraph() {
        val result = parse("**bold** and *italic* and ~~strike~~")
        assertEquals(5, result.size)
        assertIs<MarkdownInline.Bold>(result[0])
        assertIs<MarkdownInline.Text>(result[1])
        assertIs<MarkdownInline.Italic>(result[2])
        assertIs<MarkdownInline.Text>(result[3])
        assertIs<MarkdownInline.Strikethrough>(result[4])
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
}
