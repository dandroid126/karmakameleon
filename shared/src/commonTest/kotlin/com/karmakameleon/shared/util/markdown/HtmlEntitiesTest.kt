package com.karmakameleon.shared.util.markdown

import com.karmakameleon.shared.domain.markdown.MarkdownInline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Character references like &mdash; in the inline parser. */
class HtmlEntitiesTest {
    private fun parse(text: String) = parseInlineMarkdown(text)

    @Test
    fun htmlEntity_named() {
        assertEquals(
            listOf(
                MarkdownInline.Text("a "),
                MarkdownInline.Text("\u2014"),
                MarkdownInline.Text(" b "),
                MarkdownInline.Text("\u2665"),
            ),
            parse("a &mdash; b &hearts;"),
        )
    }

    @Test
    fun htmlEntity_numeric() {
        assertEquals(MarkdownInline.Text("\u2005"), parse("&#x2005;").single())
        assertEquals(MarkdownInline.Text("\u200B"), parse("&#8203;").single())
        assertEquals(MarkdownInline.Text("\uD83D\uDE00"), parse("&#X1F600;").single())
    }

    @Test
    fun htmlEntity_notACharacter_isReplacementCharacter() {
        assertEquals(MarkdownInline.Text("\uFFFD"), parse("&#0;").single())
        assertEquals(MarkdownInline.Text("\uFFFD"), parse("&#xD800;").single())
        assertEquals(MarkdownInline.Text("\uFFFD"), parse("&#x110000;").single())
    }

    @Test
    fun htmlEntity_nbspIsNonBreakingSpace() {
        assertEquals(MarkdownInline.Text("\u00A0"), parse("&nbsp;").single())
    }

    @Test
    fun htmlEntity_decodesOnce() {
        assertEquals(listOf(MarkdownInline.Text("<"), MarkdownInline.Text("3")), parse("&lt;3"))
        assertEquals(listOf(MarkdownInline.Text("&"), MarkdownInline.Text("lt;")), parse("&amp;lt;"))
    }

    @Test
    fun htmlEntity_unknownOrUnclosed_staysText() {
        assertEquals(listOf(MarkdownInline.Text("&nosuch; &amp &# &")), parse("&nosuch; &amp &# &"))
    }

    @Test
    fun htmlEntity_isNotMarkdown() {
        assertEquals(
            listOf(MarkdownInline.Text("*"), MarkdownInline.Text("not italic"), MarkdownInline.Text("*")),
            parse("&#42;not italic&#42;"),
        )
    }

    @Test
    fun htmlEntity_inCode_staysAsTyped() {
        assertEquals(MarkdownInline.Code("&mdash;"), parse("`&mdash;`").single())
    }

    @Test
    fun htmlEntity_inLinkText() {
        val link = assertIs<MarkdownInline.Link>(parse("[a &amp; b](https://example.com)").single())
        assertEquals(MarkdownInline.Text("&"), link.text[1])
    }

    @Test
    fun htmlEntity_inLinkUrl_isDecoded() {
        val link = assertIs<MarkdownInline.Link>(parse("[x](https://example.com/?a=1&amp;b=2)").single())
        assertEquals("https://example.com/?a=1&b=2", link.url)
        val titled = assertIs<MarkdownInline.Link>(parse("[x](https://example.com/?a=1&amp;b=2 \"title\")").single())
        assertEquals("https://example.com/?a=1&b=2", titled.url)
    }

    @Test
    fun htmlEntity_inBareUrl_isDecoded() {
        val link = assertIs<MarkdownInline.Link>(parse("https://example.com/?a=1&amp;b=2").single())
        assertEquals("https://example.com/?a=1&b=2", link.url)
        assertEquals(listOf(MarkdownInline.Text("https://example.com/?a=1&b=2")), link.text)
    }

    @Test
    fun decodeHtmlEntities_decodesEveryReference() {
        assertEquals("Tom & Jerry <3 \u2014\u00A0ok &nosuch; &", decodeHtmlEntities("Tom &amp; Jerry &lt;3 &mdash;&nbsp;ok &nosuch; &"))
        assertEquals("&lt;", decodeHtmlEntities("&amp;lt;"))
    }
}
