package com.karmakameleon.shared.util.markdown

import com.karmakameleon.shared.domain.markdown.MarkdownBlock
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class QuoteTailTest {
    // Lines that start, continue or end blocks in different ways, including nested quotes. Link definitions are left
    // out: one after a blank line adds no block, so parsing still ends with the paragraph above, which a lazy line
    // mustn't continue. ListAndQuoteParserTest covers them.
    private val lineKinds =
        listOf(
            "text",
            "  text",
            "a | b",
            "| x |",
            "--|--",
            "===",
            "---",
            "--",
            "-",
            "***",
            "- item",
            "  - two",
            "1. item",
            "1.     wide",
            "- a | b",
            "    code",
            "     - five",
            "      - deep",
            "```",
            "~~~",
            "    ```",
            "# heading",
            "",
            "  ",
            ">",
            "> quote",
            "> > deeper",
            "> > > x",
            "> ```",
            "> - item",
            "> # h",
            "> a | b",
            ">!spoiler!<",
        )

    @Test
    fun endsWithParagraph_agreesWithParsing_forAllShortQuotes() {
        for (size in 1..3) {
            combinations(size).forEach(::assertAgrees)
        }
    }

    @Test
    fun endsWithParagraph_agreesWithParsing_forLongerQuotes() {
        val random = Random(seed = 1)
        repeat(20_000) {
            assertAgrees(List(random.nextInt(4, 9)) { lineKinds.random(random) })
        }
    }

    private fun assertAgrees(lines: List<String>) {
        val tail = QuoteTail()
        lines.forEach { tail.add(it, lazy = false) }
        // Parsing drops blank lines at the end, even in a nested quote, but they end the paragraph.
        val endsWithText = lines.last().trimStart('>', ' ').isNotEmpty()
        val parsed = endsWithText && endsWithParagraph(parseMarkdownBlocks(lines.joinToString("\n")))
        assertEquals(parsed, tail.endsWithParagraph, lines.toString())
    }

    private fun combinations(size: Int): List<List<String>> =
        if (size == 0) listOf(emptyList()) else combinations(size - 1).flatMap { start -> lineKinds.map { start + it } }

    private fun endsWithParagraph(blocks: List<MarkdownBlock>): Boolean =
        when (val last = blocks.lastOrNull()) {
            is MarkdownBlock.Paragraph, is MarkdownBlock.BulletListItem, is MarkdownBlock.OrderedListItem -> true
            is MarkdownBlock.BlockQuote -> endsWithParagraph(last.children)
            else -> false
        }
}
