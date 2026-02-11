package com.reader.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
    onLinkClick: (String) -> Unit = {}
) {
    val lines = markdown.split("\n")
    Column(modifier = modifier) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                isTableStart(lines, i) -> {
                    val tableEndIdx = findTableEnd(lines, i)
                    val tableLines = lines.subList(i, tableEndIdx)
                    RenderTable(tableLines, style, onLinkClick)
                    i = tableEndIdx - 1
                }
                line.startsWith("#") && !line.startsWith("# ") -> {
                    i++
                    continue
                }
                line.startsWith("# ") -> {
                    ClickableMarkdownText(
                        text = parseInlineMarkdown(line.substring(2)),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                        onLinkClick = onLinkClick
                    )
                }
                line.startsWith("## ") -> {
                    ClickableMarkdownText(
                        text = parseInlineMarkdown(line.substring(3)),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                        onLinkClick = onLinkClick
                    )
                }
                line.startsWith("### ") -> {
                    ClickableMarkdownText(
                        text = parseInlineMarkdown(line.substring(4)),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        onLinkClick = onLinkClick
                    )
                }
                line.startsWith(">") -> {
                    ClickableMarkdownText(
                        text = parseInlineMarkdown(line.substring(2)),
                        style = style.copy(fontStyle = FontStyle.Italic),
                        modifier = Modifier
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                            .padding(start = 8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        onLinkClick = onLinkClick
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
                line.startsWith("- ") || line.startsWith("* ") -> {
                    ClickableMarkdownText(
                        text = parseInlineMarkdown("• " + line.substring(2)),
                        style = style,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                        onLinkClick = onLinkClick
                    )
                }
                line.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val match = Regex("^(\\d+)\\.\\s+(.*)").find(line)
                    if (match != null) {
                        ClickableMarkdownText(
                            text = parseInlineMarkdown("${match.groupValues[1]}. ${match.groupValues[2]}"),
                            style = style,
                            modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                            onLinkClick = onLinkClick
                        )
                    }
                }
                line.isNotBlank() -> {
                    ClickableMarkdownText(
                        text = parseInlineMarkdown(line),
                        style = style,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                        onLinkClick = onLinkClick
                    )
                }
                else -> {
                    Text(text = "", modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                }
            }
            i++
        }
    }
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
    onLinkClick: (String) -> Unit = {}
) {
    ClickableText(
        text = text,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            text.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    val url = annotation.item
                    onLinkClick(url)
                }
        }
    )
}

@Composable
private fun RenderTable(
    tableLines: List<String>,
    style: TextStyle,
    onLinkClick: (String) -> Unit = {}
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
                            onLinkClick = onLinkClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun parseInlineMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var current = 0
        var i = 0
        
        while (i < text.length) {
            when {
                text.startsWith("[", i) -> {
                    val closeTextIdx = text.indexOf("]", i)
                    if (closeTextIdx != -1 && text.getOrNull(closeTextIdx + 1) == '(') {
                        val closeUrlIdx = text.indexOf(")", closeTextIdx + 2)
                        if (closeUrlIdx != -1) {
                            append(text.substring(current, i))
                            val linkText = text.substring(i + 1, closeTextIdx)
                            val linkUrl = text.substring(closeTextIdx + 2, closeUrlIdx)
                            pushStringAnnotation(tag = "URL", annotation = linkUrl)
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                append(linkText)
                            }
                            pop()
                            i = closeUrlIdx + 1
                            current = i
                        } else {
                            i++
                        }
                    } else if (closeTextIdx != -1 && text.getOrNull(closeTextIdx + 1) == '[') {
                        val refCloseIdx = text.indexOf("]", closeTextIdx + 2)
                        if (refCloseIdx != -1) {
                            append(text.substring(current, i))
                            val linkText = text.substring(i + 1, closeTextIdx)
                            pushStringAnnotation(tag = "URL", annotation = linkText)
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                append(linkText)
                            }
                            pop()
                            i = refCloseIdx + 1
                            current = i
                        } else {
                            i++
                        }
                    } else {
                        i++
                    }
                }
                text.startsWith("___", i) -> {
                    val closeIdx = text.indexOf("___", i + 3)
                    if (closeIdx != -1) {
                        append(text.substring(current, i))
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 3, closeIdx))
                        }
                        i = closeIdx + 3
                        current = i
                    } else {
                        i++
                    }
                }
                text.startsWith("***", i) -> {
                    val closeIdx = text.indexOf("***", i + 3)
                    if (closeIdx != -1) {
                        append(text.substring(current, i))
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 3, closeIdx))
                        }
                        i = closeIdx + 3
                        current = i
                    } else {
                        i++
                    }
                }
                text.startsWith("__", i) -> {
                    val closeIdx = text.indexOf("__", i + 2)
                    if (closeIdx != -1) {
                        append(text.substring(current, i))
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, closeIdx))
                        }
                        i = closeIdx + 2
                        current = i
                    } else {
                        i++
                    }
                }
                text.startsWith("**", i) -> {
                    val closeIdx = text.indexOf("**", i + 2)
                    if (closeIdx != -1) {
                        append(text.substring(current, i))
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, closeIdx))
                        }
                        i = closeIdx + 2
                        current = i
                    } else {
                        i++
                    }
                }
                text.startsWith("~~", i) -> {
                    val closeIdx = text.indexOf("~~", i + 2)
                    if (closeIdx != -1) {
                        append(text.substring(current, i))
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(text.substring(i + 2, closeIdx))
                        }
                        i = closeIdx + 2
                        current = i
                    } else {
                        i++
                    }
                }
                text.startsWith(">!", i) -> {
                    val closeIdx = text.indexOf("!<", i + 2)
                    if (closeIdx != -1) {
                        append(text.substring(current, i))
                        withStyle(SpanStyle(background = Color.Gray)) {
                            append(text.substring(i + 2, closeIdx))
                        }
                        i = closeIdx + 2
                        current = i
                    } else {
                        i++
                    }
                }
                text.startsWith("^(", i) -> {
                    val closeIdx = text.indexOf(")", i + 2)
                    if (closeIdx != -1) {
                        append(text.substring(current, i))
                        withStyle(SpanStyle(fontSize = MaterialTheme.typography.bodySmall.fontSize)) {
                            append(text.substring(i + 2, closeIdx))
                        }
                        i = closeIdx + 1
                        current = i
                    } else {
                        i++
                    }
                }
                text.startsWith("^", i) && i + 1 < text.length && text[i + 1] != '(' -> {
                    val endIdx = (i + 1 until text.length).firstOrNull { text[it].isWhitespace() } ?: text.length
                    append(text.substring(current, i))
                    withStyle(SpanStyle(fontSize = MaterialTheme.typography.bodySmall.fontSize)) {
                        append(text.substring(i + 1, endIdx))
                    }
                    i = endIdx
                    current = i
                }
                text.startsWith("_", i) && !text.startsWith("__", i) -> {
                    val closeIdx = text.indexOf("_", i + 1)
                    if (closeIdx != -1) {
                        append(text.substring(current, i))
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, closeIdx))
                        }
                        i = closeIdx + 1
                        current = i
                    } else {
                        i++
                    }
                }
                text.startsWith("*", i) && !text.startsWith("**", i) && !text.startsWith("***", i) -> {
                    val closeIdx = text.indexOf("*", i + 1)
                    if (closeIdx != -1) {
                        append(text.substring(current, i))
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, closeIdx))
                        }
                        i = closeIdx + 1
                        current = i
                    } else {
                        i++
                    }
                }
                text.startsWith("`", i) -> {
                    val closeIdx = text.indexOf("`", i + 1)
                    if (closeIdx != -1) {
                        append(text.substring(current, i))
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                            append(text.substring(i + 1, closeIdx))
                        }
                        i = closeIdx + 1
                        current = i
                    } else {
                        i++
                    }
                }
                text.startsWith("https://", i) || text.startsWith("http://", i) -> {
                    val endIdx = (i until text.length).firstOrNull { text[it].isWhitespace() } ?: text.length
                    append(text.substring(current, i))
                    val url = text.substring(i, endIdx)
                    pushStringAnnotation(tag = "URL", annotation = url)
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append(url)
                    }
                    pop()
                    i = endIdx
                    current = i
                }
                text.startsWith("r/", i) -> {
                    val endIdx = (i + 2 until text.length).firstOrNull { !text[it].isLetterOrDigit() && text[it] != '_' } ?: text.length
                    append(text.substring(current, i))
                    val subredditRef = text.substring(i, endIdx)
                    pushStringAnnotation(tag = "URL", annotation = "https://www.reddit.com/$subredditRef")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append(subredditRef)
                    }
                    pop()
                    i = endIdx
                    current = i
                }
                text.startsWith("u/", i) -> {
                    val endIdx = (i + 2 until text.length).firstOrNull { !text[it].isLetterOrDigit() && text[it] != '_' } ?: text.length
                    append(text.substring(current, i))
                    val userRef = text.substring(i, endIdx)
                    pushStringAnnotation(tag = "URL", annotation = "https://www.reddit.com/$userRef")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append(userRef)
                    }
                    pop()
                    i = endIdx
                    current = i
                }
                else -> i++
            }
        }
        
        append(text.substring(current))
    }
}
