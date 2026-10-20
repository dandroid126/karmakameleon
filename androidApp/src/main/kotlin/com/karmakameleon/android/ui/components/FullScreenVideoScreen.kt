package com.karmakameleon.android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun FullScreenVideoScreen(
    videoUrl: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoom = remember { mutableStateOf(VideoZoom()) }

    MediaScreen(onClose = onBackClick, modifier = modifier.videoZoomGestures(zoom)) {
        VideoPlayer(
            videoUrl = videoUrl,
            isGif = false,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .zoomedBy(zoom.value),
            menuItems = videoMenuItems(videoUrl),
        )
    }
}
