package com.karmakameleon.android.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.roundToInt

// Expands and collapses its content like AnimatedVisibility's expandVertically and shrinkVertically, but keeps it
// composed and measured while it's collapsed, so expanding it again only places it. The content is measured as if it
// were expanded, and the animation only changes how much of it shows. While collapsed it isn't placed, so it isn't
// drawn, tapped or read by screen readers.
//
// The content gets isShowing, which is true while any of it shows, including during the animation. Parts that
// shouldn't stay composed while collapsed, like a video that would keep playing, can be left out when it's false.
// They then leave composition once the content is fully collapsed, so they still shrink together with the rest.
@Composable
internal fun KeptWhenCollapsed(
    isExpanded: Boolean,
    content: @Composable (isShowing: Boolean) -> Unit,
) {
    // The same spring AnimatedVisibility uses.
    val shown by animateFloatAsState(if (isExpanded) 1f else 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    val currentIsExpanded by rememberUpdatedState(isExpanded)
    // Only changes when the content starts expanding or finishes collapsing, so the animation doesn't compose again.
    val isShowing by remember { derivedStateOf { currentIsExpanded || shown > 0f } }
    Box(
        modifier =
            Modifier
                .then(if (isExpanded) Modifier else Modifier.clearAndSetSemantics {})
                .clipToBounds()
                // Reads the animation while laying out, so each frame of it lays out again without composing again.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val height = (placeable.height * shown).roundToInt()
                    // Shows the bottom of the content, like shrinkVertically, so the part on screen moves the whole
                    // time. Showing the top instead, a body taller than the screen would stay still until its bottom
                    // edge came into view, and then jump.
                    layout(placeable.width, height) { if (height > 0) placeable.place(0, height - placeable.height) }
                },
    ) {
        content(isShowing)
    }
}
