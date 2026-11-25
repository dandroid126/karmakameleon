package com.karmakameleon.android.ui.components

import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class ActionErrorsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsEachErrorAsAToast() {
        val errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
        composeRule.setContent { ShowActionErrors(errors) }
        composeRule.waitForIdle()

        errors.tryEmit("Vote failed")
        composeRule.waitForIdle()
        assertEquals("Vote failed", ShadowToast.getTextOfLatestToast())

        errors.tryEmit("Save failed")
        composeRule.waitForIdle()
        assertEquals("Save failed", ShadowToast.getTextOfLatestToast())
    }
}
