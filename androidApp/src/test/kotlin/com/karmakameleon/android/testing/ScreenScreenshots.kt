package com.karmakameleon.android.testing

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme

/** Shows [screen] at a phone's size and saves a screenshot of it as `src/test/screenshots/<name>.png`. */
fun ComposeContentTestRule.captureScreen(
    name: String,
    darkTheme: Boolean = false,
    screen: @Composable () -> Unit,
) {
    showScreen(darkTheme, screen)
    saveScreenshot(name)
}

/** Shows [screen] at a phone's size, for tests that do something on it before [saveScreenshot]. */
fun ComposeContentTestRule.showScreen(
    darkTheme: Boolean = false,
    screen: @Composable () -> Unit,
) {
    setContent {
        // Dynamic colors come from the wallpaper, which would make the screenshots depend on the machine.
        KarmaKameleonTheme(darkTheme = darkTheme, dynamicColor = false) { screen() }
    }
}

/** Saves a screenshot of what's shown as `src/test/screenshots/<name>.png`. */
fun ComposeContentTestRule.saveScreenshot(name: String) {
    onRoot().captureRoboImage("src/test/screenshots/$name.png")
}

/**
 * Clicks the node without touching the screen, for a click just before [saveScreenshot]. A touch starts a ripple, and
 * on Robolectric the ripple keeps growing instead of fading out, so it would show in the screenshot.
 */
fun SemanticsNodeInteraction.clickWithoutRipple() {
    performSemanticsAction(SemanticsActions.OnClick)
}

/** Like [saveScreenshot], for a screen showing a dialog, which is a window of its own over the screen's. */
@OptIn(ExperimentalRoborazziApi::class)
fun saveScreenshotWithDialog(name: String) {
    captureScreenRoboImage("src/test/screenshots/$name.png")
}
