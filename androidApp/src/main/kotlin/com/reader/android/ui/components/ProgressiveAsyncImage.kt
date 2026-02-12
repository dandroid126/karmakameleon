package com.reader.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade

@Composable
fun ProgressiveAsyncImage(
    lowResUrl: String?,
    highResUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current

    // Skip progressive loading if full image is already in memory cache
    val isHighResCached = remember(highResUrl) {
        val cacheKey = MemoryCache.Key(highResUrl)
        SingletonImageLoader.get(context).memoryCache?.get(cacheKey) != null
    }

    if (lowResUrl == null || lowResUrl == highResUrl || isHighResCached) {
        AsyncImage(
            model = highResUrl,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
        return
    }

    var fullImageReady by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Thumbnail layer — loads fast, shown until full-res is ready
        AnimatedVisibility(
            visible = !fullImageReady,
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            AsyncImage(
                model = lowResUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }

        // Full-res layer — crossfades in on top of thumbnail
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(highResUrl)
                .crossfade(300)
                .build(),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            onSuccess = { fullImageReady = true },
        )
    }
}
