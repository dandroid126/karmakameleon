package com.karmakameleon.android.testing

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme

/** Shows [screen] at a phone's size and saves a screenshot of it as `src/test/screenshots/<name>.png`. */
fun ComposeContentTestRule.captureScreen(
    name: String,
    darkTheme: Boolean = false,
    screen: @Composable () -> Unit,
) {
    setContent {
        // Dynamic colors come from the wallpaper, which would make the screenshots depend on the machine.
        KarmaKameleonTheme(darkTheme = darkTheme, dynamicColor = false) { screen() }
    }
    onRoot().captureRoboImage("src/test/screenshots/$name.png")
}
