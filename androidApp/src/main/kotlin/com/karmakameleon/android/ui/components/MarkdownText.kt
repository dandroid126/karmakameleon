package com.karmakameleon.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.shared.domain.markdown.MarkdownBlock
import com.karmakameleon.shared.domain.markdown.MarkdownInline
import com.karmakameleon.shared.util.markdown.parseMarkdown
import org.koin.compose.koinInject

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    onTextClick: (() -> Unit)? = null,
    renderInlineImages: Boolean = true,
    onImageClick: (String) -> Unit = {},
    navigationHandler: NavigationHandler = koinInject()
) {
    val handleLinkClick: (String) -> Unit = { url -> navigationHandler.handleLink(url) }
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val density = LocalDensity.current
    Column(modifier = modifier) {
        for (block in blocks) {
            RenderBlock(
                block = block,
                style = style,
                onLinkClick = handleLinkClick,
                onTextClick = onTextClick,
                renderInlineImages = renderInlineImages,
                onImageClick = onImageClick,
                density = density
            )
        }
    }
}

@Composable
private fun RenderBlock(
    block: MarkdownBlock,
    style: TextStyle,
    onLinkClick: (String) -> Unit,
    onTextClick: (() -> Unit)?,
    renderInlineImages: Boolean,
    onImageClick: (String) -> Unit,
    density: androidx.compose.ui.unit.Density
) {
    when (block) {
        is MarkdownBlock.Heading -> {
            val headingStyle = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                2 -> MaterialTheme.typography.headlineSmall
                3 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                4 -> MaterialTheme.typography.titleLarge
                5 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                else -> MaterialTheme.typography.titleSmall.copy(textDecoration = TextDecoration.Underline)
            }
            val padding = when (block.level) {
                1 -> Modifier.padding(top = 8.dp, bottom = 8.dp)
                2 -> Modifier.padding(top = 6.dp, bottom = 6.dp)
                3, 4 -> Modifier.padding(top = 4.dp, bottom = 4.dp)
                else -> Modifier.padding(top = 2.dp, bottom = 2.dp)
            }
            RenderInlineList(
                inlines = block.children,
                style = headingStyle,
                modifier = padding,
                onLinkClick = onLinkClick,
                onTextClick = onTextClick,
                renderInlineImages = renderInlineImages,
                onImageClick = onImageClick
            )
        }
        is MarkdownBlock.Paragraph -> {
            RenderInlineList(
                inlines = block.children,
                style = style,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                onLinkClick = onLinkClick,
                onTextClick = onTextClick,
                renderInlineImages = renderInlineImages,
                onImageClick = onImageClick
            )
        }
        is MarkdownBlock.BulletListItem -> {
            RenderInlineList(
                inlines = block.children,
                style = style,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                onLinkClick = onLinkClick,
                onTextClick = onTextClick,
                renderInlineImages = renderInlineImages,
                onImageClick = onImageClick
            )
        }
        is MarkdownBlock.OrderedListItem -> {
            RenderInlineList(
                inlines = block.children,
                style = style,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                onLinkClick = onLinkClick,
                onTextClick = onTextClick,
                renderInlineImages = renderInlineImages,
                onImageClick = onImageClick
            )
        }
        is MarkdownBlock.BlockQuote -> {
            Row(
                modifier = Modifier
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    )
                    .height(IntrinsicSize.Min)
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    for (child in block.children) {
                        RenderBlock(
                            block = child,
                            style = style.copy(fontStyle = FontStyle.Italic),
                            onLinkClick = onLinkClick,
                            onTextClick = onTextClick,
                            renderInlineImages = renderInlineImages,
                            onImageClick = onImageClick,
                            density = density
                        )
                    }
                }
            }
        }
        is MarkdownBlock.CodeBlock -> {
            Text(
                text = block.text,
                style = style.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .padding(top = 2.dp, bottom = 2.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            )
        }
        is MarkdownBlock.Table -> {
            RenderTable(
                table = block,
                style = style,
                onLinkClick = onLinkClick,
                onTextClick = onTextClick
            )
        }
        MarkdownBlock.HorizontalRule -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline
            )
        }
        MarkdownBlock.BlankLine -> {
            val spacerHeight = with(density) { style.fontSize.toPx().toDp() }
            Spacer(modifier = Modifier.height(spacerHeight))
        }
    }
}

private sealed class InlineSegment {
    data class TextRun(val inlines: List<MarkdownInline>) : InlineSegment()
    data class ImageItem(val url: String) : InlineSegment()
}

private fun splitInlinesIntoSegments(inlines: List<MarkdownInline>): List<InlineSegment> {
    val segments = mutableListOf<InlineSegment>()
    val textBuffer = mutableListOf<MarkdownInline>()
    for (inline in inlines) {
        if (inline is MarkdownInline.Image) {
            if (textBuffer.isNotEmpty()) {
                segments.add(InlineSegment.TextRun(textBuffer.toList()))
                textBuffer.clear()
            }
            segments.add(InlineSegment.ImageItem(inline.url))
        } else {
            textBuffer.add(inline)
        }
    }
    if (textBuffer.isNotEmpty()) {
        segments.add(InlineSegment.TextRun(textBuffer.toList()))
    }
    return segments
}

@Composable
private fun RenderInlineList(
    inlines: List<MarkdownInline>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
    onTextClick: (() -> Unit)? = null,
    renderInlineImages: Boolean = true,
    onImageClick: (String) -> Unit = {}
) {
    val hasSpoilers = inlines.any { containsSpoiler(it) }
    val hasImages = renderInlineImages && inlines.any { it is MarkdownInline.Image }

    if (!hasSpoilers && !hasImages) {
        val linkColor = MaterialTheme.colorScheme.primary
        val superscriptSize = MaterialTheme.typography.bodySmall.fontSize
        val annotated = buildAnnotatedString {
            for (inline in inlines) {
                appendInline(inline, linkColor, superscriptSize, onLinkClick, renderInlineImages, onImageClick)
            }
        }
        ClickableMarkdownText(text = annotated, style = style, modifier = modifier, onTextClick = onTextClick)
        return
    }

    val linkColor = MaterialTheme.colorScheme.primary
    val superscriptSize = MaterialTheme.typography.bodySmall.fontSize
    val segments = splitInlinesIntoSegments(inlines)

    Column(modifier = modifier) {
        for (segment in segments) {
            when (segment) {
                is InlineSegment.TextRun -> {
                    RenderInlineSegment(
                        inlines = segment.inlines,
                        style = style,
                        linkColor = linkColor,
                        superscriptSize = superscriptSize,
                        onLinkClick = onLinkClick,
                        onTextClick = onTextClick,
                        renderInlineImages = renderInlineImages,
                        onImageClick = onImageClick
                    )
                }
                is InlineSegment.ImageItem -> {
                    InlineImage(url = segment.url, modifier = Modifier.padding(vertical = 4.dp), onImageClick = onImageClick)
                }
            }
        }
    }
}

@Composable
private fun RenderInlineSegment(
    inlines: List<MarkdownInline>,
    style: TextStyle,
    linkColor: Color,
    superscriptSize: androidx.compose.ui.unit.TextUnit,
    onLinkClick: (String) -> Unit,
    onTextClick: (() -> Unit)?,
    renderInlineImages: Boolean,
    onImageClick: (String) -> Unit
) {
    val hasSpoilers = inlines.any { containsSpoiler(it) }

    if (!hasSpoilers) {
        val annotated = buildAnnotatedString {
            for (inline in inlines) {
                appendInline(inline, linkColor, superscriptSize, onLinkClick, renderInlineImages, onImageClick)
            }
        }
        ClickableMarkdownText(text = annotated, style = style, onTextClick = onTextClick)
        return
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        for (inline in inlines) {
            when (inline) {
                is MarkdownInline.Spoiler -> {
                    SpoilerComponent(
                        children = inline.children,
                        style = style,
                        linkColor = linkColor,
                        superscriptSize = superscriptSize,
                        onLinkClick = onLinkClick,
                        renderInlineImages = renderInlineImages,
                        onImageClick = onImageClick
                    )
                }
                else -> {
                    val annotated = buildAnnotatedString {
                        appendInline(inline, linkColor, superscriptSize, onLinkClick, renderInlineImages, onImageClick)
                    }
                    ClickableMarkdownText(text = annotated, style = style, onTextClick = onTextClick)
                }
            }
        }
    }
}

private fun containsSpoiler(inline: MarkdownInline): Boolean {
    return when (inline) {
        is MarkdownInline.Spoiler -> true
        is MarkdownInline.Bold -> inline.children.any { containsSpoiler(it) }
        is MarkdownInline.Italic -> inline.children.any { containsSpoiler(it) }
        is MarkdownInline.BoldItalic -> inline.children.any { containsSpoiler(it) }
        is MarkdownInline.Strikethrough -> inline.children.any { containsSpoiler(it) }
        is MarkdownInline.Superscript -> inline.children.any { containsSpoiler(it) }
        is MarkdownInline.Link -> inline.text.any { containsSpoiler(it) }
        else -> false
    }
}

@Composable
private fun SpoilerComponent(
    children: List<MarkdownInline>,
    style: TextStyle,
    linkColor: Color,
    superscriptSize: androidx.compose.ui.unit.TextUnit,
    onLinkClick: (String) -> Unit,
    renderInlineImages: Boolean,
    onImageClick: (String) -> Unit
) {
    var isRevealed by remember { mutableStateOf(false) }
    val spoilerColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = if (isRevealed) MaterialTheme.colorScheme.onSurface else spoilerColor

    val annotated = buildAnnotatedString {
        for (child in children) {
            appendInline(child, linkColor, superscriptSize, onLinkClick, renderInlineImages, onImageClick)
        }
    }

    Box(
        modifier = Modifier
            .background(if (isRevealed) Color.Transparent else spoilerColor)
            .clickable { isRevealed = !isRevealed }
            .padding(horizontal = 2.dp)
    ) {
        Text(
            text = annotated,
            style = style.copy(color = textColor),
            modifier = Modifier.padding(2.dp)
        )
    }
}

@Composable
private fun ClickableMarkdownText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onTextClick: (() -> Unit)? = null
) {
    val mergedStyle = if (style.color == Color.Unspecified) {
        style.copy(color = MaterialTheme.colorScheme.onSurface)
    } else {
        style
    }
    Text(
        text = text,
        style = mergedStyle,
        modifier = if (onTextClick != null) modifier.clickable(onClick = onTextClick) else modifier
    )
}

private fun AnnotatedString.Builder.appendInline(
    inline: MarkdownInline,
    linkColor: Color,
    superscriptSize: androidx.compose.ui.unit.TextUnit,
    onLinkClick: (String) -> Unit,
    renderInlineImages: Boolean,
    onImageClick: (String) -> Unit
) {
    when (inline) {
        is MarkdownInline.Text -> append(inline.text)
        is MarkdownInline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            for (child in inline.children) appendInline(child, linkColor, superscriptSize, onLinkClick, renderInlineImages, onImageClick)
        }
        is MarkdownInline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            for (child in inline.children) appendInline(child, linkColor, superscriptSize, onLinkClick, renderInlineImages, onImageClick)
        }
        is MarkdownInline.BoldItalic -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
            for (child in inline.children) appendInline(child, linkColor, superscriptSize, onLinkClick, renderInlineImages, onImageClick)
        }
        is MarkdownInline.Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
            for (child in inline.children) appendInline(child, linkColor, superscriptSize, onLinkClick, renderInlineImages, onImageClick)
        }
        is MarkdownInline.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
            append(inline.text)
        }
        is MarkdownInline.Superscript -> withStyle(SpanStyle(fontSize = superscriptSize)) {
            for (child in inline.children) appendInline(child, linkColor, superscriptSize, onLinkClick, renderInlineImages, onImageClick)
        }
        is MarkdownInline.Spoiler -> {
        }
        is MarkdownInline.Link -> {
            val url = inline.url
            pushLink(
                LinkAnnotation.Clickable(
                    tag = url,
                    styles = TextLinkStyles(style = SpanStyle(color = linkColor)),
                    linkInteractionListener = { onLinkClick(url) }
                )
            )
            withStyle(SpanStyle(color = linkColor)) {
                for (child in inline.text) appendInline(child, linkColor, superscriptSize, onLinkClick, renderInlineImages, onImageClick)
            }
            pop()
        }
        is MarkdownInline.Image -> {
            if (!renderInlineImages) {
                val isGif = inline.url.contains("giphy") || inline.url.endsWith(".gif")
                val label = if (isGif) "[gif]" else "[img]"
                val url = inline.url
                pushLink(
                    LinkAnnotation.Clickable(
                        tag = url,
                        styles = TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                        linkInteractionListener = { onImageClick(url) }
                    )
                )
                append(label)
                pop()
            }
        }
    }
}

@Composable
private fun RenderTable(
    table: MarkdownBlock.Table,
    style: TextStyle,
    onLinkClick: (String) -> Unit = {},
    onTextClick: (() -> Unit)? = null
) {
    if (table.header.isEmpty()) return

    val columnCount = table.header.size
    val borderColor = MaterialTheme.colorScheme.outline
    val borderWidth = 1.dp
    val allRows = listOf(table.header) + table.rows
    val linkColor = MaterialTheme.colorScheme.primary
    val superscriptSize = MaterialTheme.typography.bodySmall.fontSize

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
                    val cellInlines = com.karmakameleon.shared.util.markdown.parseInlineMarkdown(cellContent)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(borderWidth, borderColor)
                            .padding(8.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        val cellStyle = if (rowIndex == 0) style.copy(fontWeight = FontWeight.Bold) else style
                        val annotated = buildAnnotatedString {
                            for (inline in cellInlines) {
                                appendInline(inline, linkColor, superscriptSize, onLinkClick, true, {})
                            }
                        }
                        ClickableMarkdownText(
                            text = annotated,
                            style = cellStyle,
                            modifier = Modifier.wrapContentHeight(),
                            onTextClick = onTextClick
                        )
                    }
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
    val menuItems = imageMenuItems(url)

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
            MediaLongPressMenu(
                items = menuItems,
                expanded = showMenu,
                onDismiss = { showMenu = false }
            )
        }
    }
}
