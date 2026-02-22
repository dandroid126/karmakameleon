package com.reader.shared.util.markdown

import com.reader.shared.domain.markdown.MarkdownBlock
import com.reader.shared.domain.markdown.MarkdownInline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BlockParserTest {

    private fun parse(text: String) = parseMarkdownBlocks(text)

    // ==================== Headings ====================

    @Test
    fun heading_level1() {
        val result = parse("# Heading")
        assertEquals(1, result.size)
        val h = assertIs<MarkdownBlock.Heading>(result[0])
        assertEquals(1, h.level)
        assertEquals(listOf(MarkdownInline.Text("Heading")), h.children)
    }

    @Test
    fun heading_level2() {
        val result = parse("## Heading")
        assertEquals(1, result.size)
        val h = assertIs<MarkdownBlock.Heading>(result[0])
        assertEquals(2, h.level)
    }

    @Test
    fun heading_level3() {
        val result = parse("### Heading")
        assertEquals(1, result.size)
        val h = assertIs<MarkdownBlock.Heading>(result[0])
        assertEquals(3, h.level)
    }

    @Test
    fun heading_level4() {
        val result = parse("#### Heading")
        assertEquals(1, result.size)
        val h = assertIs<MarkdownBlock.Heading>(result[0])
        assertEquals(4, h.level)
    }

    @Test
    fun heading_level5() {
        val result = parse("##### Heading")
        assertEquals(1, result.size)
        val h = assertIs<MarkdownBlock.Heading>(result[0])
        assertEquals(5, h.level)
    }

    @Test
    fun heading_level6() {
        val result = parse("###### Heading")
        assertEquals(1, result.size)
        val h = assertIs<MarkdownBlock.Heading>(result[0])
        assertEquals(6, h.level)
    }

    // ==================== Paragraphs ====================

    @Test
    fun paragraph_singleLine() {
        val result = parse("Hello world")
        assertEquals(1, result.size)
        assertIs<MarkdownBlock.Paragraph>(result[0])
    }

    @Test
    fun paragraph_multiLine_mergedWithSpace() {
        val result = parse("line one\nline two")
        assertEquals(1, result.size)
        val p = assertIs<MarkdownBlock.Paragraph>(result[0])
        val text = p.children.filterIsInstance<MarkdownInline.Text>().joinToString("") { it.text }
        assertTrue(text.contains("line one"))
        assertTrue(text.contains("line two"))
    }

    @Test
    fun paragraph_hardBreak_twoTrailingSpaces() {
        val result = parse("line one  \nline two")
        assertEquals(1, result.size)
        val p = assertIs<MarkdownBlock.Paragraph>(result[0])
        val text = p.children.filterIsInstance<MarkdownInline.Text>().joinToString("") { it.text }
        assertTrue(text.contains("\n"))
    }

    // ==================== Lists ====================

    @Test
    fun bulletList_dash() {
        val result = parse("- item")
        assertEquals(1, result.size)
        val item = assertIs<MarkdownBlock.BulletListItem>(result[0])
        val text = item.children.filterIsInstance<MarkdownInline.Text>().joinToString("") { it.text }
        assertTrue(text.contains("item"))
    }

    @Test
    fun bulletList_asterisk() {
        val result = parse("* item")
        assertEquals(1, result.size)
        assertIs<MarkdownBlock.BulletListItem>(result[0])
    }

    @Test
    fun bulletList_plus() {
        val result = parse("+ item")
        assertEquals(1, result.size)
        val item = assertIs<MarkdownBlock.BulletListItem>(result[0])
        val text = item.children.filterIsInstance<MarkdownInline.Text>().joinToString("") { it.text }
        assertTrue(text.contains("item"))
    }

    @Test
    fun orderedList() {
        val result = parse("1. first\n2. second")
        assertEquals(2, result.size)
        val first = assertIs<MarkdownBlock.OrderedListItem>(result[0])
        assertEquals(1, first.number)
        val second = assertIs<MarkdownBlock.OrderedListItem>(result[1])
        assertEquals(2, second.number)
    }

    @Test
    fun orderedList_parenSyntax() {
        val result = parse("1) first\n2) second")
        assertEquals(2, result.size)
        val first = assertIs<MarkdownBlock.OrderedListItem>(result[0])
        assertEquals(1, first.number)
        val second = assertIs<MarkdownBlock.OrderedListItem>(result[1])
        assertEquals(2, second.number)
    }

    // ==================== Block quote ====================

    @Test
    fun blockQuote_singleLine() {
        val result = parse("> quote")
        assertEquals(1, result.size)
        assertIs<MarkdownBlock.BlockQuote>(result[0])
    }

    @Test
    fun blockQuote_multiLine() {
        val result = parse("> line one\n> line two")
        assertEquals(1, result.size)
        assertIs<MarkdownBlock.BlockQuote>(result[0])
    }

    @Test
    fun blockQuote_withInlineBold() {
        val result = parse("> **bold** text")
        assertEquals(1, result.size)
        val quote = assertIs<MarkdownBlock.BlockQuote>(result[0])
        val para = assertIs<MarkdownBlock.Paragraph>(quote.children[0])
        assertTrue(para.children.any { it is MarkdownInline.Bold })
    }

    @Test
    fun blockQuote_withInlineItalic() {
        val result = parse("> *italic* text")
        assertEquals(1, result.size)
        val quote = assertIs<MarkdownBlock.BlockQuote>(result[0])
        val para = assertIs<MarkdownBlock.Paragraph>(quote.children[0])
        assertTrue(para.children.any { it is MarkdownInline.Italic })
    }

    @Test
    fun blockQuote_withInlineCode() {
        val result = parse("> `code` here")
        assertEquals(1, result.size)
        val quote = assertIs<MarkdownBlock.BlockQuote>(result[0])
        val para = assertIs<MarkdownBlock.Paragraph>(quote.children[0])
        assertTrue(para.children.any { it is MarkdownInline.Code })
    }

    @Test
    fun blockQuote_withInlineLink() {
        val result = parse("> [link](https://example.com)")
        assertEquals(1, result.size)
        val quote = assertIs<MarkdownBlock.BlockQuote>(result[0])
        val para = assertIs<MarkdownBlock.Paragraph>(quote.children[0])
        assertTrue(para.children.any { it is MarkdownInline.Link })
    }

    @Test
    fun blockQuote_withSpoiler() {
        val result = parse("> >!spoiler!<")
        assertEquals(1, result.size)
        val quote = assertIs<MarkdownBlock.BlockQuote>(result[0])
        val para = assertIs<MarkdownBlock.Paragraph>(quote.children[0])
        assertTrue(para.children.any { it is MarkdownInline.Spoiler })
    }

    @Test
    fun blockQuote_withHeading() {
        val result = parse("> # Heading")
        assertEquals(1, result.size)
        val quote = assertIs<MarkdownBlock.BlockQuote>(result[0])
        assertIs<MarkdownBlock.Heading>(quote.children[0])
    }

    @Test
    fun blockQuote_withBulletList() {
        val result = parse("> - item one\n> - item two")
        assertEquals(1, result.size)
        val quote = assertIs<MarkdownBlock.BlockQuote>(result[0])
        assertEquals(2, quote.children.count { it is MarkdownBlock.BulletListItem })
    }

    @Test
    fun blockQuote_withOrderedList() {
        val result = parse("> 1. first\n> 2. second")
        assertEquals(1, result.size)
        val quote = assertIs<MarkdownBlock.BlockQuote>(result[0])
        assertEquals(2, quote.children.count { it is MarkdownBlock.OrderedListItem })
    }

    @Test
    fun blockQuote_withCodeBlock() {
        val result = parse(">     code here")
        assertEquals(1, result.size)
        val quote = assertIs<MarkdownBlock.BlockQuote>(result[0])
        assertIs<MarkdownBlock.CodeBlock>(quote.children[0])
    }

    @Test
    fun blockQuote_nested() {
        val result = parse("> > nested")
        assertEquals(1, result.size)
        val outer = assertIs<MarkdownBlock.BlockQuote>(result[0])
        assertIs<MarkdownBlock.BlockQuote>(outer.children[0])
    }

    @Test
    fun spoiler_notParsedAsBlockQuote() {
        val result = parse(">!spoiler!<")
        assertEquals(1, result.size)
        assertIs<MarkdownBlock.Paragraph>(result[0])
        val para = result[0] as MarkdownBlock.Paragraph
        assertTrue(para.children.any { it is MarkdownInline.Spoiler })
    }

    @Test
    fun spoiler_andBlockQuote_distinct() {
        val result = parse("> quote\n>!spoiler!<")
        assertEquals(2, result.size)
        assertIs<MarkdownBlock.BlockQuote>(result[0])
        val para = assertIs<MarkdownBlock.Paragraph>(result[1])
        assertTrue(para.children.any { it is MarkdownInline.Spoiler })
    }

    // ==================== Code block ====================

    @Test
    fun codeBlock_fourSpaceIndent() {
        val result = parse("    code here")
        assertEquals(1, result.size)
        val code = assertIs<MarkdownBlock.CodeBlock>(result[0])
        assertEquals("code here", code.text)
    }

    // ==================== Horizontal rule ====================

    @Test
    fun horizontalRule_threeDashes() {
        val result = parse("---")
        assertEquals(1, result.size)
        assertIs<MarkdownBlock.HorizontalRule>(result[0])
    }

    @Test
    fun horizontalRule_manyDashes() {
        val result = parse("------")
        assertEquals(1, result.size)
        assertIs<MarkdownBlock.HorizontalRule>(result[0])
    }

    @Test
    fun horizontalRule_threeAsterisks() {
        val result = parse("***")
        assertEquals(1, result.size)
        assertIs<MarkdownBlock.HorizontalRule>(result[0])
    }

    @Test
    fun horizontalRule_manyAsterisks() {
        val result = parse("******")
        assertEquals(1, result.size)
        assertIs<MarkdownBlock.HorizontalRule>(result[0])
    }

    @Test
    fun horizontalRule_threeUnderscores() {
        val result = parse("___")
        assertEquals(1, result.size)
        assertIs<MarkdownBlock.HorizontalRule>(result[0])
    }

    @Test
    fun horizontalRule_manyUnderscores() {
        val result = parse("______")
        assertEquals(1, result.size)
        assertIs<MarkdownBlock.HorizontalRule>(result[0])
    }

    // ==================== Table ====================

    @Test
    fun table_basic() {
        val md = "| A | B |\n|---|---|\n| 1 | 2 |"
        val result = parse(md)
        assertEquals(1, result.size)
        val table = assertIs<MarkdownBlock.Table>(result[0])
        assertEquals(listOf("A", "B"), table.header)
        assertEquals(1, table.rows.size)
        assertEquals(listOf("1", "2"), table.rows[0])
    }

    @Test
    fun table_multipleRows() {
        val md = "| H1 | H2 |\n|---|---|\n| r1c1 | r1c2 |\n| r2c1 | r2c2 |"
        val result = parse(md)
        assertEquals(1, result.size)
        val table = assertIs<MarkdownBlock.Table>(result[0])
        assertEquals(2, table.rows.size)
    }

    // ==================== Blank lines ====================

    @Test
    fun blankLine_betweenParagraphs() {
        val result = parse("para one\n\npara two")
        assertTrue(result.any { it is MarkdownBlock.BlankLine })
        assertTrue(result.any { it is MarkdownBlock.Paragraph })
    }

    @Test
    fun blankLine_multipleCollapsedToOne() {
        val result = parse("para one\n\n\n\npara two")
        assertEquals(1, result.count { it is MarkdownBlock.BlankLine })
    }

    // ==================== Mixed content ====================

    @Test
    fun mixed_headingThenParagraph() {
        val result = parse("# Title\n\nSome text")
        assertTrue(result.any { it is MarkdownBlock.Heading })
        assertTrue(result.any { it is MarkdownBlock.Paragraph })
    }

    @Test
    fun mixed_paragraphWithInlineFormatting() {
        val result = parse("Hello **world**")
        assertEquals(1, result.size)
        val p = assertIs<MarkdownBlock.Paragraph>(result[0])
        assertTrue(p.children.any { it is MarkdownInline.Bold })
    }

    @Test
    fun mixed_headingWithInlineFormatting() {
        val result = parse("# Hello *world*")
        assertEquals(1, result.size)
        val h = assertIs<MarkdownBlock.Heading>(result[0])
        assertTrue(h.children.any { it is MarkdownInline.Italic })
    }
}
