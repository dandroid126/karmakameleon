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

// lazyLines are the indexes of a quote's lines that continue its paragraph without a > (see parseQuoteAt).
private fun parseBlocks(
    lines: List<String>,
    references: LinkReferences,
    lazyLines: Set<Int> = emptySet(),
): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    while (i < lines.size) {
        val parsed = parseBlockAt(lines, i, references, lazyLines)
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
    lazyLines: Set<Int>,
): ParsedBlock {
    val line = lines[i]
    val codeFence = codeFenceRegex.matchEntire(line)
    return when {
        codeFence != null -> parseFencedCodeAt(lines, i, codeFence)
        isTableStart(lines, i, lazyLines) -> parseTableAt(lines, i, references)
        line.startsWith("#") -> ParsedBlock(parseHeading(line, references), i + 1)
        isQuoteLine(line) -> parseQuoteAt(lines, i, references)
        isCodeLine(line) -> ParsedBlock(MarkdownBlock.CodeBlock(line.removePrefix(CODE_INDENT)), i + 1)
        horizontalRuleRegex.matches(line) -> ParsedBlock(MarkdownBlock.HorizontalRule, i + 1)
        isListItemLine(line) -> parseListAt(lines, i, references)
        line.isNotBlank() && !linkDefinitionRegex.matches(line) -> parseParagraphAt(lines, i, references, lazyLines)
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
    lazyLines: Set<Int> = emptySet(),
): Boolean {
    val line = lines[i]
    return line.startsWith("#") ||
        isQuoteLine(line) ||
        isCodeLine(line) ||
        isCodeFence(line) ||
        horizontalRuleRegex.matches(line) ||
        isListItemLine(line) ||
        isTableStart(lines, i, lazyLines)
}

// Whether the line continues the paragraph above it rather than starting a block of its own.
private fun isParagraphContinuation(
    lines: List<String>,
    i: Int,
): Boolean = lines[i].isNotBlank() && !isSpecialLine(lines, i)

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
// A line without a > continues the quote when the quote ends with a paragraph that the line continues (a lazy
// continuation), so "> one\ntwo" is one quoted paragraph. A lazy line is only paragraph text, so it can't underline a
// heading or be a table's separator row.
private fun parseQuoteAt(
    lines: List<String>,
    i: Int,
    references: LinkReferences,
): ParsedBlock {
    val quoteLines = mutableListOf<String>()
    val lazyLines = mutableSetOf<Int>()
    var end = i
    while (end < lines.size) {
        if (isQuoteLine(lines[end])) {
            quoteLines += lines[end].removePrefix(">").removePrefix(" ")
        } else if (continuesQuote(lines, end, quoteLines, lazyLines, references)) {
            lazyLines += quoteLines.size
            quoteLines += lines[end]
        } else {
            break
        }
        end++
    }
    return ParsedBlock(MarkdownBlock.BlockQuote(parseBlocks(quoteLines, references, lazyLines)), end)
}

// Whether lines[i] lazily continues a quote whose lines so far are quoteLines.
private fun continuesQuote(
    lines: List<String>,
    i: Int,
    quoteLines: List<String>,
    lazyLines: Set<Int>,
    references: LinkReferences,
): Boolean {
    // A spoiler's >! marks a spoiler of its own rather than quoted text, so it stays out of the quote, as before.
    if (!isParagraphContinuation(lines, i) || lines[i].startsWith(">!") || quoteLines.last().isBlank()) return false
    // A lazy line is paragraph text, so the paragraph is still open after one.
    if (quoteLines.lastIndex in lazyLines) return true
    // Parsed with references of their own, so the definitions in the quote are only collected once.
    return endsWithParagraph(parseBlocks(quoteLines, LinkReferences(known = references.known), lazyLines))
}

private fun endsWithParagraph(blocks: List<MarkdownBlock>): Boolean =
    when (val last = blocks.lastOrNull()) {
        is MarkdownBlock.Paragraph, is MarkdownBlock.BulletListItem, is MarkdownBlock.OrderedListItem -> true
        is MarkdownBlock.BlockQuote -> endsWithParagraph(last.children)
        else -> false
    }

// Consecutive list items, which blank lines can separate. Like on Reddit, an item indented at least as far as the
// text of an item above it is nested in that item, so a sublist needs two spaces under "- " and three under "1. ".
// Lines after an item that aren't blocks of their own continue its text, indented or not.
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
    while (next < lines.size) {
        var itemEnd = next + 1
        while (itemEnd < lines.size && isParagraphContinuation(lines, itemEnd) && !listItemRegex.matches(lines[itemEnd])) itemEnd++
        val item = parseListItem(lines.subList(next, itemEnd), textColumns, references) ?: break
        if (next > end) items += MarkdownBlock.BlankLine
        items += item
        end = itemEnd
        // Blank lines between items keep the list going, so a sublist after one is still nested.
        next = (end until lines.size).firstOrNull { lines[it].isNotBlank() } ?: lines.size
    }
    return ParsedBlock(items, end)
}

// The list item on the first of itemLines, with the rest continuing its text, nested in the items whose text columns
// are given, which it updates. Null when the line isn't an item of the list.
private fun parseListItem(
    itemLines: List<String>,
    textColumns: ArrayDeque<Int>,
    references: LinkReferences,
): MarkdownBlock? {
    val match = listItemRegex.matchEntire(itemLines.first()) ?: return null
    val (indent, marker, gap) = match.destructured
    while (textColumns.isNotEmpty() && indent.length < textColumns.last()) textColumns.removeLast()
    if (textColumns.isEmpty() && indent.length > MAX_LIST_INDENT) return null
    val level = textColumns.size
    textColumns.addLast(indent.length + marker.length + gap.length)
    val children = references.inlines(joinParagraphLines(listOf(match.groupValues.last()) + itemLines.drop(1)))
    val number = marker.dropLast(1).toIntOrNull()
    return if (number != null) MarkdownBlock.OrderedListItem(number, children, level) else MarkdownBlock.BulletListItem(children, level)
}

// Consecutive plain lines form one paragraph. As in CommonMark, a link definition can't interrupt a paragraph, so one
// on the paragraph's next line is text. With a heading underline after it, the paragraph is a heading instead, even
// when the underline is --- and would otherwise be a horizontal rule.
private fun parseParagraphAt(
    lines: List<String>,
    i: Int,
    references: LinkReferences,
    lazyLines: Set<Int>,
): ParsedBlock {
    var end = i + 1
    var underline: MatchResult? = null
    while (end < lines.size && lines[end].isNotBlank()) {
        underline = if (end in lazyLines) null else headingUnderlineRegex.matchEntire(lines[end])
        if (underline != null || isSpecialLine(lines, end, lazyLines)) break
        end++
    }
    val children = references.inlines(joinParagraphLines(lines.subList(i, end)))
    if (underline == null) return ParsedBlock(MarkdownBlock.Paragraph(children), end)
    val level = if (underline.groupValues[1][0] == '=') 1 else 2
    return ParsedBlock(MarkdownBlock.Heading(level, children), end + 1)
}

// A line ending in two spaces is a line break; otherwise lines are joined with a space. Leading spaces of the lines
// after the first are dropped.
private fun joinParagraphLines(lines: List<String>): String =
    buildString {
        lines.forEachIndexed { idx, line ->
            if (idx > 0) append(if (lines[idx - 1].endsWith("  ")) "\n" else " ")
            append(if (idx > 0) line.trim() else line.trimEnd())
        }
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

// A lazy line can't be the separator row.
internal fun isTableStart(
    lines: List<String>,
    index: Int,
    lazyLines: Set<Int> = emptySet(),
): Boolean {
    if (index + 1 >= lines.size || index + 1 in lazyLines) return false
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
