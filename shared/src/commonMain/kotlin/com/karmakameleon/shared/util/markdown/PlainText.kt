package com.karmakameleon.shared.util.markdown

import com.karmakameleon.shared.domain.markdown.MarkdownBlock
import com.karmakameleon.shared.domain.markdown.MarkdownInline

// A post card's preview shows a few lines, so only the start of the text is parsed. It's parsed while the card is
// drawn, so this keeps a long post from making the feed stutter.
private const val PREVIEW_SOURCE_CHARS = 1000

// Stands in for a spoiler, which a preview can't reveal.
internal const val HIDDEN_SPOILER = "[spoiler]"

/**
 * The start of [markdown] as plain text, for a post card's preview: the text of each block on its own line, without
 * the markdown syntax (emphasis, heading and list markers, link URLs and the like). Spoilers stay hidden, and images,
 * horizontal rules and blank lines are left out (#87).
 */
fun markdownPreviewText(markdown: String): String =
    parseMarkdownBlocks(markdown.take(PREVIEW_SOURCE_CHARS))
        .mapNotNull { it.plainText()?.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

private fun MarkdownBlock.plainText(): String? =
    when (this) {
        is MarkdownBlock.Heading -> children.plainText()
        is MarkdownBlock.Paragraph -> children.plainText()
        is MarkdownBlock.BulletListItem -> children.plainText()
        is MarkdownBlock.OrderedListItem -> children.plainText()
        is MarkdownBlock.BlockQuote -> children.mapNotNull { it.plainText() }.joinToString("\n")
        is MarkdownBlock.CodeBlock -> text
        is MarkdownBlock.Table -> (listOf(header) + rows).joinToString("\n") { row -> row.joinToString("  ") { it.children.plainText() } }
        MarkdownBlock.HorizontalRule, MarkdownBlock.BlankLine -> null
    }

private fun List<MarkdownInline>.plainText(): String = joinToString("") { it.plainText() }

private fun MarkdownInline.plainText(): String =
    when (this) {
        is MarkdownInline.Text -> text
        is MarkdownInline.Code -> text
        is MarkdownInline.Bold -> children.plainText()
        is MarkdownInline.Italic -> children.plainText()
        is MarkdownInline.BoldItalic -> children.plainText()
        is MarkdownInline.Strikethrough -> children.plainText()
        is MarkdownInline.Superscript -> children.plainText()
        is MarkdownInline.Link -> text.plainText()
        is MarkdownInline.Spoiler -> HIDDEN_SPOILER
        is MarkdownInline.Image -> ""
    }
