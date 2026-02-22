package com.reader.shared.util.markdown

import com.reader.shared.domain.markdown.MarkdownBlock

fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    return parseMarkdownBlocks(markdown)
}
