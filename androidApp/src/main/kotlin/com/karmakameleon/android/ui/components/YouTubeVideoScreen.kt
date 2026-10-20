package com.karmakameleon.android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun YouTubeVideoScreen(
    videoId: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MediaScreen(onClose = onBackClick, modifier = modifier) {
        YouTubePlayer(
            videoId = videoId,
            autoPlay = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
        )
    }
}
