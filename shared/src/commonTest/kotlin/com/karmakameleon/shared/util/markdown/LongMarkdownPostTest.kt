package com.karmakameleon.shared.util.markdown

import com.karmakameleon.shared.domain.markdown.MarkdownBlock
import com.karmakameleon.shared.domain.markdown.MarkdownInline
import com.karmakameleon.shared.testing.readFixture
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The long post to measure markdown performance with (#121). It's made-up text in the shape of r/gaming's Phantom
 * Blade Zero review thread, which was slow to open: about 36 KB, mostly in 53 quotes (20 of them 900–1,400
 * characters) that follow short paragraphs, plus 2 headings, 4 list items and 70 links. The real thread quotes
 * published reviews, so it isn't saved here, and it can change; this text stays the same between measurements.
 */
class LongMarkdownPostTest {
    private val blocks = parseMarkdownBlocks(readFixture("long_markdown_post.md"))

    @Test
    fun hasTheMeasuredThreadsBlocks() {
        assertEquals(2, blocks.count { it is MarkdownBlock.Heading })
        assertEquals(59, blocks.count { it is MarkdownBlock.Paragraph })
        assertEquals(53, blocks.count { it is MarkdownBlock.BlockQuote })
        assertEquals(4, blocks.count { it is MarkdownBlock.BulletListItem })
    }

    @Test
    fun hasTheMeasuredThreadsLinks() {
        val inlines =
            blocks.flatMap { block ->
                when (block) {
                    is MarkdownBlock.BlockQuote -> block.children.flatMap { (it as? MarkdownBlock.Paragraph)?.children.orEmpty() }
                    is MarkdownBlock.Paragraph -> block.children
                    is MarkdownBlock.BulletListItem -> block.children
                    else -> emptyList()
                }
            }
        assertEquals(70, inlines.count { it is MarkdownInline.Link })
    }
}
