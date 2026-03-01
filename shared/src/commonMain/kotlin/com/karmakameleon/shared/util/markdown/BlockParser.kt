package com.karmakameleon.shared.util.markdown

import com.karmakameleon.shared.domain.markdown.MarkdownBlock

fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        when {
            isTableStart(lines, i) -> {
                val tableEndIdx = findTableEnd(lines, i)
                val tableLines = lines.subList(i, tableEndIdx)
                blocks.add(parseTable(tableLines))
                i = tableEndIdx
                continue
            }
            line.startsWith("######") -> {
                blocks.add(MarkdownBlock.Heading(6, parseInlineMarkdown(line.removePrefix("######").trimStart())))
            }
            line.startsWith("#####") -> {
                blocks.add(MarkdownBlock.Heading(5, parseInlineMarkdown(line.removePrefix("#####").trimStart())))
            }
            line.startsWith("####") -> {
                blocks.add(MarkdownBlock.Heading(4, parseInlineMarkdown(line.removePrefix("####").trimStart())))
            }
            line.startsWith("###") -> {
                blocks.add(MarkdownBlock.Heading(3, parseInlineMarkdown(line.removePrefix("###").trimStart())))
            }
            line.startsWith("##") -> {
                blocks.add(MarkdownBlock.Heading(2, parseInlineMarkdown(line.removePrefix("##").trimStart())))
            }
            line.startsWith("#") -> {
                blocks.add(MarkdownBlock.Heading(1, parseInlineMarkdown(line.removePrefix("#").trimStart())))
            }
            line.startsWith(">") && !line.startsWith(">!") -> {
                val quoteLines = mutableListOf(line.removePrefix(">").removePrefix(" "))
                while (i + 1 < lines.size && lines[i + 1].startsWith(">") && !lines[i + 1].startsWith(">!")) {
                    i++
                    quoteLines.add(lines[i].removePrefix(">").removePrefix(" "))
                }
                blocks.add(MarkdownBlock.BlockQuote(parseMarkdownBlocks(quoteLines.joinToString("\n"))))
            }
            line.startsWith("    ") -> {
                blocks.add(MarkdownBlock.CodeBlock(line.substring(4)))
            }
            line.matches(Regex("^-{3,}$")) || line.matches(Regex("^\\*{3,}$")) || line.matches(Regex("^_{3,}$")) -> {
                blocks.add(MarkdownBlock.HorizontalRule)
            }
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> {
                blocks.add(MarkdownBlock.BulletListItem(parseInlineMarkdown("• " + line.substring(2))))
            }
            line.matches(Regex("^\\d+[.)][\\s]+.*")) -> {
                val match = Regex("^(\\d+)[.)]\\s+(.*)").find(line)
                if (match != null) {
                    blocks.add(
                        MarkdownBlock.OrderedListItem(
                            match.groupValues[1].toInt(),
                            parseInlineMarkdown(match.groupValues[2])
                        )
                    )
                }
            }
            line.isNotBlank() -> {
                val paragraphLines = mutableListOf(line)
                while (i + 1 < lines.size && lines[i + 1].isNotBlank() && !isSpecialLine(lines, i + 1)) {
                    i++
                    paragraphLines.add(lines[i])
                }
                val merged = buildString {
                    paragraphLines.forEachIndexed { idx, l ->
                        if (idx > 0) {
                            if (paragraphLines[idx - 1].endsWith("  ")) append("\n") else append(" ")
                        }
                        append(l.trimEnd())
                    }
                }
                blocks.add(MarkdownBlock.Paragraph(parseInlineMarkdown(merged)))
            }
            else -> {
                // Collapse consecutive blank lines into one BlankLine
                while (i + 1 < lines.size && lines[i + 1].isBlank()) {
                    i++
                }
                if (i + 1 < lines.size) {
                    blocks.add(MarkdownBlock.BlankLine)
                }
            }
        }
        i++
    }

    return blocks
}

private fun isSpecialLine(lines: List<String>, i: Int): Boolean {
    val line = lines[i]
    return line.startsWith("#") ||
        (line.startsWith(">") && !line.startsWith(">!")) ||
        line.startsWith("    ") ||
        line.matches(Regex("^-{3,}$")) || line.matches(Regex("^\\*{3,}$")) || line.matches(Regex("^_{3,}$")) ||
        line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") ||
        line.matches(Regex("^\\d+[.)]\\s+.*")) ||
        isTableStart(lines, i)
}

internal fun isTableStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    val currentLine = lines[index]
    val nextLine = lines[index + 1]
    return currentLine.contains("|") && nextLine.matches(Regex("^\\|?\\s*[-:\\s|]+\\s*\\|?$"))
}

internal fun findTableEnd(lines: List<String>, startIndex: Int): Int {
    var i = startIndex + 2
    while (i < lines.size && lines[i].contains("|")) {
        i++
    }
    return i
}

private fun parseTable(tableLines: List<String>): MarkdownBlock.Table {
    val header = parseTableRow(tableLines[0])
    val rows = tableLines.drop(2)
        .filter { !isTableSeparator(it) }
        .map { parseTableRow(it) }
    return MarkdownBlock.Table(header, rows)
}

internal fun parseTableRow(line: String): List<String> {
    val cells = mutableListOf<String>()
    val parts = line.split("|")
    for (i in parts.indices) {
        val trimmed = parts[i].trim()
        if (i == 0 && trimmed.isEmpty()) continue
        if (i == parts.size - 1 && trimmed.isEmpty()) continue
        cells.add(trimmed)
    }
    return cells
}

private fun isTableSeparator(line: String): Boolean {
    return line.matches(Regex("^\\|?\\s*[-:\\s|]+\\s*\\|?$"))
}
