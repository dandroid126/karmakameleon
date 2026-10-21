package com.karmakameleon.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.android.ui.theme.postFlairColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

// Flair colors as Reddit sends them. Subreddits pick any color, so the text has to stay readable on light and dark
// ones alike, and a flair without a usable color falls back to a neutral chip.
private val subredditFlairBackgrounds =
    listOf(
        "White" to "#ffffff",
        "Black" to "#000000",
        "Yellow" to "#ffd635",
        "Orange" to "#ff4500",
        "Blue" to "#0079d3",
        "Mid gray" to "#7f7f7f",
        "No color" to null,
        "Transparent" to "transparent",
    )

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlairChipScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun flairChipsLight() = captureFlairChips(darkTheme = false, name = "flair_chips_light")

    @Test
    fun flairChipsDark() = captureFlairChips(darkTheme = true, name = "flair_chips_dark")

    private fun captureFlairChips(
        darkTheme: Boolean,
        name: String,
    ) {
        composeRule.setContent {
            // Dynamic colors come from the wallpaper, which would make the screenshots depend on the machine.
            KarmaKameleonTheme(darkTheme = darkTheme, dynamicColor = false) {
                // The post card's background, which the chips are drawn on.
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val colors = postFlairColors()
                            FlairChip(text = "NSFW", color = colors.nsfwColor)
                            FlairChip(text = "Spoiler", color = colors.spoilerColor)
                            FlairChip(text = "Pinned", color = colors.pinnedColor)
                        }
                        subredditFlairBackgrounds.forEach { (text, color) ->
                            SubredditFlairChip(text = text, backgroundColor = color)
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
