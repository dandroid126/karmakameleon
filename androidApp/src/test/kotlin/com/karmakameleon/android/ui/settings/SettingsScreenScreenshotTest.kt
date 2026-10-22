package com.karmakameleon.android.ui.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.captureScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeReddit = FakeRedditRule()

    @Test
    fun settings() {
        composeRule.captureScreen("settings_screen") { SettingsScreen(onBackClick = {}) }
    }

    @Test
    fun settingsDark() {
        composeRule.captureScreen("settings_screen_dark", darkTheme = true) { SettingsScreen(onBackClick = {}) }
    }
}
