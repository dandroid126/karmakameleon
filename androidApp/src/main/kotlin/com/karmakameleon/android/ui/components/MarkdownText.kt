package com.karmakameleon.android.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.shared.data.repository.MarkdownRepository
import com.karmakameleon.shared.domain.markdown.MarkdownBlock
import com.karmakameleon.shared.domain.markdown.MarkdownInline
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
    markdownRepository: MarkdownRepository = koinInject(),
) {
    // Text that hasn't been parsed yet is parsed off the main thread, and shows once it's ready. Keyed on the text, so
    // when it changes in place, like an edited comment, the old text isn't shown in the meantime.
    val parsed = remember(markdown) { mutableStateOf(markdownRepository.cached(markdown)) }
    LaunchedEffect(markdown) { parsed.value = markdownRepository.parse(markdown) }
    val blocks by parsed
    // The same objects while only the callbacks change, so a recomposition of the screen around unchanged text (a vote,
    // or hiding the post body) can skip its blocks. Callers usually pass new lambdas each time.
    val currentOnImageClick by rememberUpdatedState(onImageClick)
    val currentOnTextClick by rememberUpdatedState(onTextClick)
    val linkColor = MaterialTheme.colorScheme.primary
    val rendering =
        remember(linkColor, navigationHandler, renderInlineImages) {
            InlineRendering(
                linkColor = linkColor,
                onLinkClick = { url -> navigationHandler.handleLink(url) },
                renderInlineImages = renderInlineImages,
                onImageClick = { url -> currentOnImageClick(url) },
            )
        }
    val hasTextClick = onTextClick != null
    val textClick: (() -> Unit)? = remember(hasTextClick) { if (hasTextClick) ({ currentOnTextClick?.invoke() }) else null }
    Column(modifier = modifier) {
        for (block in blocks.orEmpty()) {
            RenderBlock(block = block, style = style, rendering = rendering, onTextClick = textClick)
        }
    }
}

// How inline elements look and what tapping a link or an image does. When renderInlineImages is off, images are
// shown as [img] or [gif] links instead.
internal data class InlineRendering(
    val linkColor: Color,
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
            RenderListItem(
                marker = BULLETS[block.level % BULLETS.size],
                level = block.level,
                children = block.children,
                style = style,
                rendering = rendering,
                onTextClick = onTextClick,
            )
        }

        is MarkdownBlock.OrderedListItem -> {
            RenderListItem(
                marker = "${block.number}.",
                level = block.level,
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
            RenderCodeBlock(block, style)
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
private fun RenderCodeBlock(
    block: MarkdownBlock.CodeBlock,
    style: TextStyle,
) {
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
// each other at any font size. A nested item is indented by one marker column for each level, so its marker sits
// under the text of the item it's nested in.
@Composable
private fun RenderListItem(
    marker: String,
    level: Int,
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
    val indent = (markerWidth + LIST_MARKER_GAP) * level
    Row(modifier = Modifier.padding(start = 4.dp + indent, top = 2.dp, bottom = 2.dp)) {
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

// Bullets for each level of nesting, like a browser's: a disc, a circle, then a square, and again from the disc.
private val BULLETS = listOf("•", "◦", "▪")
private val LIST_MARKER_GAP = 6.dp

// A quote: a bar at its left edge, and its blocks in italics.
@Composable
private fun RenderBlockQuote(
    block: MarkdownBlock.BlockQuote,
    style: TextStyle,
    rendering: InlineRendering,
    onTextClick: (() -> Unit)?,
) {
    val barColor = MaterialTheme.colorScheme.outline
    Column(
        modifier =
            Modifier
                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    shape =
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(4.dp),
                ).padding(8.dp)
                // Drawn rather than laid out beside the text, which would need the quote measured twice to know how
                // tall to make the bar. A long quote takes long enough to lay out that twice is noticeable.
                .drawBehind {
                    // On the start side, like the padding below.
                    val barWidth = QUOTE_BAR_WIDTH.roundToPx().toFloat()
                    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - barWidth else 0f
                    drawRect(barColor, topLeft = Offset(x, 0f), size = Size(barWidth, size.height))
                }.padding(start = QUOTE_BAR_WIDTH + QUOTE_BAR_GAP),
    ) {
        for (child in block.children) {
            RenderBlock(
                block = child,
                style = quoteStyle(style),
                rendering = rendering,
                onTextClick = onTextClick,
            )
        }
    }
}

// The tables in blocks, each with the style RenderBlock shows it in, so their columns can be measured before they're
// shown. Like RenderBlockQuote, a quote's blocks are in italics.
internal fun tablesIn(
    blocks: List<MarkdownBlock>,
    style: TextStyle,
): List<Pair<MarkdownBlock.Table, TextStyle>> =
    blocks.flatMap { block ->
        when (block) {
            is MarkdownBlock.Table -> listOf(block to style)
            is MarkdownBlock.BlockQuote -> tablesIn(block.children, quoteStyle(style))
            else -> emptyList()
        }
    }

private fun quoteStyle(style: TextStyle) = style.copy(fontStyle = FontStyle.Italic)

private val QUOTE_BAR_WIDTH = 4.dp
private val QUOTE_BAR_GAP = 8.dp

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
internal fun InlineText(
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

// A table cell's text as InlineText builds it, to measure it. Only its characters and the spans that change their size
// matter, so the colors and taps are placeholders. Spoilers take the same room hidden or revealed.
internal fun tableCellText(inlines: List<MarkdownInline>): AnnotatedString =
    buildInlines(inlines, MEASURING_RENDERING, Spoilers(revealed = emptySet(), hiddenColor = Color.Unspecified, onReveal = {}))

// Like a table cell's: images aren't shown as [img] links.
private val MEASURING_RENDERING =
    InlineRendering(linkColor = Color.Unspecified, onLinkClick = {}, renderInlineImages = true, onImageClick = {})

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
            appendStyled(SUPERSCRIPT_STYLE, inline.children, rendering, spoilers)
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

// Relative to the text around it, so superscript in a heading stays in proportion. Raised by a third of the
// ascent rather than BaselineShift.Superscript's half: raising doesn't make the line taller, so with a half, three
// levels reach into the line above.
private val SUPERSCRIPT_STYLE = SpanStyle(fontSize = 0.85.em, baselineShift = BaselineShift(0.33f))

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
