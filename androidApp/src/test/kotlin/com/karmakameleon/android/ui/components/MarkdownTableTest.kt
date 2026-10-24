package com.karmakameleon.android.ui.components

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

// Native graphics, so text has its real width.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownTableTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val navigation = NavigationRule()

    private val calls = mutableListOf<String>()

    private fun show(markdown: String) {
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                MarkdownText(markdown = markdown, onTextClick = { calls += "text" })
            }
        }
    }

    private val table = SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)

    private fun scrollRange() =
        composeRule
            .onNode(table)
            .fetchSemanticsNode()
            .config[SemanticsProperties.HorizontalScrollAxisRange]

    @Test
    fun fittingAtWidestGivesLeftoverInProportion() {
        assertArrayEquals(intArrayOf(150, 50), distributeColumnWidths(intArrayOf(10, 10), intArrayOf(60, 20), available = 200))
    }

    @Test
    fun fittingAtNarrowestGivesLeftoverToColumnsThatWantIt() {
        // The second column is as wide as it wants to be, so it gets none of the extra 30.
        assertArrayEquals(intArrayOf(68, 20, 12), distributeColumnWidths(intArrayOf(50, 20, 0), intArrayOf(110, 20, 40), available = 100))
    }

    @Test
    fun tooWideUsesLongestWords() {
        assertArrayEquals(
            intArrayOf(30, 30, 30, 30),
            distributeColumnWidths(intArrayOf(30, 30, 30, 30), intArrayOf(50, 50, 50, 50), available = 100),
        )
    }

    @Test
    fun longWordTakesWhatOtherColumnsLeave() {
        assertArrayEquals(
            intArrayOf(50, 20, 130),
            distributeColumnWidths(intArrayOf(50, 20, 300), intArrayOf(60, 20, 300), available = 200),
        )
    }

    // The other columns leave the long word less than a third of the width, so the table scrolls, and the word breaks
    // only where it's wider than 60% of the width.
    @Test
    fun tooWideCapsLongWords() {
        assertArrayEquals(
            intArrayOf(40, 40, 40, 90),
            distributeColumnWidths(intArrayOf(40, 40, 40, 300), intArrayOf(40, 40, 40, 300), available = 150),
        )
    }

    @Test
    fun emptyColumnsShareLeftoverEvenly() {
        assertArrayEquals(intArrayOf(33, 33, 34), distributeColumnWidths(intArrayOf(0, 0, 0), intArrayOf(0, 0, 0), available = 100))
    }

    @Test
    fun narrowTableDoesNotScroll() {
        show("| A | B |\n|---|---|\n| 1 | 2 |")
        assertEquals(0f, scrollRange().maxValue(), 0f)
    }

    @Test
    fun wideTableScrollsSideways() {
        val header = (1..12).joinToString("|", "|", "|") { "Column$it" }
        val divider = (1..12).joinToString("|", "|", "|") { "---" }
        show("$header\n$divider")
        assertTrue(scrollRange().maxValue() > 0f)

        composeRule.onNode(table).performTouchInput { swipeLeft() }
        assertTrue(scrollRange().value() > 0f)

        composeRule.onNode(table).performTouchInput { swipeRight() }
        assertEquals(0f, scrollRange().value(), 0f)
    }

    // The table calls updateMaxOffset while measuring, so a state it read there would make the table measure again,
    // and reading offset would do that on every scroll frame.
    @Test
    fun updatingMaxOffsetReadsNoState() {
        val scroll = TableScroll()
        scroll.updateMaxOffset(100)
        scroll.state.dispatchRawDelta(50f)

        val reads = mutableListOf<Any>()
        Snapshot.observe(readObserver = { reads += it }) { scroll.updateMaxOffset(20) }

        assertEquals(emptyList<Any>(), reads)
        assertEquals(20f, scroll.offset, 0f)
    }

    // A quote asks for its content's intrinsic height, which the table has to answer without being measured.
    @Test
    fun tableInQuote() {
        show("> | A | B |\n> |---|---|\n> | 1 | 2 |")
        composeRule.onNodeWithText("A").performClick()
        assertEquals(listOf("text"), calls)
    }
}
