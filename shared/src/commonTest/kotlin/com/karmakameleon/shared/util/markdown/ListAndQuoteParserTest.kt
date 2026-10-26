package com.karmakameleon.shared.util.markdown

import com.karmakameleon.shared.domain.markdown.MarkdownBlock
import com.karmakameleon.shared.domain.markdown.MarkdownInline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// The block parser's tests for lists and quotes, apart from BlockParserTest so that neither class is too large.
class ListAndQuoteParserTest {
    private fun parse(text: String) = parseMarkdownBlocks(text)

    // ==================== Lists ====================

    @Test
    fun bulletList_dash() {
        val result = parse("- item")
        assertEquals(1, result.size)
        val item = assertIs<MarkdownBlock.BulletListItem>(result[0])
        assertEquals(listOf(MarkdownInline.Text("item")), item.children)
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

    // Like CommonMark, a number longer than 9 digits doesn't start a list item, so it can't overflow an Int.
    @Test
    fun orderedList_numberTooLong() {
        val result = parse("99999999999. x")
        assertIs<MarkdownBlock.Paragraph>(result.single())
    }

    private fun bullet(
        text: String,
        level: Int = 0,
    ) = MarkdownBlock.BulletListItem(listOf(MarkdownInline.Text(text)), level)

    private fun numbered(
        number: Int,
        text: String,
        level: Int = 0,
    ) = MarkdownBlock.OrderedListItem(number, listOf(MarkdownInline.Text(text)), level)

    @Test
    fun nestedList_fourSpaces() {
        assertEquals(
            listOf(bullet("Top"), bullet("Nested item", 1), bullet("Another nested", 1), bullet("Back to top")),
            parse("- Top\n    - Nested item\n    - Another nested\n- Back to top"),
        )
    }

    // Reddit's guide indents unordered sublists by two spaces and ordered ones by three, which is where the text of
    // the item above starts.
    @Test
    fun nestedList_indentedToParentText() {
        assertEquals(listOf(bullet("a"), bullet("b", 1), bullet("c", 1), bullet("d")), parse("- a\n  - b\n  - c\n- d"))
        assertEquals(
            listOf(numbered(1, "a"), numbered(1, "b", 1), numbered(2, "c", 1), numbered(2, "d")),
            parse("1. a\n   1. b\n   2. c\n2. d"),
        )
    }

    // Two spaces don't reach the text of "1. ", so the item isn't nested.
    @Test
    fun nestedList_notIndentedEnough() {
        assertEquals(listOf(numbered(1, "a"), numbered(2, "b")), parse("1. a\n  2. b"))
    }

    @Test
    fun nestedList_severalLevels() {
        assertEquals(
            listOf(bullet("a"), numbered(1, "b", 1), bullet("c", 2), bullet("d", 1), bullet("e")),
            parse("- a\n  1. b\n     - c\n  - d\n- e"),
        )
    }

    // A blank line between an item and its sublist keeps the nesting, and stays as a blank line.
    @Test
    fun nestedList_afterBlankLine() {
        val expected = listOf(bullet("a"), MarkdownBlock.BlankLine, bullet("b", 1), MarkdownBlock.BlankLine, bullet("c"))
        assertEquals(expected, parse("- a\n\n  - b\n\n- c"))
        assertEquals(expected, parse("- a\n\n    - b\n\n- c"))
    }

    // A line after the blank lines that isn't an item ends the list there, and trailing blank lines are dropped.
    @Test
    fun list_endsBeforeBlankLines() {
        assertEquals(
            listOf(bullet("a"), MarkdownBlock.BlankLine, MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("after")))),
            parse("- a\n\nafter"),
        )
        assertEquals(listOf(bullet("a")), parse("- a\n\n"))
    }

    @Test
    fun list_indentedUpToThreeSpaces() {
        assertEquals(listOf(bullet("a"), numbered(2, "b")), parse("   - a\n 2. b"))
    }

    // With four spaces it's code, unless it's nested in the item above.
    @Test
    fun list_indentedFourSpacesIsCode() {
        assertEquals(listOf(MarkdownBlock.CodeBlock("- a")), parse("    - a"))
        assertEquals(
            listOf(MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("text"))), MarkdownBlock.CodeBlock("- a")),
            parse("text\n    - a"),
        )
    }

    @Test
    fun listItem_continuationLines() {
        val result = parse("- one\ntwo\n  three\n- four")
        assertEquals(
            listOf(
                MarkdownBlock.BulletListItem(listOf(MarkdownInline.Text("one two three")), 0),
                MarkdownBlock.BulletListItem(listOf(MarkdownInline.Text("four")), 0),
            ),
            result,
        )
    }

    @Test
    fun listItem_continuationThenSublist() {
        val result = parse("1. one\ntwo\n   - nested")
        assertEquals(
            listOf(
                MarkdownBlock.OrderedListItem(1, listOf(MarkdownInline.Text("one two")), 0),
                MarkdownBlock.BulletListItem(listOf(MarkdownInline.Text("nested")), 1),
            ),
            result,
        )
    }

    @Test
    fun listItem_continuationEndsAtBlankLineOrBlock() {
        val result = parse("- one\n> quote\n- two\n\nafter")
        assertEquals(
            listOf(
                MarkdownBlock.BulletListItem(listOf(MarkdownInline.Text("one")), 0),
                quote(paragraph("quote")),
                MarkdownBlock.BulletListItem(listOf(MarkdownInline.Text("two")), 0),
                MarkdownBlock.BlankLine,
                paragraph("after"),
            ),
            result,
        )
    }

    // ==================== Block quote ====================

    private fun paragraph(text: String) = MarkdownBlock.Paragraph(listOf(MarkdownInline.Text(text)))

    private fun quote(vararg children: MarkdownBlock) = MarkdownBlock.BlockQuote(children.toList())

    @Test
    fun blockQuote_lazyContinuation() {
        assertEquals(listOf(quote(paragraph("one two three"))), parse("> one\ntwo\nthree"))
    }

    @Test
    fun blockQuote_lazyContinuation_thenQuoteLinesAgain() {
        assertEquals(listOf(quote(paragraph("one two three"))), parse("> one\ntwo\n> three"))
    }

    @Test
    fun blockQuote_lazyContinuation_keepsHardBreak() {
        assertEquals(listOf(quote(paragraph("one\ntwo"))), parse("> one  \ntwo"))
    }

    @Test
    fun blockQuote_lazyContinuation_ofNestedQuoteAndListItem() {
        assertEquals(listOf(quote(quote(paragraph("one two")))), parse("> > one\ntwo"))
        assertEquals(listOf(quote(MarkdownBlock.BulletListItem(listOf(MarkdownInline.Text("one two")), 0))), parse("> - one\ntwo"))
    }

    @Test
    fun blockQuote_noLazyContinuation_afterBlankQuoteLine() {
        assertEquals(listOf(quote(paragraph("one")), paragraph("two")), parse("> one\n>\ntwo"))
    }

    @Test
    fun blockQuote_noLazyContinuation_afterHeadingOrCode() {
        assertEquals(listOf(quote(MarkdownBlock.Heading(1, listOf(MarkdownInline.Text("one")))), paragraph("two")), parse("> # one\ntwo"))
        assertEquals(listOf(quote(MarkdownBlock.CodeBlock("code")), paragraph("two")), parse(">     code\ntwo"))
        assertEquals(listOf(quote(MarkdownBlock.CodeBlock("code")), paragraph("two")), parse("> ```\n> code\ntwo"))
    }

    @Test
    fun blockQuote_lazyContinuation_endsAtBlock() {
        assertEquals(
            listOf(quote(paragraph("one")), MarkdownBlock.BulletListItem(listOf(MarkdownInline.Text("two")), 0)),
            parse("> one\n- two"),
        )
        assertEquals(listOf(quote(paragraph("one")), MarkdownBlock.Heading(1, listOf(MarkdownInline.Text("two")))), parse("> one\n# two"))
    }

    @Test
    fun blockQuote_lazyLine_isNotTableSeparator() {
        assertEquals(listOf(quote(paragraph("x a | b -|-"))), parse("> x\n> a | b\n-|-"))
    }

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

    @Test
    fun underline_afterLazyLineOfListItemOrQuote_isNotHeading() {
        // "more" continues the item or quote, and an underline can't, so --- is a rule and === is text.
        val item = MarkdownBlock.BulletListItem(listOf(MarkdownInline.Text("item more")), 0)
        assertEquals(listOf(item, MarkdownBlock.HorizontalRule), parse("- item\nmore\n---"))
        val numbered = MarkdownBlock.OrderedListItem(1, listOf(MarkdownInline.Text("item more")), 0)
        assertEquals(listOf(numbered, MarkdownBlock.HorizontalRule), parse("1. item\nmore\n---"))
        assertEquals(listOf(quote(paragraph("quote more")), MarkdownBlock.HorizontalRule), parse("> quote\nmore\n---"))
        assertEquals(listOf(quote(paragraph("quote more ==="))), parse("> quote\nmore\n==="))
    }
}
