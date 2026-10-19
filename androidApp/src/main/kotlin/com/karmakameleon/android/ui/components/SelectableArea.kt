package com.karmakameleon.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Text the user can select. [onTouchStart] runs for each pointer event inside it, before the text sees the event,
 * so the screen knows a selection may be starting. Changing [selectionVersion] clears the selection.
 */
@Composable
fun SelectableArea(
    selectionVersion: Int,
    onTouchStart: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val currentOnTouchStart by rememberUpdatedState(onTouchStart)
    Box(
        modifier =
            modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        currentOnTouchStart()
                    }
                }
            },
    ) {
        key(selectionVersion) {
            SelectionContainer {
                content()
            }
        }
    }
}
