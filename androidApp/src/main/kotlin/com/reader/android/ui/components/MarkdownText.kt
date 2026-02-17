package com.reader.android.ui.components

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    onLinkClick: (String) -> Unit = {},
    onTextClick: (() -> Unit)? = null,
    renderInlineImages: Boolean = true,
    onImageClick: (String) -> Unit = {}
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
                    RenderMixedContent(
                        text = line.removePrefix("###").trimStart(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        onLinkClick = onLinkClick,
                        onTextClick = onTextClick,
                        renderInlineImages = renderInlineImages,
                        onImageClick = onImageClick
                    )
                }
                line.startsWith("##") -> {
                    RenderMixedContent(
                        text = line.removePrefix("##").trimStart(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                        onLinkClick = onLinkClick,
                        onTextClick = onTextClick,
                        renderInlineImages = renderInlineImages,
                        onImageClick = onImageClick
                    )
                }
                line.startsWith("#") -> {
                    RenderMixedContent(
                        text = line.removePrefix("#").trimStart(),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                        onLinkClick = onLinkClick,
                        onTextClick = onTextClick,
                        renderInlineImages = renderInlineImages,
                        onImageClick = onImageClick
                    )
                }
                line.startsWith(">") -> {
                    val quoteLines = mutableListOf(line.removePrefix(">").removePrefix(" "))
                    while (i + 1 < lines.size && lines[i + 1].startsWith(">")) {
                        i++
                        quoteLines.add(lines[i].removePrefix(">").removePrefix(" "))
                    }
                    RenderMixedContent(
                        text = quoteLines.joinToString("\n"),
                        style = style.copy(fontStyle = FontStyle.Italic),
                        modifier = Modifier
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                            .padding(start = 8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        onLinkClick = onLinkClick,
                        onTextClick = onTextClick,
                        renderInlineImages = renderInlineImages,
                        onImageClick = onImageClick
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
                    RenderMixedContent(
                        text = "• " + line.substring(2),
                        style = style,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                        onLinkClick = onLinkClick,
                        onTextClick = onTextClick,
                        renderInlineImages = renderInlineImages,
                        onImageClick = onImageClick
                    )
                }
                line.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val match = Regex("^(\\d+)\\.\\s+(.*)").find(line)
                    if (match != null) {
                        RenderMixedContent(
                            text = "${match.groupValues[1]}. ${match.groupValues[2]}",
                            style = style,
                            modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                            onLinkClick = onLinkClick,
                            onTextClick = onTextClick,
                            renderInlineImages = renderInlineImages,
                            onImageClick = onImageClick
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
                    RenderMixedContent(
                        text = merged,
                        style = style,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                        onLinkClick = onLinkClick,
                        onTextClick = onTextClick,
                        renderInlineImages = renderInlineImages,
                        onImageClick = onImageClick
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

private sealed class ContentSegment {
    data class Text(val text: String) : ContentSegment()
    data class Image(val url: String) : ContentSegment()
}

private val inlineImagePattern = Regex("""!\[img]\(([^)]+)\)""")
private val inlineGifPattern = Regex("""!\[gif]\(giphy\|([^)]+)\)""")
private val previewRedditUrlPattern = Regex("""https://preview\.redd\.it/[^\s)]+""")

private fun splitIntoContentSegments(text: String): List<ContentSegment> {
    data class ImageMatch(val range: IntRange, val url: String)

    val imageMatches = mutableListOf<ImageMatch>()

    for (match in inlineImagePattern.findAll(text)) {
        val content = match.groupValues[1]
        val url = if (content.startsWith("http")) {
            content
        } else {
            "https://preview.redd.it/${content}.jpeg"
        }
        imageMatches.add(ImageMatch(match.range, url))
    }

    for (match in inlineGifPattern.findAll(text)) {
        val parts = match.groupValues[1].split("|")
        val gifId = parts[0]
        val params = parts.drop(1)
        val knownParams = setOf("downsized")
        val unknownParams = params.filter { it !in knownParams }
        if (unknownParams.isNotEmpty()) {
            Napier.w("Unknown giphy parameters: $unknownParams in ${match.value}")
        }
        val suffix = when {
            "downsized" in params -> "giphy-downsized.gif"
            else -> "giphy.gif"
        }
        imageMatches.add(ImageMatch(match.range, "https://i.giphy.com/${gifId}/${suffix}"))
    }

    for (match in previewRedditUrlPattern.findAll(text)) {
        if (imageMatches.none { it.range.first <= match.range.first && match.range.last <= it.range.last }) {
            imageMatches.add(ImageMatch(match.range, match.value))
        }
    }

    if (imageMatches.isEmpty()) {
        return listOf(ContentSegment.Text(text))
    }

    val sorted = imageMatches.sortedBy { it.range.first }
    val segments = mutableListOf<ContentSegment>()
    var lastEnd = 0

    for (img in sorted) {
        if (img.range.first > lastEnd) {
            val textBefore = text.substring(lastEnd, img.range.first).trim()
            if (textBefore.isNotEmpty()) {
                segments.add(ContentSegment.Text(textBefore))
            }
        }
        segments.add(ContentSegment.Image(img.url))
        lastEnd = img.range.last + 1
    }

    if (lastEnd < text.length) {
        val textAfter = text.substring(lastEnd).trim()
        if (textAfter.isNotEmpty()) {
            segments.add(ContentSegment.Text(textAfter))
        }
    }

    return segments
}

@Composable
private fun RenderMixedContent(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
    onTextClick: (() -> Unit)? = null,
    renderInlineImages: Boolean = true,
    onImageClick: (String) -> Unit = {}
) {
    if (!renderInlineImages) {
        val segments = splitIntoContentSegments(text)
        if (segments.all { it is ContentSegment.Text }) {
            ClickableMarkdownText(
                text = parseInlineMarkdown(text),
                style = style,
                modifier = modifier,
                onLinkClick = onLinkClick,
                onTextClick = onTextClick
            )
        } else {
            Column(modifier = modifier) {
                for (segment in segments) {
                    when (segment) {
                        is ContentSegment.Text -> {
                            ClickableMarkdownText(
                                text = parseInlineMarkdown(segment.text),
                                style = style,
                                onLinkClick = onLinkClick,
                                onTextClick = onTextClick
                            )
                        }
                        is ContentSegment.Image -> {
                            val isGif = segment.url.contains("giphy") || segment.url.endsWith(".gif")
                            val label = if (isGif) "[gif]" else "[img]"
                            val linkColor = MaterialTheme.colorScheme.primary
                            val annotated = buildAnnotatedString {
                                pushStringAnnotation(tag = "IMAGE", annotation = segment.url)
                                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                                    append(label)
                                }
                                pop()
                            }
                            ClickableText(
                                text = annotated,
                                style = style,
                                modifier = Modifier.padding(vertical = 2.dp),
                                onClick = { offset ->
                                    annotated.getStringAnnotations("IMAGE", offset, offset)
                                        .firstOrNull()?.let { onImageClick(it.item) }
                                }
                            )
                        }
                    }
                }
            }
        }
        return
    }

    val segments = splitIntoContentSegments(text)

    if (segments.size == 1 && segments[0] is ContentSegment.Text) {
        ClickableMarkdownText(
            text = parseInlineMarkdown(text),
            style = style,
            modifier = modifier,
            onLinkClick = onLinkClick,
            onTextClick = onTextClick
        )
        return
    }

    Column(modifier = modifier) {
        for (segment in segments) {
            when (segment) {
                is ContentSegment.Text -> {
                    ClickableMarkdownText(
                        text = parseInlineMarkdown(segment.text),
                        style = style,
                        onLinkClick = onLinkClick,
                        onTextClick = onTextClick
                    )
                }
                is ContentSegment.Image -> {
                    InlineImage(
                        url = segment.url,
                        modifier = Modifier.padding(vertical = 4.dp),
                        onImageClick = onImageClick
                    )
                }
            }
        }
    }
}

private val aspectRatioCache = mutableMapOf<String, Float>()

@Composable
private fun InlineImage(
    url: String,
    modifier: Modifier = Modifier,
    onImageClick: (String) -> Unit = {}
) {
    var aspectRatio by remember(url) { mutableStateOf(aspectRatioCache[url]) }
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (aspectRatio != null) Modifier.aspectRatio(aspectRatio!!)
                else Modifier
            )
            .clipToBounds()
            .combinedClickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = { onImageClick(url) },
                onLongClick = { showMenu = true }
            )
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillWidth,
            onSuccess = { state ->
                val w = state.result.image.width
                val h = state.result.image.height
                if (w > 0 && h > 0) {
                    val ratio = w.toFloat() / h.toFloat()
                    aspectRatioCache[url] = ratio
                    aspectRatio = ratio
                }
            }
        )
        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Copy Image URL") },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(url))
                        Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Save Image") },
                    onClick = {
                        showMenu = false
                        scope.launch {
                            saveImageToGallery(context, url)
                        }
                    }
                )
            }
        }
    }
}

private suspend fun saveImageToGallery(context: android.content.Context, url: String) {
    withContext(Dispatchers.IO) {
        try {
            val filename = url.substringAfterLast("/").substringBefore("?").let {
                if (it.contains(".")) it else "${it}.jpg"
            }
            val mimeType = when {
                filename.endsWith(".gif") -> "image/gif"
                filename.endsWith(".png") -> "image/png"
                filename.endsWith(".webp") -> "image/webp"
                else -> "image/jpeg"
            }

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.DATA, file.absolutePath)
                }
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
            }

            if (uri != null) {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.connect()
                connection.inputStream.use { input ->
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                connection.disconnect()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Image saved", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
