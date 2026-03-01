package com.reader.android.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.reader.android.data.VideoCacheProvider
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUrl: String,
    isGif: Boolean,
    modifier: Modifier = Modifier,
    menuItems: List<MediaMenuItem> = emptyList()
) {
    val context = LocalContext.current

    var isOverlayVisible by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(isGif) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isFullscreen by remember { mutableStateOf(false) }
    var inlineViewKey by remember { mutableIntStateOf(0) }
    var videoAspectRatio by remember { mutableStateOf(16f / 9f) }
    var showMenu by remember { mutableStateOf(false) }
    val hasRetriedWithoutAudio = remember { mutableStateOf(false) }

    val exoPlayer = remember {
        val cache = VideoCacheProvider.getCache(context)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build().apply {
            val cleanUrl = videoUrl.replace(Regex("\\?.*"), "")
            if (cleanUrl.contains("v.redd.it")) {
                val baseUrl = cleanUrl.substringBeforeLast("/")
                val hlsUrl = "$baseUrl/HLSPlaylist.m3u8"
                setMediaItem(MediaItem.fromUri(hlsUrl))
            } else {
                setMediaItem(MediaItem.fromUri(videoUrl))
            }
            repeatMode = if (isGif) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!hasRetriedWithoutAudio.value) {
                    hasRetriedWithoutAudio.value = true
                    exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Update position periodically while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(200L)
        }
    }

    // Auto-hide overlay after 2 seconds of no interaction
    LaunchedEffect(lastInteractionTime, isOverlayVisible) {
        if (isOverlayVisible) {
            delay(2000L)
            if (System.currentTimeMillis() - lastInteractionTime >= 2000L) {
                isOverlayVisible = false
            }
        }
    }

    // Sync mute state
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    // Sync loop state
    LaunchedEffect(isLooping) {
        exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (isOverlayVisible) {
                        isOverlayVisible = false
                    } else {
                        isOverlayVisible = true
                        lastInteractionTime = System.currentTimeMillis()
                    }
                },
                onLongClick = { if (menuItems.isNotEmpty()) showMenu = true }
            )
    ) {
        key(inlineViewKey) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).also { textureView ->
                        exoPlayer.setVideoTextureView(textureView)
                    }
                },
                onRelease = {
                    exoPlayer.clearVideoTextureView(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(videoAspectRatio)
            )
        }

        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
            MediaLongPressMenu(
                items = menuItems,
                expanded = showMenu,
                onDismiss = { showMenu = false }
            )
        }

        // Controls overlay
        AnimatedVisibility(
            visible = isOverlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            VideoControlsOverlay(
                isPlaying = isPlaying,
                isMuted = isMuted,
                isLooping = isLooping,
                isFullscreen = false,
                hasAudio = !isGif && !hasRetriedWithoutAudio.value,
                currentPosition = currentPosition,
                duration = duration,
                onPlayPauseClick = {
                    lastInteractionTime = System.currentTimeMillis()
                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                        exoPlayer.seekTo(0)
                        exoPlayer.play()
                    } else if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer.play()
                    }
                },
                onMuteClick = {
                    lastInteractionTime = System.currentTimeMillis()
                    isMuted = !isMuted
                },
                onLoopClick = {
                    lastInteractionTime = System.currentTimeMillis()
                    isLooping = !isLooping
                },
                onSeek = { position ->
                    lastInteractionTime = System.currentTimeMillis()
                    exoPlayer.seekTo(position)
                    currentPosition = position
                },
                onFullscreenClick = {
                    lastInteractionTime = System.currentTimeMillis()
                    isFullscreen = true
                }
            )
        }
    }

    if (isFullscreen) {
        var zoomScale by remember { mutableFloatStateOf(1f) }
        var zoomOffsetX by remember { mutableFloatStateOf(0f) }
        var zoomOffsetY by remember { mutableFloatStateOf(0f) }

        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            // Hide system bars and unlock orientation for true fullscreen
            val dialogView = LocalView.current
            DisposableEffect(Unit) {
                val activity = context as? Activity
                val originalOrientation = activity?.requestedOrientation
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR

                val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
                dialogWindow?.let { window -> hideSystemBars(window, dialogView) }
                onDispose {
                    activity?.requestedOrientation =
                        originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (zoomScale * zoom).coerceIn(1f, 5f)
                            zoomScale = newScale
                            if (newScale > 1f) {
                                zoomOffsetX += pan.x
                                zoomOffsetY += pan.y
                            } else {
                                zoomOffsetX = 0f
                                zoomOffsetY = 0f
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (isOverlayVisible) {
                                    isOverlayVisible = false
                                } else {
                                    isOverlayVisible = true
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            },
                            onDoubleTap = {
                                zoomScale = 1f
                                zoomOffsetX = 0f
                                zoomOffsetY = 0f
                            }
                        )
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).also { textureView ->
                            exoPlayer.setVideoTextureView(textureView)
                        }
                    },
                    onRelease = {
                        exoPlayer.clearVideoTextureView(it)
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .aspectRatio(videoAspectRatio)
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = zoomOffsetX
                            translationY = zoomOffsetY
                        }
                )

                AnimatedVisibility(
                    visible = isOverlayVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    VideoControlsOverlay(
                        isPlaying = isPlaying,
                        isMuted = isMuted,
                        isLooping = isLooping,
                        isFullscreen = true,
                        hasAudio = !isGif && !hasRetriedWithoutAudio.value,
                        currentPosition = currentPosition,
                        duration = duration,
                        onPlayPauseClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                exoPlayer.seekTo(0)
                                exoPlayer.play()
                            } else if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        onMuteClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            isMuted = !isMuted
                        },
                        onLoopClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            isLooping = !isLooping
                        },
                        onSeek = { position ->
                            lastInteractionTime = System.currentTimeMillis()
                            exoPlayer.seekTo(position)
                            currentPosition = position
                        },
                        onFullscreenClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            isFullscreen = false
                            inlineViewKey++
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoControlsOverlay(
    isPlaying: Boolean,
    isMuted: Boolean,
    isLooping: Boolean,
    isFullscreen: Boolean,
    hasAudio: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPauseClick: () -> Unit,
    onMuteClick: () -> Unit,
    onLoopClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onFullscreenClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = if (isFullscreen) 24.dp else 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Timestamp
            Text(
                text = "${formatDuration(currentPosition)} / ${formatDuration(duration)}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play/Pause
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Mute toggle
                IconButton(
                    onClick = if (hasAudio) onMuteClick else { {} },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (!hasAudio || isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (!hasAudio) "No audio available"
                            else if (isMuted) "Unmute" else "Mute",
                        tint = if (!hasAudio) Color.Red else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Loop toggle
                IconButton(
                    onClick = onLoopClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Repeat,
                        contentDescription = if (isLooping) "Disable loop" else "Enable loop",
                        tint = if (isLooping) Color(0xFF4FC3F7) else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Fullscreen toggle
                IconButton(
                    onClick = onFullscreenClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Seek bar
        Slider(
            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
            onValueChange = { fraction ->
                onSeek((fraction * duration).toLong())
            },
            modifier = Modifier.fillMaxWidth().height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
