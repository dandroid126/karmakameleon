package com.karmakameleon.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.MultiParagraphIntrinsics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.resolveDefaults
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.karmakameleon.shared.data.repository.MarkdownRepository
import com.karmakameleon.shared.domain.markdown.MarkdownBlock
import com.karmakameleon.shared.domain.markdown.MarkdownInline
import com.karmakameleon.shared.domain.markdown.TableAlignment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlin.math.ceil
import kotlin.math.roundToInt

// Each column's widest and narrowest width, in pixels and with the cells' padding: its text on one line, and its
// longest word. narrowest is null when it hasn't been measured, which a table that fits at its widest doesn't need.
internal class ColumnWidths(
    val widest: IntArray,
    val narrowest: IntArray?,
)

// What a table's text is measured with. Its columns are as wide again only when all of these are the same.
internal data class TableTextEnvironment(
    val style: TextStyle,
    val density: Density,
    val layoutDirection: LayoutDirection,
    val fontFamilyResolver: FontFamily.Resolver,
)

/**
 * The widths of tables' columns, so a table doesn't measure all of its cells' text to size its columns each time it's
 * shown. Measuring them takes long enough in a table with many cells, like a sports bot's stats, that a comment
 * scrolling on screen drops frames. [measure] measures them on [measureDispatcher], before the tables are shown, and a
 * table measures them itself, and keeps them, when they aren't ready.
 *
 * Use it from the main thread. Only the measuring runs elsewhere, so the widths kept need no lock. It keeps the widths
 * of the [maxEntries] tables used most recently, by table: the same parsed text gives the same table, from
 * MarkdownRepository's cache, while it's kept there.
 */
internal class TableColumnWidths(
    private val measureDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    // Least recently used first, so the entries past maxEntries are dropped from the front.
    private val widths = LinkedHashMap<Key, ColumnWidths>(16, 0.75f, true)

    fun get(
        table: MarkdownBlock.Table,
        environment: TableTextEnvironment,
    ): ColumnWidths? = widths[Key(table, environment)]

    fun put(
        table: MarkdownBlock.Table,
        environment: TableTextEnvironment,
        columns: ColumnWidths,
    ) {
        widths[Key(table, environment)] = columns
        while (widths.size > maxEntries) widths.remove(widths.keys.first())
    }

    /** Measures the columns of the tables that aren't measured yet, each with the environment it's shown in. */
    suspend fun measure(tables: List<Pair<MarkdownBlock.Table, TableTextEnvironment>>) {
        // Only those not measured yet, or not measured in full, which takes a table's widest widths alone.
        val missing = tables.filter { (table, environment) -> get(table, environment)?.narrowest == null }.distinct()
        if (missing.isEmpty()) return
        val measured = withContext(measureDispatcher) { missing.map { (table, environment) -> measureColumns(table, environment) } }
        missing.zip(measured).forEach { (tableAndEnvironment, columns) ->
            put(tableAndEnvironment.first, tableAndEnvironment.second, columns)
        }
    }

    // The table itself, rather than an equal one, so looking it up doesn't compare all of its cells.
    private class Key(
        val table: MarkdownBlock.Table,
        val environment: TableTextEnvironment,
    ) {
        override fun equals(other: Any?): Boolean = other is Key && other.table === table && other.environment == environment

        override fun hashCode(): Int = 31 * System.identityHashCode(table) + environment.hashCode()
    }

    private companion object {
        // Enough for the tables of a long thread and the posts around it.
        const val DEFAULT_MAX_ENTRIES = 500
    }
}

// The columns' widths the way the table's cells measure them: TableCell's text and style, measured like Text measures
// its intrinsic widths, plus its padding, so a table looks the same whether they were measured here or by its cells.
internal fun measureColumns(
    table: MarkdownBlock.Table,
    environment: TableTextEnvironment,
): ColumnWidths {
    val columnCount = table.header.size
    val padding = with(environment.density) { CELL_PADDING.roundToPx() * 2 }
    val widest = IntArray(columnCount)
    val narrowest = IntArray(columnCount)
    (listOf(table.header) + table.rows).forEachIndexed { rowIndex, row ->
        repeat(columnCount) { colIndex ->
            val style = tableCellStyle(environment.style, isHeader = rowIndex == 0, table.alignment(colIndex))
            val intrinsics = cellIntrinsics(row.getOrNull(colIndex)?.children.orEmpty(), style, environment)
            widest[colIndex] = maxOf(widest[colIndex], intrinsics.maxIntrinsicWidth.ceilToPx() + padding)
            narrowest[colIndex] = maxOf(narrowest[colIndex], intrinsics.minIntrinsicWidth.ceilToPx() + padding)
        }
    }
    return ColumnWidths(widest, narrowest)
}

private fun cellIntrinsics(
    inlines: List<MarkdownInline>,
    style: TextStyle,
    environment: TableTextEnvironment,
): MultiParagraphIntrinsics =
    MultiParagraphIntrinsics(
        annotatedString = tableCellText(inlines),
        style = resolveDefaults(style, environment.layoutDirection),
        placeholders = emptyList(),
        density = environment.density,
        fontFamilyResolver = environment.fontFamilyResolver,
        // Like Text's default, which the cells use.
        softWrap = true,
    )

// Like Text, which rounds its intrinsic widths up to whole pixels.
private fun Float.ceilToPx(): Int = ceil(this).roundToInt()

// A cell's style: the table's, bold in the header, aligned like its column.
internal fun tableCellStyle(
    style: TextStyle,
    isHeader: Boolean,
    alignment: TableAlignment,
): TextStyle = style.copy(fontWeight = if (isHeader) FontWeight.Bold else style.fontWeight, textAlign = alignment.textAlign)

/**
 * Measures the columns of the tables in [texts] in the background, so they're ready by the time the texts scroll on
 * screen. A comment comes on screen a frame or two after it's composed, too soon to measure them then. [texts] are
 * the markdown a screen is about to show in [style], and they're measured again when [key] changes.
 */
@Composable
internal fun MeasureTablesAhead(
    key: Any?,
    style: TextStyle,
    markdownRepository: MarkdownRepository = koinInject(),
    columnWidths: TableColumnWidths = koinInject(),
    texts: () -> List<String>,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val currentTexts by rememberUpdatedState(texts)
    LaunchedEffect(key, style, density, layoutDirection, fontFamilyResolver) {
        // Parsed already, by the repository that loaded them, so this only looks them up.
        val blocks = markdownRepository.parseAll(currentTexts())
        val tables =
            blocks.values.flatMap { textBlocks ->
                tablesIn(textBlocks, style).map { (table, tableStyle) ->
                    table to TableTextEnvironment(tableStyle, density, layoutDirection, fontFamilyResolver)
                }
            }
        columnWidths.measure(tables)
    }
}
