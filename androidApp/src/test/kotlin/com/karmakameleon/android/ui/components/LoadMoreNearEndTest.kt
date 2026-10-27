package com.karmakameleon.android.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoadMoreNearEndTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var items by mutableStateOf(List(20) { it })
    private var isLoadingMore by mutableStateOf(false)
    private var loads = 0

    // Shows the items in a list that fits 4 of them, and starts loading the next page when asked.
    private fun showList() {
        composeRule.setContent {
            val listState = rememberLazyListState()
            LoadMoreNearEnd(listState, items.size, enabled = !isLoadingMore) {
                loads++
                isLoadingMore = true
            }
            LazyColumn(state = listState, modifier = Modifier.height(200.dp).testTag("list")) {
                items(items) { Text("Item $it", Modifier.height(50.dp)) }
            }
        }
    }

    private fun scrollToEnd() {
        composeRule.onNodeWithTag("list").performScrollToIndex(items.lastIndex)
        composeRule.waitForIdle()
    }

    // The view model finishes loading a page, which added these items.
    private fun finishLoading(newItems: List<Int>) {
        composeRule.runOnIdle {
            items = items + newItems
            isLoadingMore = false
        }
        composeRule.waitForIdle()
    }

    @Test
    fun loadsOnlyNearTheEnd() {
        showList()
        assertEquals(0, loads)

        scrollToEnd()
        assertEquals(1, loads)
    }

    @Test
    fun loadsAgainNearTheEndAfterAPageWithFewNewItems() {
        showList()
        scrollToEnd()

        // The second page only repeated items from the first, so the list stays near its end.
        finishLoading(emptyList())
        assertEquals(2, loads)

        finishLoading(listOf(20))
        assertEquals(3, loads)
    }

    @Test
    fun loadsAgainAfterAPageThatMovesTheEndAway() {
        showList()
        scrollToEnd()

        finishLoading(List(20) { 20 + it })
        assertEquals(1, loads)

        scrollToEnd()
        assertEquals(2, loads)
    }
}
