package com.karmakameleon.shared.util.markdown

import com.karmakameleon.shared.domain.markdown.MarkdownInline
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

    // ==================== Underscores inside words ====================

    @Test
    fun underscoresInsideWord_stayText() {
        assertEquals(listOf(MarkdownInline.Text("snake_case_names")), parse("snake_case_names"))
        assertEquals(listOf(MarkdownInline.Text("foo__bar__baz")), parse("foo__bar__baz"))
        assertEquals(listOf(MarkdownInline.Text("foo___bar___baz")), parse("foo___bar___baz"))
        // The second underscore of a run inside a word is inside the word too.
        assertEquals(listOf(MarkdownInline.Text("foo__bar__baz and _")), parse("foo__bar__baz and _"))
    }

    @Test
    fun underscoreAfterDigit_staysText() {
        assertEquals(listOf(MarkdownInline.Text("1_000_000")), parse("1_000_000"))
    }

    @Test
    fun italicUnderscores_skipUnderscoreInsideWord() {
        val italic = assertIs<MarkdownInline.Italic>(parse("_foo_bar_").single())
        assertEquals(listOf(MarkdownInline.Text("foo_bar")), italic.children)
    }

    @Test
    fun boldUnderscores_skipUnderscoresInsideWord() {
        val bold = assertIs<MarkdownInline.Bold>(parse("__foo__bar__").single())
        assertEquals(listOf(MarkdownInline.Text("foo__bar")), bold.children)
    }

    @Test
    fun italicUnderscores_afterPunctuation() {
        val result = parse("(_italic_), done")
        assertEquals(MarkdownInline.Text("("), result[0])
        assertIs<MarkdownInline.Italic>(result[1])
        assertEquals(MarkdownInline.Text("), done"), result[2])
    }

    @Test
    fun italicUnderscores_withoutCloseOutsideWord_staysText() {
        assertEquals(listOf(MarkdownInline.Text("_snake_case")), parse("_snake_case"))
    }

    @Test
    fun asterisksInsideWord_stillEmphasize() {
        val result = parse("vul*pu*ta**te**")
        assertEquals(MarkdownInline.Text("vul"), result[0])
        assertIs<MarkdownInline.Italic>(result[1])
        assertEquals(MarkdownInline.Text("ta"), result[2])
        assertIs<MarkdownInline.Bold>(result[3])
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

    @Test
    fun superscript_doubleCaretNests() {
        val result = parse("^^double")
        val outer = assertIs<MarkdownInline.Superscript>(result.single())
        val inner = assertIs<MarkdownInline.Superscript>(outer.children.single())
        assertEquals(listOf(MarkdownInline.Text("double")), inner.children)
    }

    @Test
    fun superscript_caretInsideWordNests() {
        val result = parse("2^2^2")
        assertEquals(MarkdownInline.Text("2"), result[0])
        val outer = assertIs<MarkdownInline.Superscript>(result[1])
        assertEquals(MarkdownInline.Text("2"), outer.children[0])
        val inner = assertIs<MarkdownInline.Superscript>(outer.children[1])
        assertEquals(listOf(MarkdownInline.Text("2")), inner.children)
    }

    // Each superscript level wraps the next, so this unwraps them and returns how many there were and what's inside.
    private fun superscriptDepth(inlines: List<MarkdownInline>): Pair<Int, List<MarkdownInline>> {
        var depth = 0
        var children = inlines
        while (children.size == 1 && children[0] is MarkdownInline.Superscript) {
            children = (children[0] as MarkdownInline.Superscript).children
            depth++
        }
        return depth to children
    }

    @Test
    fun superscript_nestingStopsAtThreeLevels() {
        val (depth, inside) = superscriptDepth(parse("^".repeat(10) + "tower"))
        assertEquals(3, depth)
        assertEquals(listOf(MarkdownInline.Text("tower")), inside)
    }

    @Test
    fun superscript_parenthesesPastDeepestLevelAreText() {
        val (depth, inside) = superscriptDepth(parse("^".repeat(3) + "^(word)"))
        assertEquals(3, depth)
        assertEquals(listOf(MarkdownInline.Text("^(word)")), inside)
    }

    // Comments can be 10,000 characters long, so a comment of carets mustn't overflow the stack.
    @Test
    fun superscript_manyCaretsDoNotOverflow() {
        val (depth, inside) = superscriptDepth(parse("^".repeat(10_000) + "x"))
        assertEquals(3, depth)
        assertEquals(listOf(MarkdownInline.Text("x")), inside)
    }

    @Test
    fun superscript_caretBeforeSpaceIsText() {
        assertEquals(listOf(MarkdownInline.Text("a ^ b")), parse("a ^ b"))
    }

    @Test
    fun superscript_caretAtEndIsText() {
        assertEquals(listOf(MarkdownInline.Text("a^")), parse("a^"))
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

    @Test
    fun bareUrl_previewImageIsShownAsAnImage() {
        val result = parse("look http://preview.redd.it/abc.png?width=640")

        assertEquals(MarkdownInline.Image("https://preview.redd.it/abc.png?width=640"), result.last())
    }
}
