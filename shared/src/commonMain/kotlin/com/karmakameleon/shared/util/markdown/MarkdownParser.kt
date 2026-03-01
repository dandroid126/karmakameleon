package com.karmakameleon.shared.util.markdown

import com.karmakameleon.shared.domain.markdown.MarkdownBlock

fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    return parseMarkdownBlocks(markdown)
}
