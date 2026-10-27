package com.karmakameleon.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeptWhenCollapsedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var isExpanded by mutableStateOf(true)
    private var compositions = 0

    // 100.dp of content, and a marker below it that shows how much room the content takes.
    private fun show() {
        composeRule.setContent {
            Column {
                KeptWhenCollapsed(isExpanded = isExpanded) {
                    remember { compositions++ }
                    Box(Modifier.height(100.dp)) { Text("Content") }
                }
                Box(Modifier.size(10.dp).testTag("below"))
            }
        }
    }

    private fun belowTop() = composeRule.onNodeWithTag("below").getBoundsInRoot().top

    @Test
    fun collapsingTakesAwayTheContentsRoomAndExpandingGivesItBack() {
        show()
        assertEquals(100.dp, belowTop())

        isExpanded = false
        composeRule.onNodeWithText("Content").assertDoesNotExist()
        assertEquals(0.dp, belowTop())

        isExpanded = true
        composeRule.onNodeWithText("Content").assertExists()
        assertEquals(100.dp, belowTop())
    }

    @Test
    fun contentStaysComposedWhileCollapsed() {
        show()

        isExpanded = false
        composeRule.waitForIdle()
        isExpanded = true
        composeRule.waitForIdle()

        // Composed once, so expanding again doesn't build and lay out all of it again.
        assertEquals(1, compositions)
    }
}
