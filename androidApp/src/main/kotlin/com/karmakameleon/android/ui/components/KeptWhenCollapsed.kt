package com.karmakameleon.android.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.roundToInt

// Expands and collapses its content like AnimatedVisibility's expandVertically and shrinkVertically, but keeps it
// composed and measured while it's collapsed, so expanding it again only places it. The content is measured as if it
// were expanded, and the animation only changes how much of it shows. While collapsed it isn't placed, so it isn't
// drawn, tapped or read by screen readers.
@Composable
internal fun KeptWhenCollapsed(
    isExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    // The same spring AnimatedVisibility uses, so it moves together with AnimatedVisibility content around it.
    val shown by animateFloatAsState(if (isExpanded) 1f else 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    Box(
        modifier =
            Modifier
                .then(if (isExpanded) Modifier else Modifier.clearAndSetSemantics {})
                .clipToBounds()
                // Reads the animation while laying out, so each frame of it lays out again without composing again.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val height = (placeable.height * shown).roundToInt()
                    layout(placeable.width, height) { if (height > 0) placeable.place(0, 0) }
                },
    ) {
        content()
    }
}
