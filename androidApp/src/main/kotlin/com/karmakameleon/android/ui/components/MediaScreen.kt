package com.karmakameleon.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karmakameleon.android.ui.theme.mediaColors

// Shows media on its own over the whole screen, with a close button in the top corner. Back also closes it.
@Composable
internal fun MediaScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BackHandler(onBack = onClose)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(mediaColors().mediaBackgroundColor),
    ) {
        content()

        IconButton(
            onClick = onClose,
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
