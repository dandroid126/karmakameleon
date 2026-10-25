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

    data class Table(
        val header: List<String>,
        val rows: List<List<String>>,
    ) : MarkdownBlock

    object HorizontalRule : MarkdownBlock

    object BlankLine : MarkdownBlock
}
