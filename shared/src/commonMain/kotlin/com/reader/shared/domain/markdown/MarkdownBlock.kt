package com.reader.shared.domain.markdown

sealed class MarkdownBlock {
    data class Heading(val level: Int, val children: List<MarkdownInline>) : MarkdownBlock()
    data class Paragraph(val children: List<MarkdownInline>) : MarkdownBlock()
    data class BulletListItem(val children: List<MarkdownInline>) : MarkdownBlock()
    data class OrderedListItem(val number: Int, val children: List<MarkdownInline>) : MarkdownBlock()
    data class BlockQuote(val children: List<MarkdownBlock>) : MarkdownBlock()
    data class CodeBlock(val text: String) : MarkdownBlock()
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock()
    object HorizontalRule : MarkdownBlock()
    object BlankLine : MarkdownBlock()
}
