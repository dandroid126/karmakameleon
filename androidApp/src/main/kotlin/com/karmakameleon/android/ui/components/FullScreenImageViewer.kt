package com.karmakameleon.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.karmakameleon.android.ui.theme.mediaColors

@Composable
fun FullScreenImageViewer(
    imageUrls: List<String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    BackHandler(onBack = onDismiss)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(mediaColors().mediaBackgroundColor),
    ) {
        if (imageUrls.size == 1) {
            ZoomableImage(url = imageUrls[0], modifier = Modifier.fillMaxSize())
        } else {
            ImagePager(imageUrls = imageUrls, initialPage = initialPage)
        }
        IconButton(
            onClick = onDismiss,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = mediaColors().onOverlayColor,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

// Swipe between images, with "page / count" at the bottom.
@Composable
private fun BoxScope.ImagePager(
    imageUrls: List<String>,
    initialPage: Int,
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { imageUrls.size })
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        ZoomableImage(url = imageUrls[page], modifier = Modifier.fillMaxSize())
    }
    Box(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .background(mediaColors().pageIndicatorBackgroundColor, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = "${pagerState.currentPage + 1} / ${imageUrls.size}",
            color = mediaColors().onOverlayColor,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 3f

// How far an image is zoomed (1x to 5x) and panned. It can only be panned while zoomed in, and not past its edges.
@Stable
private class ImageZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    fun transform(
        pan: Offset,
        zoom: Float,
        size: IntSize,
    ) {
        val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        offset = if (newScale > 1f) clamped(offset + pan, newScale, size) else Offset.Zero
        scale = newScale
    }

    // Zooms in, or back out when already zoomed in.
    fun toggleZoom() {
        if (scale > 1f) {
            scale = 1f
            offset = Offset.Zero
        } else {
            scale = DOUBLE_TAP_ZOOM
        }
    }

    private fun clamped(
        offset: Offset,
        scale: Float,
        size: IntSize,
    ): Offset {
        val maxX = size.width * (scale - 1) / 2f
        val maxY = size.height * (scale - 1) / 2f
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }
}

// Two fingers zoom and pan; one finger pans while zoomed in. Unzoomed one-finger drags are left for the pager.
private suspend fun PointerInputScope.detectZoomAndPan(state: ImageZoomState) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val isPinch = event.changes.size >= 2
            if (isPinch || state.scale > 1f) {
                state.transform(event.calculatePan(), if (isPinch) event.calculateZoom() else 1f, size)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
private fun ZoomableImage(
    url: String,
    modifier: Modifier = Modifier,
) {
    val zoom = remember(url) { ImageZoomState() }
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier
                .pointerInput(url) { detectZoomAndPan(zoom) }
                .pointerInput(url) {
                    detectTapGestures(onDoubleTap = { zoom.toggleZoom() }, onLongPress = { showMenu = true })
                },
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = zoom.scale,
                        scaleY = zoom.scale,
                        translationX = zoom.offset.x,
                        translationY = zoom.offset.y,
                    ),
            contentScale = ContentScale.Fit,
            loading = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            },
        )
        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
            MediaLongPressMenu(items = imageMenuItems(url), expanded = showMenu, onDismiss = { showMenu = false })
        }
    }
}
