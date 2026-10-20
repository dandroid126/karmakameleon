package com.karmakameleon.android.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

private const val MAX_VIDEO_ZOOM = 5f

/** How far a fullscreen video is zoomed (1x to 5x) and panned. Panning only applies while zoomed in. */
internal data class VideoZoom(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    fun transformed(
        panX: Float,
        panY: Float,
        gestureZoom: Float,
    ): VideoZoom {
        val newScale = (scale * gestureZoom).coerceIn(1f, MAX_VIDEO_ZOOM)
        return if (newScale > 1f) VideoZoom(newScale, offsetX + panX, offsetY + panY) else VideoZoom(newScale)
    }
}

internal fun Modifier.zoomedBy(zoom: VideoZoom): Modifier =
    graphicsLayer {
        scaleX = zoom.scale
        scaleY = zoom.scale
        translationX = zoom.offsetX
        translationY = zoom.offsetY
    }

// Pinch to zoom and pan, double tap to reset. Single taps go to onTap.
internal fun Modifier.videoZoomGestures(
    zoom: MutableState<VideoZoom>,
    onTap: ((Offset) -> Unit)? = null,
): Modifier =
    pointerInput(Unit) {
        detectTransformGestures { _, pan, gestureZoom, _ -> zoom.value = zoom.value.transformed(pan.x, pan.y, gestureZoom) }
    }.pointerInput(Unit) {
        detectTapGestures(onTap = onTap, onDoubleTap = { zoom.value = VideoZoom() })
    }
