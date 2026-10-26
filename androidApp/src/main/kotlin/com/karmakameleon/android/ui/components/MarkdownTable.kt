package com.karmakameleon.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.karmakameleon.android.ui.theme.markdownColors
import com.karmakameleon.shared.domain.markdown.MarkdownBlock
import com.karmakameleon.shared.domain.markdown.TableAlignment
import com.karmakameleon.shared.util.markdown.parseInlineMarkdown
import kotlin.math.roundToInt

// Columns are sized by their content, like a browser's automatic table layout, and a table too wide for the
// screen scrolls sideways. Images aren't shown in table cells.
@Composable
internal fun RenderTable(
    table: MarkdownBlock.Table,
    style: TextStyle,
    rendering: InlineRendering,
    onTextClick: (() -> Unit)?,
) {
    if (table.header.isEmpty()) return
    val cellRendering = rendering.copy(renderInlineImages = true, onImageClick = {})
    val columnCount = table.header.size
    val scroll = remember { TableScroll() }

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        TableGrid(columnCount = columnCount, scroll = scroll) {
            (listOf(table.header) + table.rows).forEachIndexed { rowIndex, row ->
                repeat(columnCount) { colIndex ->
                    TableCell(
                        text = row.getOrElse(colIndex) { "" },
                        references = table.references,
                        isHeader = rowIndex == 0,
                        alignment = table.alignments.getOrElse(colIndex) { TableAlignment.Default },
                        style = style,
                        rendering = cellRendering,
                        onTextClick = onTextClick,
                    )
                }
            }
        }
        // Over the faded edges, to show there's more of the table that way.
        val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
        if (scroll.canScrollBackward) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = hintColor,
                modifier = Modifier.align(Alignment.CenterStart).size(SCROLL_HINT_ICON_SIZE),
            )
        }
        if (scroll.canScrollForward) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = hintColor,
                modifier = Modifier.align(Alignment.CenterEnd).size(SCROLL_HINT_ICON_SIZE),
            )
        }
    }
}

private val TABLE_BORDER_WIDTH = 1.dp
private val CELL_PADDING = 8.dp
private val SCROLL_HINT_ICON_SIZE = 20.dp
private val SCROLL_FADE_WIDTH = 32.dp

// Words too long to fit beside the other columns, such as a bare URL, break inside the word when that makes the
// table fit and leaves each of them at least this share of the width.
private const val LONG_WORD_MIN_SHARE = 1f / 3

// In a table that scrolls, the widest a word can make its column, so a column is never wider than the screen.
private const val SCROLLING_COLUMN_MAX_SHARE = 0.6f

@Composable
private fun TableCell(
    text: String,
    references: Map<String, String>,
    isHeader: Boolean,
    alignment: TableAlignment,
    style: TextStyle,
    rendering: InlineRendering,
    onTextClick: (() -> Unit)?,
) {
    val inlines = remember(text, references) { parseInlineMarkdown(text, references) }
    Box(
        modifier =
            Modifier
                .border(TABLE_BORDER_WIDTH, MaterialTheme.colorScheme.outline)
                .background(if (isHeader) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                .padding(CELL_PADDING),
    ) {
        // Filling the cell, so the text can be aligned in it.
        InlineText(
            inlines = inlines,
            style = style.copy(fontWeight = if (isHeader) FontWeight.Bold else style.fontWeight, textAlign = alignment.textAlign),
            rendering = rendering,
            onTextClick = onTextClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Like Reddit's align="left" and align="right", left and right don't change with the layout direction.
private val TableAlignment.textAlign: TextAlign
    get() =
        when (this) {
            TableAlignment.Default -> TextAlign.Start
            TableAlignment.Left -> TextAlign.Left
            TableAlignment.Center -> TextAlign.Center
            TableAlignment.Right -> TextAlign.Right
        }

// How far a wide table is scrolled. maxOffset is set each time the table is measured.
@Stable
internal class TableScroll {
    var offset by mutableFloatStateOf(0f)
        private set

    var maxOffset by mutableIntStateOf(0)
        private set

    // Derived, so the scroll hints only change when the table reaches or leaves an edge, not on every frame.
    val canScrollBackward by derivedStateOf { offset > 0f }
    val canScrollForward by derivedStateOf { offset < maxOffset }

    val state =
        ScrollableState { delta ->
            val old = offset
            offset = (old + delta).coerceIn(0f, maxOffset.toFloat())
            offset - old
        }

    // Called while measuring. Reading offset there would make every scroll frame measure the whole table again
    // instead of only moving the cells, so the read isn't tracked.
    fun updateMaxOffset(value: Int) {
        maxOffset = value
        Snapshot.withoutReadObservation { offset = offset.coerceAtMost(value.toFloat()) }
    }
}

@Composable
private fun TableGrid(
    columnCount: Int,
    scroll: TableScroll,
    content: @Composable () -> Unit,
) {
    val fadeMaskColor = markdownColors().fadeMaskColor
    val measurePolicy = remember(columnCount, scroll) { TableMeasurePolicy(columnCount, scroll) }
    val canScroll = scroll.maxOffset > 0
    Layout(
        content = content,
        modifier =
            Modifier
                .semantics {
                    horizontalScrollAxisRange = ScrollAxisRange(value = { scroll.offset }, maxValue = { scroll.maxOffset.toFloat() })
                    if (canScroll) {
                        scrollBy { x, _ -> scroll.state.dispatchRawDelta(x) != 0f }
                    }
                }.scrollable(
                    state = scroll.state,
                    orientation = Orientation.Horizontal,
                    // Off when the table fits, so a sideways swipe on it reaches whatever is around it.
                    enabled = canScroll,
                    reverseDirection = ScrollableDefaults.reverseDirection(LocalLayoutDirection.current, Orientation.Horizontal, false),
                ).fadeScrolledEdges(scroll, fadeMaskColor)
                .clipToBounds(),
        measurePolicy = measurePolicy,
    )
}

// Fades the content out towards each edge it can still scroll to.
private fun Modifier.fadeScrolledEdges(
    scroll: TableScroll,
    maskColor: Color,
): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        val fade = SCROLL_FADE_WIDTH.toPx().coerceAtMost(size.width / 2)
        val (fadeLeft, fadeRight) =
            if (layoutDirection == LayoutDirection.Ltr) {
                scroll.canScrollBackward to scroll.canScrollForward
            } else {
                scroll.canScrollForward to scroll.canScrollBackward
            }
        if (fadeLeft) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(maskColor, Color.Transparent), startX = 0f, endX = fade),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstOut,
            )
        }
        if (fadeRight) {
            val start = size.width - fade
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, maskColor), startX = start, endX = size.width),
                topLeft = Offset(start, 0f),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstOut,
            )
        }
    }

// Lays out the cells, given in rows, in columns sized by columnWidths, each row as tall as its tallest cell.
private class TableMeasurePolicy(
    private val columnCount: Int,
    private val scroll: TableScroll,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val widths = columnWidths(measurables, constraints.maxWidth)
        val rows = measurables.chunked(columnCount)
        val heights = rowHeights(rows, widths)
        val placeables =
            rows.mapIndexed { rowIndex, row ->
                row.mapIndexed { colIndex, cell -> cell.measure(Constraints.fixed(widths[colIndex], heights[rowIndex])) }
            }
        val tableWidth = widths.sum()
        val width = constraints.constrainWidth(tableWidth)
        scroll.updateMaxOffset(tableWidth - width)
        return layout(width, constraints.constrainHeight(heights.sum())) {
            val startX = -scroll.offset.roundToInt()
            var y = 0
            placeables.forEachIndexed { rowIndex, row ->
                var x = startX
                row.forEachIndexed { colIndex, placeable ->
                    placeable.placeRelative(x, y)
                    x += widths[colIndex]
                }
                y += heights[rowIndex]
            }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = measurables.chunked(columnCount).columnMaxOf { it.minIntrinsicWidth(Constraints.Infinity) }.sum()

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = measurables.chunked(columnCount).columnMaxOf { it.maxIntrinsicWidth(Constraints.Infinity) }.sum()

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = rowHeights(measurables.chunked(columnCount), columnWidths(measurables, width)).sum()

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = minIntrinsicHeight(measurables, width)

    private fun columnWidths(
        cells: List<IntrinsicMeasurable>,
        available: Int,
    ): IntArray {
        val rows = cells.chunked(columnCount)
        val maxWidths = rows.columnMaxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        if (available == Constraints.Infinity) return maxWidths
        val minWidths = rows.columnMaxOf { it.minIntrinsicWidth(Constraints.Infinity) }
        return distributeColumnWidths(minWidths, maxWidths, available)
    }

    private fun rowHeights(
        rows: List<List<IntrinsicMeasurable>>,
        widths: IntArray,
    ): IntArray =
        IntArray(rows.size) { rowIndex ->
            rows[rowIndex].withIndex().maxOf { (colIndex, cell) ->
                cell.maxIntrinsicHeight(widths[colIndex])
            }
        }

    private fun List<List<IntrinsicMeasurable>>.columnMaxOf(value: (IntrinsicMeasurable) -> Int): IntArray =
        IntArray(columnCount) { colIndex -> maxOf { row -> value(row[colIndex]) } }
}

/**
 * Column widths for a table [available] pixels wide, like a browser's automatic table layout. Each column's
 * content has a narrowest width, its longest word, and a widest, its text on one line.
 * - When every column fits at its widest, they get that, and the space left over in proportion to it.
 * - Otherwise, when they fit at their narrowest, they get that, and the space left over in proportion to how much
 *   wider they would like to be. Short and empty columns stay narrow, and text wraps only between words.
 * - Otherwise the table is too wide, so each column gets its narrowest width and the table scrolls.
 *
 * Unlike a browser, the longest words can be broken inside the word: to make the table fit when that leaves them at
 * least [LONG_WORD_MIN_SHARE] of the width each, and otherwise where they're wider than [SCROLLING_COLUMN_MAX_SHARE]
 * of it, so a column is never wider than the screen.
 */
internal fun distributeColumnWidths(
    longestWords: IntArray,
    maxWidths: IntArray,
    available: Int,
): IntArray {
    val minWidths = narrowestWidths(longestWords, available)
    val minTotal = minWidths.sum()
    val maxTotal = maxWidths.sum()
    return when {
        maxTotal <= available -> {
            grow(maxWidths, weights = maxWidths, extra = available - maxTotal)
        }

        minTotal <= available -> {
            grow(
                minWidths,
                weights = IntArray(minWidths.size) { maxWidths[it] - minWidths[it] },
                extra =
                    available - minTotal,
            )
        }

        else -> {
            minWidths
        }
    }
}

// Each column's narrowest width: its longest word, unless distributeColumnWidths breaks that word.
private fun narrowestWidths(
    longestWords: IntArray,
    available: Int,
): IntArray {
    if (longestWords.sum() <= available) return longestWords
    val fittingCap = widestFittingCap(longestWords, available)
    val cap = if (fittingCap >= available * LONG_WORD_MIN_SHARE) fittingCap else (available * SCROLLING_COLUMN_MAX_SHARE).toInt()
    return IntArray(longestWords.size) { minOf(longestWords[it], cap) }
}

// The widest cap on the words that lets them all fit side by side in available.
private fun widestFittingCap(
    words: IntArray,
    available: Int,
): Int {
    val sorted = words.sorted()
    var left = available
    sorted.forEachIndexed { i, word ->
        val columnsLeft = sorted.size - i
        if (word * columnsLeft > left) return left / columnsLeft
        left -= word
    }
    return Int.MAX_VALUE
}

// Adds extra to widths in proportion to weights, or evenly when there are none, so the widths add up exactly.
private fun grow(
    widths: IntArray,
    weights: IntArray,
    extra: Int,
): IntArray {
    val even = weights.all { it == 0 }
    val totalWeight = if (even) widths.size.toLong() else weights.sumOf { it.toLong() }
    var weightSoFar = 0L
    var given = 0
    return IntArray(widths.size) { i ->
        weightSoFar += if (even) 1 else weights[i]
        val upToHere = (extra * weightSoFar / totalWeight).toInt()
        val share = upToHere - given
        given = upToHere
        widths[i] + share
    }
}
