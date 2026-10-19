package com.karmakameleon.shared.util.markdown

import com.karmakameleon.shared.domain.markdown.MarkdownBlock

private val horizontalRuleRegex = Regex("^(-{3,}|\\*{3,}|_{3,})$")
private val orderedListItemRegex = Regex("^(\\d+)[.)]\\s+(.*)")
private val tableSeparatorRegex = Regex("^\\|?\\s*[-:\\s|]+\\s*\\|?$")

fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    while (i < lines.size) {
        val parsed = parseBlockAt(lines, i)
        parsed.block?.let { blocks.add(it) }
        i = parsed.nextIndex
    }
    return blocks
}

// A parsed block (null when the lines produce none) and the index of the first line after it.
private class ParsedBlock(
    val block: MarkdownBlock?,
    val nextIndex: Int,
)

private fun parseBlockAt(
    lines: List<String>,
    i: Int,
): ParsedBlock {
    val line = lines[i]
    return when {
        isTableStart(lines, i) -> parseTableAt(lines, i)
        line.startsWith("#") -> ParsedBlock(parseHeading(line), i + 1)
        isQuoteLine(line) -> parseQuoteAt(lines, i)
        isCodeLine(line) -> ParsedBlock(MarkdownBlock.CodeBlock(line.substring(4)), i + 1)
        horizontalRuleRegex.matches(line) -> ParsedBlock(MarkdownBlock.HorizontalRule, i + 1)
        isBulletLine(line) -> ParsedBlock(MarkdownBlock.BulletListItem(parseInlineMarkdown("• " + line.substring(2))), i + 1)
        orderedListItemRegex.matches(line) -> ParsedBlock(parseOrderedListItem(line), i + 1)
        line.isNotBlank() -> parseParagraphAt(lines, i)
        else -> parseBlankLinesAt(lines, i)
    }
}

private fun isQuoteLine(line: String): Boolean = line.startsWith(">") && !line.startsWith(">!")

private fun isCodeLine(line: String): Boolean = line.startsWith("    ")

private fun isBulletLine(line: String): Boolean = line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")

// Lines that start a block of their own, so they end a paragraph.
private fun isSpecialLine(
    lines: List<String>,
    i: Int,
): Boolean {
    val line = lines[i]
    return line.startsWith("#") ||
        isQuoteLine(line) ||
        isCodeLine(line) ||
        horizontalRuleRegex.matches(line) ||
        isBulletLine(line) ||
        orderedListItemRegex.matches(line) ||
        isTableStart(lines, i)
}

private fun parseTableAt(
    lines: List<String>,
    i: Int,
): ParsedBlock {
    val tableEnd = findTableEnd(lines, i)
    return ParsedBlock(parseTable(lines.subList(i, tableEnd)), tableEnd)
}

// Up to six #s set the level; any more are part of the text.
private fun parseHeading(line: String): MarkdownBlock.Heading {
    val level = line.takeWhile { it == '#' }.length.coerceAtMost(6)
    return MarkdownBlock.Heading(level, parseInlineMarkdown(line.drop(level).trimStart()))
}

private fun parseQuoteAt(
    lines: List<String>,
    i: Int,
): ParsedBlock {
    val end = findBlockEnd(lines, i) { isQuoteLine(it) }
    val quoteText = lines.subList(i, end).joinToString("\n") { it.removePrefix(">").removePrefix(" ") }
    return ParsedBlock(MarkdownBlock.BlockQuote(parseMarkdownBlocks(quoteText)), end)
}

private fun parseOrderedListItem(line: String): MarkdownBlock? {
    val match = orderedListItemRegex.find(line) ?: return null
    return MarkdownBlock.OrderedListItem(match.groupValues[1].toInt(), parseInlineMarkdown(match.groupValues[2]))
}

// Consecutive plain lines form one paragraph. A line ending in two spaces is a line break; otherwise lines are
// joined with a space.
private fun parseParagraphAt(
    lines: List<String>,
    i: Int,
): ParsedBlock {
    var end = i + 1
    while (end < lines.size && lines[end].isNotBlank() && !isSpecialLine(lines, end)) end++
    val paragraphLines = lines.subList(i, end)
    val merged =
        buildString {
            paragraphLines.forEachIndexed { idx, l ->
                if (idx > 0) append(if (paragraphLines[idx - 1].endsWith("  ")) "\n" else " ")
                append(l.trimEnd())
            }
        }
    return ParsedBlock(MarkdownBlock.Paragraph(parseInlineMarkdown(merged)), end)
}

// Consecutive blank lines collapse into one BlankLine, and trailing ones are dropped.
private fun parseBlankLinesAt(
    lines: List<String>,
    i: Int,
): ParsedBlock {
    val end = findBlockEnd(lines, i) { it.isBlank() }
    return ParsedBlock(if (end < lines.size) MarkdownBlock.BlankLine else null, end)
}

// The index of the first line after start that doesn't match.
private inline fun findBlockEnd(
    lines: List<String>,
    start: Int,
    matches: (String) -> Boolean,
): Int {
    var end = start + 1
    while (end < lines.size && matches(lines[end])) end++
    return end
}

internal fun isTableStart(
    lines: List<String>,
    index: Int,
): Boolean {
    if (index + 1 >= lines.size) return false
    val currentLine = lines[index]
    val nextLine = lines[index + 1]
    return currentLine.contains("|") && tableSeparatorRegex.matches(nextLine)
}

internal fun findTableEnd(
    lines: List<String>,
    startIndex: Int,
): Int {
    var i = startIndex + 2
    while (i < lines.size && lines[i].contains("|")) {
        i++
    }
    return i
}

private fun parseTable(tableLines: List<String>): MarkdownBlock.Table {
    val header = parseTableRow(tableLines[0])
    val rows =
        tableLines
            .drop(2)
            .filter { !isTableSeparator(it) }
            .map { parseTableRow(it) }
    return MarkdownBlock.Table(header, rows)
}

// Leading and trailing pipes are optional, so an empty first or last cell isn't a cell.
internal fun parseTableRow(line: String): List<String> {
    val parts = line.split("|").map { it.trim() }
    return parts.filterIndexed { i, cell -> cell.isNotEmpty() || (i != 0 && i != parts.lastIndex) }
}

private fun isTableSeparator(line: String): Boolean = tableSeparatorRegex.matches(line)
