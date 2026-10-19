package com.karmakameleon.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.karmakameleon.android.ui.theme.mediaColors

@Composable
fun FullScreenVideoScreen(
    videoUrl: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackClick)
    var zoom by remember { mutableStateOf(VideoZoom()) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(mediaColors().mediaBackgroundColor)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ -> zoom = zoom.transformed(pan.x, pan.y, gestureZoom) }
                }.pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { zoom = VideoZoom() })
                },
    ) {
        VideoPlayer(
            videoUrl = videoUrl,
            isGif = false,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .zoomedBy(zoom),
            menuItems = videoMenuItems(videoUrl),
        )

        IconButton(
            onClick = onBackClick,
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
