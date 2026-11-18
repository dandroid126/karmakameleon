package com.karmakameleon.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

// For a line of small links, like a card's subreddit or a comment's author, right above or below text that taps through
// to the card, comment or message around it. Compose grows a small link's touch area to 48 dp, which reaches into those
// lines, so a tap on them opened the link instead (#33). Here the links' touch areas still grow sideways, but stay
// within their line.
@Composable
fun InlineLinks(content: @Composable () -> Unit) {
    val viewConfiguration = LocalViewConfiguration.current
    val inLine =
        remember(viewConfiguration) {
            object : ViewConfiguration by viewConfiguration {
                override val minimumTouchTargetSize: DpSize
                    get() = DpSize(viewConfiguration.minimumTouchTargetSize.width, 0.dp)
            }
        }
    CompositionLocalProvider(LocalViewConfiguration provides inLine, content = content)
}
