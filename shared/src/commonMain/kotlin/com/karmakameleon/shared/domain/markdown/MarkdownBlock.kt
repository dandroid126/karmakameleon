package com.karmakameleon.shared.domain.markdown

sealed interface MarkdownBlock {
    data class Heading(
        val level: Int,
        val children: List<MarkdownInline>,
    ) : MarkdownBlock

    data class Paragraph(
        val children: List<MarkdownInline>,
    ) : MarkdownBlock

    // level is how deeply the item is nested in other list items: 0 for an item of a top-level list.
    data class BulletListItem(
        val children: List<MarkdownInline>,
        val level: Int = 0,
    ) : MarkdownBlock

    data class OrderedListItem(
        val number: Int,
        val children: List<MarkdownInline>,
        val level: Int = 0,
    ) : MarkdownBlock

    data class BlockQuote(
        val children: List<MarkdownBlock>,
    ) : MarkdownBlock

    data class CodeBlock(
        val text: String,
    ) : MarkdownBlock

    // A row can have fewer or more cells than the header. alignments has each column's alignment, from the separator
    // row, and can have fewer or more entries than there are columns; a column without one has the default alignment.
    data class Table(
        val header: List<Cell>,
        val rows: List<List<Cell>>,
        val alignments: List<TableAlignment> = emptyList(),
    ) : MarkdownBlock {
        data class Cell(
            val children: List<MarkdownInline>,
        )
    }

    object HorizontalRule : MarkdownBlock

    object BlankLine : MarkdownBlock
}
