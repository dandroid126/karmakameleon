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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.shared.domain.markdown.MarkdownBlock
import com.karmakameleon.shared.domain.markdown.MarkdownInline
import com.karmakameleon.shared.util.markdown.parseMarkdownBlocks
import org.koin.compose.koinInject

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    onTextClick: (() -> Unit)? = null,
    renderInlineImages: Boolean = true,
    onImageClick: (String) -> Unit = {},
    navigationHandler: NavigationHandler = koinInject(),
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val rendering =
        InlineRendering(
            linkColor = MaterialTheme.colorScheme.primary,
            superscriptSize = MaterialTheme.typography.bodySmall.fontSize,
            onLinkClick = { url -> navigationHandler.handleLink(url) },
            renderInlineImages = renderInlineImages,
            onImageClick = onImageClick,
        )
    Column(modifier = modifier) {
        for (block in blocks) {
            RenderBlock(block = block, style = style, rendering = rendering, onTextClick = onTextClick)
        }
    }
}

// How inline elements look and what tapping a link or an image does. When renderInlineImages is off, images are
// shown as [img] or [gif] links instead.
private data class InlineRendering(
    val linkColor: Color,
    val superscriptSize: TextUnit,
    val onLinkClick: (String) -> Unit,
    val renderInlineImages: Boolean,
    val onImageClick: (String) -> Unit,
)

@Composable
private fun RenderBlock(
    block: MarkdownBlock,
    style: TextStyle,
    rendering: InlineRendering,
    onTextClick: (() -> Unit)?,
) {
    when (block) {
        is MarkdownBlock.Heading -> {
            RenderInlineList(
                inlines = block.children,
                style = headingStyle(block.level),
                modifier = Modifier.headingPadding(block.level),
                rendering = rendering,
                onTextClick = onTextClick,
            )
        }

        is MarkdownBlock.Paragraph -> {
            RenderInlineList(block.children, style, rendering, onTextClick, Modifier.padding(top = 2.dp, bottom = 2.dp))
        }

        is MarkdownBlock.BulletListItem -> {
            RenderListItem(marker = "•", children = block.children, style = style, rendering = rendering, onTextClick = onTextClick)
        }

        is MarkdownBlock.OrderedListItem -> {
            RenderListItem(
                marker = "${block.number}.",
                children = block.children,
                style = style,
                rendering = rendering,
                onTextClick = onTextClick,
            )
        }

        is MarkdownBlock.BlockQuote -> {
            RenderBlockQuote(block = block, style = style, rendering = rendering, onTextClick = onTextClick)
        }

        is MarkdownBlock.CodeBlock -> {
            Text(
                text = block.text,
                style = style.copy(fontFamily = FontFamily.Monospace),
                modifier =
                    Modifier
                        .padding(top = 2.dp, bottom = 2.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
            )
        }

        is MarkdownBlock.Table -> {
            RenderTable(table = block, style = style, rendering = rendering, onTextClick = onTextClick)
        }

        MarkdownBlock.HorizontalRule -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline,
            )
        }

        MarkdownBlock.BlankLine -> {
            val spacerHeight = with(LocalDensity.current) { style.fontSize.toPx().toDp() }
            Spacer(modifier = Modifier.height(spacerHeight))
        }
    }
}

@Composable
@ReadOnlyComposable
private fun headingStyle(level: Int): TextStyle =
    when (level) {
        1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        2 -> MaterialTheme.typography.headlineSmall
        3 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        4 -> MaterialTheme.typography.titleLarge
        5 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        else -> MaterialTheme.typography.titleSmall.copy(textDecoration = TextDecoration.Underline)
    }

// Space above and below headings of levels 1 to 4; smaller headings get 2.dp.
private val HEADING_PADDING = listOf(8.dp, 6.dp, 4.dp, 4.dp)

private fun Modifier.headingPadding(level: Int): Modifier = padding(vertical = HEADING_PADDING.getOrElse(level - 1) { 2.dp })

// The marker has a column of its own, so an item's wrapped lines line up with its text rather than its marker.
// Markers are right-aligned in the width of "99." in the item's style, so the items of a list up to 99 line up with
// each other at any font size.
@Composable
private fun RenderListItem(
    marker: String,
    children: List<MarkdownInline>,
    style: TextStyle,
    rendering: InlineRendering,
    onTextClick: (() -> Unit)?,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val markerWidth =
        remember(style, density) {
            with(density) {
                textMeasurer
                    .measure(WIDEST_MARKER, style)
                    .size.width
                    .toDp()
            }
        }
    Row(modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
        ClickableMarkdownText(
            text = AnnotatedString(marker),
            style = style.copy(textAlign = TextAlign.End),
            modifier = Modifier.widthIn(min = markerWidth + LIST_MARKER_GAP).padding(end = LIST_MARKER_GAP),
            onTextClick = onTextClick,
        )
        RenderInlineList(children, style, rendering, onTextClick, Modifier.weight(1f))
    }
}

private const val WIDEST_MARKER = "99."
private val LIST_MARKER_GAP = 6.dp

// A quote: a bar at its left edge, and its blocks in italics.
@Composable
private fun RenderBlockQuote(
    block: MarkdownBlock.BlockQuote,
    style: TextStyle,
    rendering: InlineRendering,
    onTextClick: (() -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    shape =
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(4.dp),
                ).height(IntrinsicSize.Min)
                .padding(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            for (child in block.children) {
                RenderBlock(
                    block = child,
                    style = style.copy(fontStyle = FontStyle.Italic),
                    rendering = rendering,
                    onTextClick = onTextClick,
                )
            }
        }
    }
}

private sealed interface InlineSegment {
    data class TextRun(
        val inlines: List<MarkdownInline>,
    ) : InlineSegment

    data class ImageItem(
        val url: String,
    ) : InlineSegment
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

// One Text when it can be; otherwise images on lines of their own between runs of text.
@Composable
private fun RenderInlineList(
    inlines: List<MarkdownInline>,
    style: TextStyle,
    rendering: InlineRendering,
    onTextClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val hasImages = rendering.renderInlineImages && inlines.any { it is MarkdownInline.Image }

    if (!hasImages) {
        InlineText(inlines = inlines, style = style, rendering = rendering, onTextClick = onTextClick, modifier = modifier)
        return
    }

    Column(modifier = modifier) {
        for (segment in splitInlinesIntoSegments(inlines)) {
            when (segment) {
                is InlineSegment.TextRun -> {
                    InlineText(inlines = segment.inlines, style = style, rendering = rendering, onTextClick = onTextClick)
                }

                is InlineSegment.ImageItem -> {
                    InlineImage(url = segment.url, modifier = Modifier.padding(vertical = 4.dp), onImageClick = rendering.onImageClick)
                }
            }
        }
    }
}

// Spoilers are part of the text, so a line with one wraps like any other. A tap reveals a spoiler and, like on
// Reddit, it stays revealed.
@Composable
private fun InlineText(
    inlines: List<MarkdownInline>,
    style: TextStyle,
    rendering: InlineRendering,
    onTextClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var revealed by remember(inlines) { mutableStateOf(emptySet<Int>()) }
    val spoilers =
        Spoilers(
            revealed = revealed,
            hiddenColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onReveal = { revealed = revealed + it },
        )
    val text = buildInlines(inlines, rendering, spoilers)
    val hasHiddenSpoiler = spoilers.count > revealed.size
    ClickableMarkdownText(
        text = text,
        style = style,
        // Screen readers read a hidden spoiler's text, so they're told it's meant to be hidden.
        modifier = if (hasHiddenSpoiler) modifier.semantics { stateDescription = "Hidden spoiler" } else modifier,
        onTextClick = onTextClick,
    )
}

// The spoilers in one Text, numbered in order as the text is built; count is how many there are once it's built.
private class Spoilers(
    val revealed: Set<Int>,
    val hiddenColor: Color,
    val onReveal: (Int) -> Unit,
) {
    var count = 0
}

@Composable
private fun ClickableMarkdownText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onTextClick: (() -> Unit)? = null,
) {
    val mergedStyle =
        if (style.color == Color.Unspecified) {
            style.copy(color = MaterialTheme.colorScheme.onSurface)
        } else {
            style
        }
    Text(
        text = text,
        style = mergedStyle,
        modifier = if (onTextClick != null) modifier.clickable(onClick = onTextClick) else modifier,
    )
}

private fun buildInlines(
    inlines: List<MarkdownInline>,
    rendering: InlineRendering,
    spoilers: Spoilers,
): AnnotatedString = buildAnnotatedString { inlines.forEach { appendInline(it, rendering, spoilers) } }

private fun AnnotatedString.Builder.appendInline(
    inline: MarkdownInline,
    rendering: InlineRendering,
    spoilers: Spoilers,
) {
    when (inline) {
        is MarkdownInline.Text -> {
            append(inline.text)
        }

        is MarkdownInline.Bold -> {
            appendStyled(SpanStyle(fontWeight = FontWeight.Bold), inline.children, rendering, spoilers)
        }

        is MarkdownInline.Italic -> {
            appendStyled(SpanStyle(fontStyle = FontStyle.Italic), inline.children, rendering, spoilers)
        }

        is MarkdownInline.BoldItalic -> {
            appendStyled(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), inline.children, rendering, spoilers)
        }

        is MarkdownInline.Strikethrough -> {
            appendStyled(SpanStyle(textDecoration = TextDecoration.LineThrough), inline.children, rendering, spoilers)
        }

        is MarkdownInline.Code -> {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(inline.text) }
        }

        is MarkdownInline.Superscript -> {
            appendStyled(SpanStyle(fontSize = rendering.superscriptSize), inline.children, rendering, spoilers)
        }

        is MarkdownInline.Spoiler -> {
            appendSpoiler(inline, rendering, spoilers)
        }

        is MarkdownInline.Link -> {
            appendLink(inline, rendering, spoilers)
        }

        is MarkdownInline.Image -> {
            if (!rendering.renderInlineImages) appendImageLink(inline.url, rendering)
        }
    }
}

private fun AnnotatedString.Builder.appendStyled(
    style: SpanStyle,
    children: List<MarkdownInline>,
    rendering: InlineRendering,
    spoilers: Spoilers,
) {
    withStyle(style) { children.forEach { appendInline(it, rendering, spoilers) } }
}

// A hidden spoiler is drawn in one color on a background of the same color. Links in it take that color too, and
// tapping them reveals the spoiler instead of opening them.
private fun AnnotatedString.Builder.appendSpoiler(
    spoiler: MarkdownInline.Spoiler,
    rendering: InlineRendering,
    spoilers: Spoilers,
) {
    val index = spoilers.count++
    if (index in spoilers.revealed) {
        spoiler.children.forEach { appendInline(it, rendering, spoilers) }
        return
    }
    val reveal = { spoilers.onReveal(index) }
    val hidden = rendering.copy(linkColor = spoilers.hiddenColor, onLinkClick = { reveal() }, onImageClick = { reveal() })
    pushLink(LinkAnnotation.Clickable(tag = SPOILER_TAG, linkInteractionListener = { reveal() }))
    appendStyled(SpanStyle(color = spoilers.hiddenColor, background = spoilers.hiddenColor), spoiler.children, hidden, spoilers)
    pop()
}

private const val SPOILER_TAG = "spoiler"

private fun AnnotatedString.Builder.appendLink(
    link: MarkdownInline.Link,
    rendering: InlineRendering,
    spoilers: Spoilers,
) {
    val url = link.url
    // A tap reveals the spoilers in the link's text before it opens the link.
    val firstSpoiler = spoilers.count
    var endSpoiler = firstSpoiler
    pushLink(
        LinkAnnotation.Clickable(
            tag = url,
            styles = TextLinkStyles(style = SpanStyle(color = rendering.linkColor)),
            linkInteractionListener = {
                val hidden = (firstSpoiler until endSpoiler).filterNot { index -> index in spoilers.revealed }
                if (hidden.isEmpty()) rendering.onLinkClick(url) else hidden.forEach(spoilers.onReveal)
            },
        ),
    )
    appendStyled(SpanStyle(color = rendering.linkColor), link.text, rendering, spoilers)
    endSpoiler = spoilers.count
    pop()
}

private fun AnnotatedString.Builder.appendImageLink(
    url: String,
    rendering: InlineRendering,
) {
    val isGif = url.contains("giphy") || url.endsWith(".gif")
    pushLink(
        LinkAnnotation.Clickable(
            tag = url,
            styles = TextLinkStyles(style = SpanStyle(color = rendering.linkColor, textDecoration = TextDecoration.Underline)),
            linkInteractionListener = { rendering.onImageClick(url) },
        ),
    )
    append(if (isGif) "[gif]" else "[img]")
    pop()
}

// Images aren't shown in table cells.
@Composable
private fun RenderTable(
    table: MarkdownBlock.Table,
    style: TextStyle,
    rendering: InlineRendering,
    onTextClick: (() -> Unit)?,
) {
    if (table.header.isEmpty()) return
    val cellRendering = rendering.copy(renderInlineImages = true, onImageClick = {})

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(TABLE_BORDER_WIDTH, MaterialTheme.colorScheme.outline),
    ) {
        (listOf(table.header) + table.rows).forEachIndexed { rowIndex, row ->
            TableRow(
                cells = row,
                columnCount = table.header.size,
                isHeader = rowIndex == 0,
                style = style,
                rendering = cellRendering,
                onTextClick = onTextClick,
            )
        }
    }
}

private val TABLE_BORDER_WIDTH = 1.dp

// A row of equal-width cells; rows with fewer cells than the header get empty ones.
@Composable
private fun TableRow(
    cells: List<String>,
    columnCount: Int,
    isHeader: Boolean,
    style: TextStyle,
    rendering: InlineRendering,
    onTextClick: (() -> Unit)?,
) {
    val borderColor = MaterialTheme.colorScheme.outline
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
                .border(TABLE_BORDER_WIDTH, borderColor)
                .background(if (isHeader) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent),
    ) {
        repeat(columnCount) { colIndex ->
            val cellInlines =
                com.karmakameleon.shared.util.markdown
                    .parseInlineMarkdown(cells.getOrElse(colIndex) { "" })
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(TABLE_BORDER_WIDTH, borderColor)
                        .padding(8.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                InlineText(
                    inlines = cellInlines,
                    style = if (isHeader) style.copy(fontWeight = FontWeight.Bold) else style,
                    rendering = rendering,
                    onTextClick = onTextClick,
                    modifier = Modifier.wrapContentHeight(),
                )
            }
        }
    }
}

private val aspectRatioCache = mutableMapOf<String, Float>()

@Composable
private fun InlineImage(
    url: String,
    modifier: Modifier = Modifier,
    onImageClick: (String) -> Unit = {},
) {
    var aspectRatio by remember(url) { mutableStateOf(aspectRatioCache[url]) }
    var showMenu by remember { mutableStateOf(false) }
    val menuItems = imageMenuItems(url)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .then(aspectRatio?.let { Modifier.aspectRatio(it) } ?: Modifier)
                .clipToBounds()
                .combinedClickable(
                    interactionSource =
                        remember {
                            androidx.compose.foundation.interaction
                                .MutableInteractionSource()
                        },
                    indication = null,
                    onClick = { onImageClick(url) },
                    onLongClick = { showMenu = true },
                ),
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
            },
        )
        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
            MediaLongPressMenu(
                items = menuItems,
                expanded = showMenu,
                onDismiss = { showMenu = false },
            )
        }
    }
}
