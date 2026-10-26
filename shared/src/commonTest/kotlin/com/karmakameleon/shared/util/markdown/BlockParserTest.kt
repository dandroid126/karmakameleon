package com.karmakameleon.shared.util.markdown

import com.karmakameleon.shared.domain.markdown.MarkdownBlock
import com.karmakameleon.shared.domain.markdown.MarkdownInline
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

    @Test
    fun underlinedHeading_equalsIsLevel1() {
        val result = parse("Heading 1\n===")
        assertEquals(listOf(MarkdownBlock.Heading(1, listOf(MarkdownInline.Text("Heading 1")))), result)
    }

    @Test
    fun underlinedHeading_dashesIsLevel2NotRule() {
        val result = parse("Heading 2\n---")
        assertEquals(listOf(MarkdownBlock.Heading(2, listOf(MarkdownInline.Text("Heading 2")))), result)
    }

    @Test
    fun underlinedHeading_anyLengthAndIndentUpToThree() {
        val result = parse("One\n=\n\nTwo\n   --  ")
        assertEquals(
            listOf(
                MarkdownBlock.Heading(1, listOf(MarkdownInline.Text("One"))),
                MarkdownBlock.BlankLine,
                MarkdownBlock.Heading(2, listOf(MarkdownInline.Text("Two"))),
            ),
            result,
        )
    }

    @Test
    fun underlinedHeading_takesTheWholeParagraph() {
        val result = parse("First line\nsecond *line*\n===\nAfter")
        assertEquals(
            listOf(
                MarkdownBlock.Heading(
                    1,
                    listOf(MarkdownInline.Text("First line second "), MarkdownInline.Italic(listOf(MarkdownInline.Text("line")))),
                ),
                MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("After"))),
            ),
            result,
        )
    }

    @Test
    fun underline_withoutParagraphAbove() {
        // With a blank line above, === is text and --- a horizontal rule.
        val result = parse("Text\n\n===\n\n---")
        assertEquals(
            listOf(
                MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("Text"))),
                MarkdownBlock.BlankLine,
                MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("==="))),
                MarkdownBlock.BlankLine,
                MarkdownBlock.HorizontalRule,
            ),
            result,
        )
    }

    @Test
    fun underline_afterListItemIsRule() {
        val result = parse("- item\n---")
        assertEquals(listOf(MarkdownBlock.BulletListItem(listOf(MarkdownInline.Text("item")), 0), MarkdownBlock.HorizontalRule), result)
    }

    @Test
    fun underline_afterLazyLineOfListItemOrQuote_isNotHeading() {
        // On Reddit, "more" continues the item or quote, and an underline can't, so --- is a rule and === is text.
        val more = MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("more")))
        val item = MarkdownBlock.BulletListItem(listOf(MarkdownInline.Text("item")), 0)
        assertEquals(listOf(item, more, MarkdownBlock.HorizontalRule), parse("- item\nmore\n---"))
        val numbered = MarkdownBlock.OrderedListItem(1, listOf(MarkdownInline.Text("item")), 0)
        assertEquals(listOf(numbered, more, MarkdownBlock.HorizontalRule), parse("1. item\nmore\n---"))
        val quote = MarkdownBlock.BlockQuote(listOf(MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("quote")))))
        assertEquals(listOf(quote, more, MarkdownBlock.HorizontalRule), parse("> quote\nmore\n---"))
        assertEquals(listOf(quote, MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("more ===")))), parse("> quote\nmore\n==="))
    }

    @Test
    fun underlinedHeading_afterBlankLineBelowListItem() {
        val result = parse("- item\n\nHeading\n---")
        assertEquals(MarkdownBlock.Heading(2, listOf(MarkdownInline.Text("Heading"))), result.last())
    }

    @Test
    fun underlinedHeading_inQuote() {
        val result = parse("> Quoted\n> ===")
        assertEquals(
            listOf(MarkdownBlock.BlockQuote(listOf(MarkdownBlock.Heading(1, listOf(MarkdownInline.Text("Quoted")))))),
            result,
        )
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

    @Test
    fun fencedCode_backticks_keepsLinesAndIndent() {
        assertEquals(listOf(MarkdownBlock.CodeBlock("fenced code\n  indented line")), parse("```\nfenced code\n  indented line\n```"))
    }

    @Test
    fun fencedCode_tildes() {
        assertEquals(listOf(MarkdownBlock.CodeBlock("code")), parse("~~~\ncode\n~~~"))
    }

    @Test
    fun fencedCode_noMarkdownInside() {
        val result = parse("```\n# not a heading\n- not a list\n\n**not bold**\n```")
        assertEquals(listOf(MarkdownBlock.CodeBlock("# not a heading\n- not a list\n\n**not bold**")), result)
    }

    @Test
    fun fencedCode_infoStringIgnored() {
        assertEquals(listOf(MarkdownBlock.CodeBlock("val x = 1")), parse("```kotlin\nval x = 1\n```"))
    }

    @Test
    fun fencedCode_longerFenceHoldsShorterOne() {
        assertEquals(listOf(MarkdownBlock.CodeBlock("```\ninner\n```")), parse("````\n```\ninner\n```\n````"))
    }

    @Test
    fun fencedCode_otherCharacterDoesNotClose() {
        assertEquals(listOf(MarkdownBlock.CodeBlock("~~~\nstill code")), parse("```\n~~~\nstill code\n```"))
    }

    @Test
    fun fencedCode_withoutClose_runsToEnd() {
        assertEquals(listOf(MarkdownBlock.CodeBlock("code\n\nmore")), parse("```\ncode\n\nmore"))
    }

    @Test
    fun fencedCode_indentedFence_dropsThatIndentFromLines() {
        assertEquals(listOf(MarkdownBlock.CodeBlock("a\n b\nc")), parse("  ```\n  a\n   b\nc\n  ```"))
    }

    @Test
    fun fencedCode_interruptsParagraph_andTextFollows() {
        val result = parse("before\n```\ncode\n```\nafter")
        assertEquals(
            listOf(
                MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("before"))),
                MarkdownBlock.CodeBlock("code"),
                MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("after"))),
            ),
            result,
        )
    }

    @Test
    fun fencedCode_inQuote() {
        val quote = assertIs<MarkdownBlock.BlockQuote>(parse("> ```\n> a\n>   b\n> ```").single())
        assertEquals(listOf(MarkdownBlock.CodeBlock("a\n  b")), quote.children)
    }

    // A backtick fence's info string can't hold a backtick, so this is inline code, which the inline parser handles.
    @Test
    fun backticksOnOneLine_areNotAFence() {
        assertIs<MarkdownBlock.Paragraph>(parse("```code```").single())
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

    @Test
    fun table_escapedPipe_staysInCell() {
        val table = assertIs<MarkdownBlock.Table>(parse("| A | B |\n|---|---|\n| a \\| pipe | ok |").single())
        assertEquals(listOf(listOf("a | pipe", "ok")), table.rows)
    }

    @Test
    fun table_escapedPipe_inHeaderAndWithoutOuterPipes() {
        val table = assertIs<MarkdownBlock.Table>(parse("x \\| y | z\n--|--\n1 | 2").single())
        assertEquals(listOf("x | y", "z"), table.header)
    }

    @Test
    fun table_escapedPipe_inInlineCode() {
        val table = assertIs<MarkdownBlock.Table>(parse("| A |\n|---|\n| `a \\| b` |").single())
        assertEquals(listOf(listOf("`a | b`")), table.rows)
    }

    // In \\| the backslash is escaped, so the pipe separates cells; the inline parser turns \\ into one backslash.
    @Test
    fun table_escapedBackslashBeforePipe_separatesCells() {
        assertEquals(listOf("C:\\\\", "D"), parseTableRow("| C:\\\\| D |"))
        assertEquals(listOf("a\\\\|b"), parseTableRow("| a\\\\\\|b |"))
        assertEquals(listOf("C:\\\\", "D"), parseTableRow("| C:\\\\ | D |"))
    }

    @Test
    fun table_lineWithOnlyEscapedPipes_endsTable() {
        val result = parse("| A |\n|---|\n| 1 |\nuse a \\| b")
        assertEquals(listOf(listOf("1")), assertIs<MarkdownBlock.Table>(result[0]).rows)
        assertIs<MarkdownBlock.Paragraph>(result[1])
    }

    @Test
    fun table_lineWithOnlyEscapedPipes_doesNotStartTable() {
        // Without a table, the --- underlines a heading.
        val result = parse("x \\| y\n---")
        assertEquals(
            MarkdownBlock.Heading(2, listOf(MarkdownInline.Text("x "), MarkdownInline.Text("|"), MarkdownInline.Text(" y"))),
            result.single(),
        )
    }

    // ==================== Link definitions ====================

    @Test
    fun linkDefinition_laterInText_isUsedAndHidden() {
        val md =
            "Read [cursus lectus][cl] or [Lorem ipsum].\n\n" +
                "[cl]: https://example.com/cl\n[lorem ipsum]: <https://example.com/li> \"Title\""
        val paragraph = assertIs<MarkdownBlock.Paragraph>(parse(md).single())
        val links = paragraph.children.filterIsInstance<MarkdownInline.Link>()
        assertEquals(listOf("https://example.com/cl", "https://example.com/li"), links.map { it.url })
    }

    @Test
    fun linkDefinition_firstOneWins() {
        val paragraph = assertIs<MarkdownBlock.Paragraph>(parse("[a]\n\n[a]: https://one.example\n[A]: https://two.example").first())
        assertEquals("https://one.example", assertIs<MarkdownInline.Link>(paragraph.children.single()).url)
    }

    // Definitions between paragraphs leave one blank line, at the end they're dropped, and alone they add no space.
    @Test
    fun linkDefinition_countsAsBlankLine() {
        val result = parse("one\n\n[a]: https://example.com\n\ntwo\n[a]: https://example.com")
        assertEquals(3, result.size)
        assertIs<MarkdownBlock.BlankLine>(result[1])
        assertEquals(listOf<MarkdownBlock>(MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("one")))), parse("one\n\n[a]: /x\n"))
        assertEquals(listOf<MarkdownBlock>(MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("two")))), parse("[a]: /x\ntwo"))
    }

    // Like trailing ones, definitions at the start leave no gap, in a quote too.
    @Test
    fun linkDefinition_atStart_addsNoBlankLine() {
        val two = MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("two")))
        assertEquals(listOf<MarkdownBlock>(two), parse("[a]: /x\n\ntwo"))
        assertEquals(listOf<MarkdownBlock>(MarkdownBlock.BlockQuote(listOf(two))), parse("> [a]: /x\n>\n> two"))
    }

    @Test
    fun linkDefinition_underParagraphLine_isText() {
        val paragraph = assertIs<MarkdownBlock.Paragraph>(parse("text\n[a]: https://example.com").single())
        assertEquals("text [a]: ", (paragraph.children[0] as MarkdownInline.Text).text)
    }

    @Test
    fun linkDefinition_inQuote_isUsedOutside() {
        val result = parse("[a]\n\n> [a]: https://example.com")
        val paragraph = assertIs<MarkdownBlock.Paragraph>(result[0])
        assertEquals("https://example.com", assertIs<MarkdownInline.Link>(paragraph.children.single()).url)
        assertEquals(MarkdownBlock.BlockQuote(emptyList()), result[2])
    }

    @Test
    fun linkDefinition_usedInListsHeadingsAndTables() {
        val result = parse("# [a]\n- [a]\n\n| [a] |\n|---|\n\n[a]: https://example.com")
        val headingLink = assertIs<MarkdownBlock.Heading>(result[0]).children.single()
        val itemLink = assertIs<MarkdownBlock.BulletListItem>(result[1]).children.single()
        assertEquals("https://example.com", assertIs<MarkdownInline.Link>(headingLink).url)
        assertEquals("https://example.com", assertIs<MarkdownInline.Link>(itemLink).url)
        assertEquals(mapOf("a" to "https://example.com"), assertIs<MarkdownBlock.Table>(result[3]).references)
    }

    @Test
    fun linkDefinition_inCode_isCode() {
        val result = parse("    [a]: https://example.com\n```\n[b]: https://example.com\n```\n[a] [b]")
        assertEquals(MarkdownBlock.CodeBlock("[a]: https://example.com"), result[0])
        assertEquals(MarkdownBlock.CodeBlock("[b]: https://example.com"), result[1])
        assertEquals(MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("[a] [b]"))), result[2])
    }

    @Test
    fun linkDefinition_withTextAfterUrl_isText() {
        val paragraph = assertIs<MarkdownBlock.Paragraph>(parse("[a]: https://example.com more words").single())
        assertEquals(MarkdownInline.Text("[a]: "), paragraph.children[0])
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
