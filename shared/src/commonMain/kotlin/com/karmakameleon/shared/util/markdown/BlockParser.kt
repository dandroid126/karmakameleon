package com.karmakameleon.shared.util.markdown

import com.karmakameleon.shared.domain.markdown.MarkdownBlock
import com.karmakameleon.shared.domain.markdown.MarkdownInline

// A line indented by four spaces is a code block.
private const val CODE_INDENT = "    "

// A code fence: up to three spaces, then three or more backticks or tildes, then an info string such as a language,
// which is ignored. A backtick fence's info string can't contain a backtick, so ```code``` on one line is inline code.
private val codeFenceRegex = Regex("^( {0,3})(`{3,}(?=[^`]*$)|~{3,})(.*)")

private val horizontalRuleRegex = Regex("^(-{3,}|\\*{3,}|_{3,})$")

// A line of =s under a paragraph makes it a level 1 heading, and one of -s a level 2 heading. It can be indented by up
// to three spaces, and any length counts.
private val headingUnderlineRegex = Regex("^ {0,3}(=+|-+)[ \t]*$")

// A list item: its indent, its marker (-, * or +, or a number followed by . or )), the spaces after the marker and
// its text.
private val listItemRegex = Regex("^( *)([-*+]|\\d{1,9}[.)])([ \t]+)(.*)")

// A list can be indented by up to three spaces; with four, it's a code block.
private const val MAX_LIST_INDENT = 3

private val tableSeparatorRegex = Regex("^\\|?\\s*[-:\\s|]+\\s*\\|?$")

// A reference link's definition: up to three spaces, [label]: and the URL, optionally in angle brackets, then
// optionally a title in double quotes, single quotes or parentheses, which is a tooltip on Reddit, so it's dropped.
private val linkDefinitionRegex =
    Regex("""^ {0,3}\[([^\]]*\S[^\]]*)]:[ \t]*<?([^\s<>]+)>?(?:[ \t]+(?:"[^"]*"|'[^']*'|\([^)]*\)))?[ \t]*$""")

fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.split("\n")
    val firstPass = LinkReferences(known = emptyMap())
    val blocks = parseBlocks(lines, firstPass)
    // A link can use a definition further down, so with any definitions the text is parsed again to link them.
    return if (firstPass.found.isEmpty()) blocks else parseBlocks(lines, LinkReferences(known = firstPass.found))
}

// The URLs of reference links, by normalized label: the known ones, which links use, and the ones found while
// parsing. Like on Reddit, the first definition of a label wins.
private class LinkReferences(
    val known: Map<String, String>,
) {
    val found = mutableMapOf<String, String>()

    fun inlines(text: String): List<MarkdownInline> = parseInlineMarkdown(text, known)
}

private fun parseBlocks(
    lines: List<String>,
    references: LinkReferences,
): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    while (i < lines.size) {
        val parsed = parseBlockAt(lines, i, references, blocks.lastOrNull())
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
    references: LinkReferences,
    previous: MarkdownBlock?,
): ParsedBlock {
    val line = lines[i]
    val codeFence = codeFenceRegex.matchEntire(line)
    return when {
        codeFence != null -> parseFencedCodeAt(lines, i, codeFence)
        isTableStart(lines, i) -> parseTableAt(lines, i, references)
        line.startsWith("#") -> ParsedBlock(parseHeading(line, references), i + 1)
        isQuoteLine(line) -> parseQuoteAt(lines, i, references)
        isCodeLine(line) -> ParsedBlock(MarkdownBlock.CodeBlock(line.removePrefix(CODE_INDENT)), i + 1)
        horizontalRuleRegex.matches(line) -> ParsedBlock(MarkdownBlock.HorizontalRule, i + 1)
        isListItemLine(line) -> parseListAt(lines, i, references)
        line.isNotBlank() && !linkDefinitionRegex.matches(line) -> parseParagraphAt(lines, i, references, previous)
        else -> parseBlankLinesAt(lines, i, references)
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
    references: LinkReferences,
): ParsedBlock {
    val tableEnd = findTableEnd(lines, i)
    return ParsedBlock(parseTable(lines.subList(i, tableEnd), references.known), tableEnd)
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
private fun parseHeading(
    line: String,
    references: LinkReferences,
): MarkdownBlock.Heading {
    val level = line.takeWhile { it == '#' }.length.coerceAtMost(6)
    return MarkdownBlock.Heading(level, references.inlines(line.drop(level).trimStart()))
}

// Definitions inside a quote can be used anywhere, so the quote's text shares the references of the text around it.
private fun parseQuoteAt(
    lines: List<String>,
    i: Int,
    references: LinkReferences,
): ParsedBlock {
    val end = findBlockEnd(lines, i) { isQuoteLine(it) }
    val quoteLines = lines.subList(i, end).map { it.removePrefix(">").removePrefix(" ") }
    return ParsedBlock(MarkdownBlock.BlockQuote(parseBlocks(quoteLines, references)), end)
}

// Consecutive list items, which blank lines can separate. Like on Reddit, an item indented at least as far as the
// text of an item above it is nested in that item, so a sublist needs two spaces under "- " and three under "1. ".
private fun parseListAt(
    lines: List<String>,
    i: Int,
    references: LinkReferences,
): ParsedBlock {
    // Where the text starts in each item that the next item could be nested in, outermost first.
    val textColumns = ArrayDeque<Int>()
    val items = mutableListOf<MarkdownBlock>()
    var end = i
    var next = i
    var item = parseListItem(lines[i], textColumns, references)
    while (item != null) {
        if (next > end) items += MarkdownBlock.BlankLine
        items += item
        end = next + 1
        // Blank lines between items keep the list going, so a sublist after one is still nested.
        next = (end until lines.size).firstOrNull { lines[it].isNotBlank() } ?: lines.size
        item = lines.getOrNull(next)?.let { parseListItem(it, textColumns, references) }
    }
    return ParsedBlock(items, end)
}

// The list item on line, nested in the items whose text columns are given, which it updates. Null when the line
// isn't an item of the list.
private fun parseListItem(
    line: String,
    textColumns: ArrayDeque<Int>,
    references: LinkReferences,
): MarkdownBlock? {
    val match = listItemRegex.matchEntire(line) ?: return null
    val (indent, marker, gap) = match.destructured
    while (textColumns.isNotEmpty() && indent.length < textColumns.last()) textColumns.removeLast()
    if (textColumns.isEmpty() && indent.length > MAX_LIST_INDENT) return null
    val level = textColumns.size
    textColumns.addLast(indent.length + marker.length + gap.length)
    val children = references.inlines(match.groupValues.last())
    val number = marker.dropLast(1).toIntOrNull()
    return if (number != null) MarkdownBlock.OrderedListItem(number, children, level) else MarkdownBlock.BulletListItem(children, level)
}

// Consecutive plain lines form one paragraph. A line ending in two spaces is a line break; otherwise lines are
// joined with a space. As in CommonMark, a link definition can't interrupt a paragraph, so one on the paragraph's
// next line is text. With a heading underline after it, the paragraph is a heading instead, even when the underline is
// --- and would otherwise be a horizontal rule.
//
// Right after a list item or quote, with no blank line between, Reddit continues the item or quote with the paragraph
// (a lazy continuation), and an underline can't continue one, so --- is a rule and === is text. Lazy continuation isn't
// supported yet (#83), but the underline is still left out there, so it doesn't show a heading Reddit doesn't.
private fun parseParagraphAt(
    lines: List<String>,
    i: Int,
    references: LinkReferences,
    previous: MarkdownBlock?,
): ParsedBlock {
    val canHaveUnderline =
        previous !is MarkdownBlock.BulletListItem && previous !is MarkdownBlock.OrderedListItem && previous !is MarkdownBlock.BlockQuote
    var end = i + 1
    var underline: MatchResult? = null
    while (end < lines.size && lines[end].isNotBlank()) {
        underline = if (canHaveUnderline) headingUnderlineRegex.matchEntire(lines[end]) else null
        if (underline != null || isSpecialLine(lines, end)) break
        end++
    }
    val paragraphLines = lines.subList(i, end)
    val merged =
        buildString {
            paragraphLines.forEachIndexed { idx, l ->
                if (idx > 0) append(if (paragraphLines[idx - 1].endsWith("  ")) "\n" else " ")
                append(l.trimEnd())
            }
        }
    val children = references.inlines(merged)
    if (underline == null) return ParsedBlock(MarkdownBlock.Paragraph(children), end)
    val level = if (underline.groupValues[1][0] == '=') 1 else 2
    return ParsedBlock(MarkdownBlock.Heading(level, children), end + 1)
}

// Consecutive blank lines collapse into one BlankLine, and leading and trailing ones are dropped. Link definitions
// among them aren't shown, so they're collected and count as blank, except that definitions alone, with no blank
// line, add no space. Definitions at the start of the text or of a quote leave no gap above it either.
private fun parseBlankLinesAt(
    lines: List<String>,
    i: Int,
    references: LinkReferences,
): ParsedBlock {
    var hasBlankLine = false
    var end = i
    while (end < lines.size) {
        val line = lines[end]
        val definition = linkDefinitionRegex.matchEntire(line)
        if (definition != null) {
            val (label, url) = definition.destructured
            references.found.getOrPut(normalizeLinkLabel(label)) { url }
        } else if (line.isBlank()) {
            hasBlankLine = true
        } else {
            break
        }
        end++
    }
    return ParsedBlock(if (hasBlankLine && i > 0 && end < lines.size) MarkdownBlock.BlankLine else null, end)
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
    return hasCellSeparator(currentLine) && tableSeparatorRegex.matches(nextLine)
}

internal fun findTableEnd(
    lines: List<String>,
    startIndex: Int,
): Int {
    var i = startIndex + 2
    while (i < lines.size && hasCellSeparator(lines[i])) {
        i++
    }
    return i
}

private fun parseTable(
    tableLines: List<String>,
    references: Map<String, String>,
): MarkdownBlock.Table {
    val header = parseTableRow(tableLines[0])
    val rows =
        tableLines
            .drop(2)
            .filter { !isTableSeparator(it) }
            .map { parseTableRow(it) }
    return MarkdownBlock.Table(header, rows, references)
}

// Leading and trailing pipes are optional, so an empty first or last cell isn't a cell.
internal fun parseTableRow(line: String): List<String> {
    val parts = splitCells(line).map { it.trim() }
    return parts.filterIndexed { i, cell -> cell.isNotEmpty() || (i != 0 && i != parts.lastIndex) }
}

// Whether the line has a pipe that isn't escaped, so it can be a table row.
private fun hasCellSeparator(line: String): Boolean = splitCells(line).size > 1

// The line split at pipes that aren't escaped. A backslash escapes the character after it, so \| is a | in the cell,
// even inside inline code, while in \\| the backslash is escaped and the pipe separates cells.
private fun splitCells(line: String): List<String> {
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var i = 0
    while (i < line.length) {
        val c = line[i]
        val next = line.getOrNull(i + 1)
        if (c == '\\' && next != null) {
            // Only the backslash before a pipe is dropped; the inline parser handles the other escapes.
            if (next != '|') cell.append(c)
            cell.append(next)
            i += 2
        } else {
            if (c == '|') cells += cell.toString().also { cell.clear() } else cell.append(c)
            i++
        }
    }
    return cells + cell.toString()
}

private fun isTableSeparator(line: String): Boolean = tableSeparatorRegex.matches(line)
