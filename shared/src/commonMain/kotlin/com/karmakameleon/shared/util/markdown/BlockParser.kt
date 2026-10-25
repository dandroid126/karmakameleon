package com.karmakameleon.shared.util.markdown

import com.karmakameleon.shared.domain.markdown.MarkdownBlock

// A line indented by four spaces is a code block.
private const val CODE_INDENT = "    "

// A code fence: up to three spaces, then three or more backticks or tildes, then an info string such as a language,
// which is ignored. A backtick fence's info string can't contain a backtick, so ```code``` on one line is inline code.
private val codeFenceRegex = Regex("^( {0,3})(`{3,}(?=[^`]*$)|~{3,})(.*)")

private val horizontalRuleRegex = Regex("^(-{3,}|\\*{3,}|_{3,})$")

// A list item: its indent, its marker (-, * or +, or a number followed by . or )), the spaces after the marker and
// its text.
private val listItemRegex = Regex("^( *)([-*+]|\\d{1,9}[.)])([ \t]+)(.*)")

// A list can be indented by up to three spaces; with four, it's a code block.
private const val MAX_LIST_INDENT = 3
private val tableSeparatorRegex = Regex("^\\|?\\s*[-:\\s|]+\\s*\\|?$")

fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    while (i < lines.size) {
        val parsed = parseBlockAt(lines, i)
        blocks.addAll(parsed.blocks)
        i = parsed.nextIndex
    }
    return blocks
}

// The blocks parsed from some lines and the index of the first line after them.
private class ParsedBlock(
    val blocks: List<MarkdownBlock>,
    val nextIndex: Int,
) {
    // One block, or none when it's null.
    constructor(block: MarkdownBlock?, nextIndex: Int) : this(listOfNotNull(block), nextIndex)
}

private fun parseBlockAt(
    lines: List<String>,
    i: Int,
): ParsedBlock {
    val line = lines[i]
    val codeFence = codeFenceRegex.matchEntire(line)
    return when {
        codeFence != null -> parseFencedCodeAt(lines, i, codeFence)
        isTableStart(lines, i) -> parseTableAt(lines, i)
        line.startsWith("#") -> ParsedBlock(parseHeading(line), i + 1)
        isQuoteLine(line) -> parseQuoteAt(lines, i)
        isCodeLine(line) -> ParsedBlock(MarkdownBlock.CodeBlock(line.removePrefix(CODE_INDENT)), i + 1)
        horizontalRuleRegex.matches(line) -> ParsedBlock(MarkdownBlock.HorizontalRule, i + 1)
        isListItemLine(line) -> parseListAt(lines, i)
        line.isNotBlank() -> parseParagraphAt(lines, i)
        else -> parseBlankLinesAt(lines, i)
    }
}

private fun isQuoteLine(line: String): Boolean = line.startsWith(">") && !line.startsWith(">!")

private fun isCodeLine(line: String): Boolean = line.startsWith(CODE_INDENT)

private fun isCodeFence(line: String): Boolean = codeFenceRegex.matches(line)

private fun isListItemLine(line: String): Boolean {
    val match = listItemRegex.matchEntire(line) ?: return false
    return match.groupValues[1].length <= MAX_LIST_INDENT
}

// Lines that start a block of their own, so they end a paragraph.
private fun isSpecialLine(
    lines: List<String>,
    i: Int,
): Boolean {
    val line = lines[i]
    return line.startsWith("#") ||
        isQuoteLine(line) ||
        isCodeLine(line) ||
        isCodeFence(line) ||
        horizontalRuleRegex.matches(line) ||
        isListItemLine(line) ||
        isTableStart(lines, i)
}

private fun parseTableAt(
    lines: List<String>,
    i: Int,
): ParsedBlock {
    val tableEnd = findTableEnd(lines, i)
    return ParsedBlock(parseTable(lines.subList(i, tableEnd)), tableEnd)
}

// The lines between an opening fence and a closing one, kept as typed. The closing fence uses the same character, at
// least as many times, so a longer fence can hold a shorter one. Without one, the code runs to the end of the text.
// Each line loses up to as many leading spaces as the opening fence is indented by.
private fun parseFencedCodeAt(
    lines: List<String>,
    i: Int,
    codeFence: MatchResult,
): ParsedBlock {
    val (indent, fence) = codeFence.destructured
    val closingFence = Regex("^ {0,3}${fence[0]}{${fence.length},}[ \t]*$")
    val end = (i + 1 until lines.size).firstOrNull { closingFence.matches(lines[it]) } ?: lines.size
    val code = lines.subList(i + 1, end).joinToString("\n") { it.dropLeadingSpaces(indent.length) }
    return ParsedBlock(MarkdownBlock.CodeBlock(code), minOf(end + 1, lines.size))
}

private fun String.dropLeadingSpaces(max: Int): String = drop(take(max).takeWhile { it == ' ' }.length)

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

// Consecutive list items, which blank lines can separate. Like on Reddit, an item indented at least as far as the
// text of an item above it is nested in that item, so a sublist needs two spaces under "- " and three under "1. ".
private fun parseListAt(
    lines: List<String>,
    i: Int,
): ParsedBlock {
    // Where the text starts in each item that the next item could be nested in, outermost first.
    val textColumns = ArrayDeque<Int>()
    val items = mutableListOf<MarkdownBlock>()
    var end = i
    var next = i
    var item = parseListItem(lines[i], textColumns)
    while (item != null) {
        if (next > end) items += MarkdownBlock.BlankLine
        items += item
        end = next + 1
        // Blank lines between items keep the list going, so a sublist after one is still nested.
        next = (end until lines.size).firstOrNull { lines[it].isNotBlank() } ?: lines.size
        item = lines.getOrNull(next)?.let { parseListItem(it, textColumns) }
    }
    return ParsedBlock(items, end)
}

// The list item on line, nested in the items whose text columns are given, which it updates. Null when the line
// isn't an item of the list.
private fun parseListItem(
    line: String,
    textColumns: ArrayDeque<Int>,
): MarkdownBlock? {
    val match = listItemRegex.matchEntire(line) ?: return null
    val (indent, marker, gap) = match.destructured
    while (textColumns.isNotEmpty() && indent.length < textColumns.last()) textColumns.removeLast()
    if (textColumns.isEmpty() && indent.length > MAX_LIST_INDENT) return null
    val level = textColumns.size
    textColumns.addLast(indent.length + marker.length + gap.length)
    val children = parseInlineMarkdown(match.groupValues.last())
    val number = marker.dropLast(1).toIntOrNull()
    return if (number != null) MarkdownBlock.OrderedListItem(number, children, level) else MarkdownBlock.BulletListItem(children, level)
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
