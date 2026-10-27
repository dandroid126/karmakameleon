package com.karmakameleon.android.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.data.repository.MarkdownRepository
import com.karmakameleon.shared.domain.markdown.MarkdownBlock
import com.karmakameleon.shared.util.markdown.parseMarkdownBlocks
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

// Native graphics, so text has its real width.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TableColumnWidthsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val navigation = NavigationRule()

    private val widths get() = GlobalContext.get().get<TableColumnWidths>()

    private val plainEnvironment =
        TableTextEnvironment(
            TextStyle.Default,
            Density(1f),
            LayoutDirection.Ltr,
            createFontFamilyResolver(RuntimeEnvironment.getApplication()),
        )

    // Styles, links, a spoiler, superscript, an image, an empty and a missing cell, a table too wide for the screen,
    // which needs its narrowest widths, and one in a quote, which is in italics.
    private val markdown =
        """
        | Name | **Bold** | `code` |
        |:--|:-:|--:|
        | [link](https://example.com) | >!spoiler!< | x^2 |
        | ![img](https://example.com/a.png) | | *italic* |
        | short |

        | Team | Name | Pos | GP | G | A | Pts | +/- | PIM | S | S% | ATOI | FO% |
        |:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
        | [EDM](/r/edmontonoilers) | Connor McDavid | C | 12 | 8 | 26 | 34 | 16 | 2 | 30 | 26.67 | 23:53 | 47.14 |

        > | Quoted | table |
        > |---|---|
        > | in | italics |
        """.trimIndent()

    // Shows the markdown, then checks that each table's widths, which it measured from its cells and kept, are what
    // measureColumns measures for it.
    private fun assertMeasuredLikeTheCells(
        fontScale: Float = 1f,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        lateinit var environment: (TextStyle) -> TableTextEnvironment
        lateinit var style: TextStyle
        composeRule.setContent {
            val density = Density(LocalDensity.current.density, fontScale)
            KarmaKameleonTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalDensity provides density, LocalLayoutDirection provides layoutDirection) {
                    style = MaterialTheme.typography.bodyMedium
                    val resolver = LocalFontFamilyResolver.current
                    environment = { TableTextEnvironment(it, density, layoutDirection, resolver) }
                    MarkdownText(markdown = markdown, style = style, modifier = Modifier.width(360.dp))
                }
            }
        }
        composeRule.waitForIdle()

        val tables = tablesIn(parseMarkdownBlocks(markdown), style)
        assertEquals(3, tables.size)
        for ((table, tableStyle) in tables) {
            // The instance the table was shown with, from MarkdownRepository.
            val shown = shownTable(table)
            val kept = checkNotNull(widths.get(shown, environment(tableStyle))) { "$table wasn't kept" }
            val measured = measureColumns(shown, environment(tableStyle))
            assertArrayEquals(measured.widest, kept.widest)
            kept.narrowest?.let { assertArrayEquals(measured.narrowest, it) }
        }
        // The wide table doesn't fit, so it measured its narrowest widths too.
        assertNotNull(widths.get(shownTable(tables[1].first), environment(tables[1].second))!!.narrowest)
    }

    private fun shownTable(table: MarkdownBlock.Table): MarkdownBlock.Table {
        val repository = GlobalContext.get().get<MarkdownRepository>()
        val blocks = repository.cached(markdown)!!
        return (blocks + blocks.filterIsInstance<MarkdownBlock.BlockQuote>().flatMap { it.children })
            .filterIsInstance<MarkdownBlock.Table>()
            .single { it == table }
    }

    @Test
    fun widthsMeasuredAheadAreTheCellsWidths() = assertMeasuredLikeTheCells()

    @Test
    fun widthsMeasuredAheadAreTheCellsWidths_withLargeFont() = assertMeasuredLikeTheCells(fontScale = 1.5f)

    @Test
    fun widthsMeasuredAheadAreTheCellsWidths_rightToLeft() = assertMeasuredLikeTheCells(layoutDirection = LayoutDirection.Rtl)

    @Test
    fun measureTablesAhead_measuresTheTablesBeforeTheyreShown() {
        lateinit var environment: TableTextEnvironment
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                val style = MaterialTheme.typography.bodyMedium
                environment =
                    TableTextEnvironment(style, LocalDensity.current, LocalLayoutDirection.current, LocalFontFamilyResolver.current)
                MeasureTablesAhead(key = markdown, style = style) { listOf(markdown) }
            }
        }
        composeRule.waitForIdle()

        val table =
            GlobalContext
                .get()
                .get<MarkdownRepository>()
                .cached(markdown)!!
                .filterIsInstance<MarkdownBlock.Table>()
                .first()
        val measured = checkNotNull(widths.get(table, environment))
        assertArrayEquals(measureColumns(table, environment).widest, measured.widest)
    }

    @Test
    fun keepsOnlyTheMostRecentlyUsedTables() {
        val widths = TableColumnWidths(maxEntries = 2)
        val environment = plainEnvironment
        val (a, b, c) = List(3) { MarkdownBlock.Table(header = emptyList(), rows = emptyList()) }
        val columns = ColumnWidths(IntArray(0), null)
        widths.put(a, environment, columns)
        widths.put(b, environment, columns)
        widths.get(a, environment)
        widths.put(c, environment, columns)

        // b was used least recently, and equal tables are kept apart.
        assertNull(widths.get(b, environment))
        assertNotNull(widths.get(a, environment))
        assertNotNull(widths.get(c, environment))
    }

    @Test
    fun measure_skipsTablesMeasuredInFull() {
        val widths = TableColumnWidths(maxEntries = 2)
        val table = parseMarkdownBlocks("| a |\n|---|\n| b |").filterIsInstance<MarkdownBlock.Table>().single()
        val environment = plainEnvironment
        val kept = ColumnWidths(intArrayOf(1), intArrayOf(1))
        widths.put(table, environment, kept)

        runBlocking { widths.measure(listOf(table to environment)) }

        assertEquals(kept, widths.get(table, environment))
    }
}
