package com.reader.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    onLinkClick: (String) -> Unit = {},
    onTextClick: (() -> Unit)? = null
) {
    val lines = markdown.split("\n")
    val density = LocalDensity.current
    Column(modifier = modifier) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                isTableStart(lines, i) -> {
                    val tableEndIdx = findTableEnd(lines, i)
                    val tableLines = lines.subList(i, tableEndIdx)
                    RenderTable(tableLines, style, onLinkClick, onTextClick)
                    i = tableEndIdx - 1
                }
                line.startsWith("###") -> {
                    ClickableMarkdownText(
                        text = parseInlineMarkdown(line.removePrefix("###").trimStart()),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        onLinkClick = onLinkClick,
                        onTextClick = onTextClick
                    )
                }
                line.startsWith("##") -> {
                    ClickableMarkdownText(
                        text = parseInlineMarkdown(line.removePrefix("##").trimStart()),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                        onLinkClick = onLinkClick,
                        onTextClick = onTextClick
                    )
                }
                line.startsWith("#") -> {
                    ClickableMarkdownText(
                        text = parseInlineMarkdown(line.removePrefix("#").trimStart()),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                        onLinkClick = onLinkClick,
                        onTextClick = onTextClick
                    )
                }
                line.startsWith(">") -> {
                    val quoteLines = mutableListOf(line.removePrefix(">").removePrefix(" "))
                    while (i + 1 < lines.size && lines[i + 1].startsWith(">")) {
                        i++
                        quoteLines.add(lines[i].removePrefix(">").removePrefix(" "))
                    }
                    ClickableMarkdownText(
                        text = parseInlineMarkdown(quoteLines.joinToString("\n")),
                        style = style.copy(fontStyle = FontStyle.Italic),
                        modifier = Modifier
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                            .padding(start = 8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        onLinkClick = onLinkClick,
                        onTextClick = onTextClick
                    )
                }
                line.startsWith("    ") -> {
                    Text(
                        text = line.substring(4),
                        style = style.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .padding(top = 2.dp, bottom = 2.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                    )
                }
                line.matches(Regex("^-{3,}$")) -> {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    ClickableMarkdownText(
                        text = parseInlineMarkdown("• " + line.substring(2)),
                        style = style,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                        onLinkClick = onLinkClick,
                        onTextClick = onTextClick
                    )
                }
                line.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val match = Regex("^(\\d+)\\.\\s+(.*)").find(line)
                    if (match != null) {
                        ClickableMarkdownText(
                            text = parseInlineMarkdown("${match.groupValues[1]}. ${match.groupValues[2]}"),
                            style = style,
                            modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                            onLinkClick = onLinkClick,
                            onTextClick = onTextClick
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
                                if (paragraphLines[idx - 1].endsWith("  ")) {
                                    append("\n")
                                } else {
                                    append(" ")
                                }
                            }
                            append(l.trimEnd())
                        }
                    }
                    ClickableMarkdownText(
                        text = parseInlineMarkdown(merged),
                        style = style,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                        onLinkClick = onLinkClick,
                        onTextClick = onTextClick
                    )
                }
                else -> {
                    while (i + 1 < lines.size && lines[i + 1].isBlank()) {
                        i++
                    }
                    if (i + 1 < lines.size) {
                        val spacerHeight = with(density) { style.fontSize.toPx().toDp() }
                        Spacer(modifier = Modifier.height(spacerHeight))
                    }
                }
            }
            i++
        }
    }
}

private fun isSpecialLine(lines: List<String>, i: Int): Boolean {
    val line = lines[i]
    return line.startsWith("#") ||
            line.startsWith(">") ||
            line.startsWith("    ") ||
            line.matches(Regex("^-{3,}$")) ||
            line.startsWith("- ") || line.startsWith("* ") ||
            line.matches(Regex("^\\d+\\.\\s+.*")) ||
            isTableStart(lines, i)
}

private fun isTableStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    val currentLine = lines[index]
    val nextLine = lines[index + 1]
    
    return currentLine.contains("|") && nextLine.matches(Regex("^\\|?\\s*[-:\\s|]+\\s*\\|?$"))
}

private fun findTableEnd(lines: List<String>, startIndex: Int): Int {
    var i = startIndex + 2
    while (i < lines.size && lines[i].contains("|")) {
        i++
    }
    return i
}

private fun parseTableRow(line: String): List<String> {
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

@Composable
private fun ClickableMarkdownText(
    text: androidx.compose.ui.text.AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
    onTextClick: (() -> Unit)? = null
) {
    val mergedStyle = if (style.color == Color.Unspecified) {
        style.copy(color = MaterialTheme.colorScheme.onSurface)
    } else {
        style
    }
    ClickableText(
        text = text,
        style = mergedStyle,
        modifier = modifier,
        onClick = { offset ->
            val annotation = text.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()
            if (annotation != null) {
                onLinkClick(annotation.item)
            } else {
                onTextClick?.invoke()
            }
        }
    )
}

@Composable
private fun RenderTable(
    tableLines: List<String>,
    style: TextStyle,
    onLinkClick: (String) -> Unit = {},
    onTextClick: (() -> Unit)? = null
) {
    if (tableLines.isEmpty()) return
    
    val headerRow = parseTableRow(tableLines[0])
    val dataRows = tableLines.drop(2)
        .filter { !isTableSeparator(it) }
        .map { parseTableRow(it) }
    
    if (headerRow.isEmpty()) return
    
    val columnCount = headerRow.size
    val borderColor = MaterialTheme.colorScheme.outline
    val borderWidth = 1.dp
    
    val allRows = listOf(headerRow) + dataRows
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(borderWidth, borderColor)
    ) {
        allRows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
                    .border(borderWidth, borderColor)
                    .background(
                        if (rowIndex == 0) MaterialTheme.colorScheme.surfaceVariant
                        else Color.Transparent
                    )
            ) {
                repeat(columnCount) { colIndex ->
                    val cellContent = if (colIndex < row.size) row[colIndex] else ""
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(borderWidth, borderColor)
                            .padding(8.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        ClickableMarkdownText(
                            text = parseInlineMarkdown(cellContent),
                            style = if (rowIndex == 0) style.copy(fontWeight = FontWeight.Bold) else style,
                            modifier = Modifier.wrapContentHeight(),
                            onLinkClick = onLinkClick,
                            onTextClick = onTextClick
                        )
                    }
                }
            }
        }
    }
}

private data class InlineMatch(
    val text: String,
    val style: SpanStyle? = null,
    val linkUrl: String? = null,
    val endIndex: Int
)

private fun delimitedPattern(delimiter: String, style: SpanStyle): (String, Int) -> InlineMatch? = { t, i ->
    if (t.startsWith(delimiter, i)) {
        val closeIdx = t.indexOf(delimiter, i + delimiter.length)
        if (closeIdx != -1) {
            InlineMatch(
                text = t.substring(i + delimiter.length, closeIdx),
                style = style,
                endIndex = closeIdx + delimiter.length
            )
        } else null
    } else null
}

@Composable
private fun parseInlineMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val superscriptSize = MaterialTheme.typography.bodySmall.fontSize
    val escapeChars = setOf('\\', '*', '_', '~', '`', '>', '!', '<', '^', '[', ']', '(', ')', '#', '-', '.', '+', '|')

    val patterns: List<(String, Int) -> InlineMatch?> = listOf(
        // Backslash escape
        { t, i ->
            if (t.startsWith("\\", i) && i + 1 < t.length && t[i + 1] in escapeChars) {
                InlineMatch(text = t[i + 1].toString(), endIndex = i + 2)
            } else null
        },
        // Links: [text](url) and [text][ref]
        { t, i ->
            if (t.startsWith("[", i)) {
                val closeTextIdx = t.indexOf("]", i + 1)
                if (closeTextIdx != -1) {
                    when {
                        t.getOrNull(closeTextIdx + 1) == '(' -> {
                            val closeUrlIdx = t.indexOf(")", closeTextIdx + 2)
                            if (closeUrlIdx != -1) {
                                val linkText = t.substring(i + 1, closeTextIdx)
                                val linkUrl = t.substring(closeTextIdx + 2, closeUrlIdx)
                                InlineMatch(linkText, SpanStyle(color = linkColor), linkUrl, closeUrlIdx + 1)
                            } else null
                        }
                        t.getOrNull(closeTextIdx + 1) == '[' -> {
                            val refCloseIdx = t.indexOf("]", closeTextIdx + 2)
                            if (refCloseIdx != -1) {
                                val linkText = t.substring(i + 1, closeTextIdx)
                                InlineMatch(linkText, SpanStyle(color = linkColor), linkText, refCloseIdx + 1)
                            } else null
                        }
                        else -> null
                    }
                } else null
            } else null
        },
        // Bold+Italic ___...___ and ***...***
        delimitedPattern("___", SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)),
        delimitedPattern("***", SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)),
        // Bold __...__ and **...**
        delimitedPattern("__", SpanStyle(fontWeight = FontWeight.Bold)),
        delimitedPattern("**", SpanStyle(fontWeight = FontWeight.Bold)),
        // Strikethrough ~~...~~
        delimitedPattern("~~", SpanStyle(textDecoration = TextDecoration.LineThrough)),
        // Spoiler >!...!<
        { t, i ->
            if (t.startsWith(">!", i)) {
                val closeIdx = t.indexOf("!<", i + 2)
                if (closeIdx != -1) {
                    InlineMatch(t.substring(i + 2, closeIdx), SpanStyle(background = Color.Gray), endIndex = closeIdx + 2)
                } else null
            } else null
        },
        // Superscript ^(...)
        { t, i ->
            if (t.startsWith("^(", i)) {
                val closeIdx = t.indexOf(")", i + 2)
                if (closeIdx != -1) {
                    InlineMatch(t.substring(i + 2, closeIdx), SpanStyle(fontSize = superscriptSize), endIndex = closeIdx + 1)
                } else null
            } else null
        },
        // Superscript ^word
        { t, i ->
            if (t.startsWith("^", i) && i + 1 < t.length && t[i + 1] != '(') {
                val endIdx = (i + 1 until t.length).firstOrNull { t[it].isWhitespace() } ?: t.length
                InlineMatch(t.substring(i + 1, endIdx), SpanStyle(fontSize = superscriptSize), endIndex = endIdx)
            } else null
        },
        // Italic _..._ (not __)
        { t, i ->
            if (t.startsWith("_", i) && !t.startsWith("__", i)) {
                val closeIdx = t.indexOf("_", i + 1)
                if (closeIdx != -1) {
                    InlineMatch(t.substring(i + 1, closeIdx), SpanStyle(fontStyle = FontStyle.Italic), endIndex = closeIdx + 1)
                } else null
            } else null
        },
        // Italic *...* (not ** or ***)
        { t, i ->
            if (t.startsWith("*", i) && !t.startsWith("**", i)) {
                val closeIdx = t.indexOf("*", i + 1)
                if (closeIdx != -1) {
                    InlineMatch(t.substring(i + 1, closeIdx), SpanStyle(fontStyle = FontStyle.Italic), endIndex = closeIdx + 1)
                } else null
            } else null
        },
        // Inline code `...`
        delimitedPattern("`", SpanStyle(fontFamily = FontFamily.Monospace)),
        // Bare URL
        { t, i ->
            if (t.startsWith("https://", i) || t.startsWith("http://", i)) {
                val endIdx = (i until t.length).firstOrNull { t[it].isWhitespace() } ?: t.length
                val url = t.substring(i, endIdx)
                InlineMatch(url, SpanStyle(color = linkColor), url, endIdx)
            } else null
        },
        // Subreddit reference r/
        { t, i ->
            if (t.startsWith("r/", i)) {
                val endIdx = (i + 2 until t.length).firstOrNull { !t[it].isLetterOrDigit() && t[it] != '_' } ?: t.length
                val ref = t.substring(i, endIdx)
                InlineMatch(ref, SpanStyle(color = linkColor), "https://www.reddit.com/$ref", endIdx)
            } else null
        },
        // User reference u/
        { t, i ->
            if (t.startsWith("u/", i)) {
                val endIdx = (i + 2 until t.length).firstOrNull { !t[it].isLetterOrDigit() && t[it] != '_' } ?: t.length
                val ref = t.substring(i, endIdx)
                InlineMatch(ref, SpanStyle(color = linkColor), "https://www.reddit.com/$ref", endIdx)
            } else null
        }
    )

    return buildAnnotatedString {
        var current = 0
        var i = 0
        while (i < text.length) {
            val match = patterns.firstNotNullOfOrNull { it(text, i) }
            if (match != null) {
                append(text.substring(current, i))
                if (match.linkUrl != null) {
                    pushStringAnnotation(tag = "URL", annotation = match.linkUrl)
                }
                if (match.style != null) {
                    withStyle(match.style) { append(match.text) }
                } else {
                    append(match.text)
                }
                if (match.linkUrl != null) {
                    pop()
                }
                i = match.endIndex
                current = i
            } else {
                i++
            }
        }
        append(text.substring(current))
    }
}
