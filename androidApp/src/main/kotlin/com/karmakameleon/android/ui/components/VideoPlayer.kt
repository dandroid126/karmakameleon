package com.karmakameleon.android.ui.components

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.karmakameleon.android.data.VideoCacheProvider
import com.karmakameleon.android.ui.theme.mediaColors
import kotlinx.coroutines.delay

@Composable
fun VideoPlayer(
    videoUrl: String,
    isGif: Boolean,
    modifier: Modifier = Modifier,
    menuItems: List<MediaMenuItem> = emptyList(),
) {
    val player = rememberVideoPlayer(videoUrl, isGif)
    val state = remember { VideoPlaybackState(player, isGif) }
    var isFullscreen by remember { mutableStateOf(false) }
    // Bumped when leaving fullscreen, so the inline surface is recreated and takes the video back.
    var inlineViewKey by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    val hasAudio = !isGif && !state.hasRetriedWithoutAudio
    VideoPlayerEffects(state = state, videoUrl = videoUrl)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(mediaColors().mediaBackgroundColor)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = state::toggleOverlay,
                    onLongClick = { if (menuItems.isNotEmpty()) showMenu = true },
                ),
    ) {
        key(inlineViewKey) {
            VideoSurface(player = player, modifier = Modifier.fillMaxWidth().aspectRatio(state.videoAspectRatio))
        }
        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
            MediaLongPressMenu(items = menuItems, expanded = showMenu, onDismiss = { showMenu = false })
        }
        VideoControls(
            state = state,
            isFullscreen = false,
            hasAudio = hasAudio,
            onFullscreenClick = {
                state.touched()
                isFullscreen = true
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (isFullscreen) {
        FullscreenVideoDialog(
            state = state,
            hasAudio = hasAudio,
            onExitFullscreen = {
                state.touched()
                isFullscreen = false
                inlineViewKey++
            },
            onDismiss = { isFullscreen = false },
        )
    }
}

private const val DEFAULT_ASPECT_RATIO = 16f / 9f

// The player's state as the controls see it, and what the controls do. Using a control counts as interaction, which
// keeps the controls from hiding.
@Stable
private class VideoPlaybackState(
    val player: ExoPlayer,
    isGif: Boolean,
) {
    var isOverlayVisible by mutableStateOf(true)
    var isMuted by mutableStateOf(false)
    var isLooping by mutableStateOf(isGif)
    var isPlaying by mutableStateOf(false)
    var currentPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var lastInteractionTime by mutableLongStateOf(System.currentTimeMillis())
    var videoAspectRatio by mutableFloatStateOf(DEFAULT_ASPECT_RATIO)

    // Set when playback failed and the video was retried from its plain URL, which has no audio.
    var hasRetriedWithoutAudio by mutableStateOf(false)

    fun touched() {
        lastInteractionTime = System.currentTimeMillis()
    }

    fun toggleOverlay() {
        isOverlayVisible = !isOverlayVisible
        if (isOverlayVisible) touched()
    }

    // Plays from the start again once the video has ended.
    fun playPause() {
        touched()
        when {
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0)
                player.play()
            }

            player.isPlaying -> {
                player.pause()
            }

            else -> {
                player.play()
            }
        }
    }

    fun toggleMute() {
        touched()
        isMuted = !isMuted
    }

    fun toggleLoop() {
        touched()
        isLooping = !isLooping
    }

    fun seek(position: Long) {
        touched()
        player.seekTo(position)
        currentPosition = position
    }
}

// Starts muted and playing, reading through the video cache. GIFs loop.
@Composable
private fun rememberVideoPlayer(
    videoUrl: String,
    isGif: Boolean,
): ExoPlayer {
    val context = LocalContext.current
    return remember { buildVideoPlayer(context, videoUrl, isGif) }
}

@OptIn(UnstableApi::class)
private fun buildVideoPlayer(
    context: Context,
    videoUrl: String,
    isGif: Boolean,
): ExoPlayer {
    val cacheDataSourceFactory =
        CacheDataSource
            .Factory()
            .setCache(VideoCacheProvider.getCache(context))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    return ExoPlayer
        .Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
        .build()
        .apply {
            setMediaItem(MediaItem.fromUri(playbackUrl(videoUrl)))
            repeatMode = if (isGif) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            volume = 0f
            playWhenReady = true
            prepare()
        }
}

// Reddit videos are played from their HLS playlist, which carries the audio track.
private fun playbackUrl(videoUrl: String): String {
    val cleanUrl = videoUrl.replace(Regex("\\?.*"), "")
    return if (cleanUrl.contains("v.redd.it")) "${cleanUrl.substringBeforeLast("/")}/HLSPlaylist.m3u8" else videoUrl
}

// Keeps the state in step with the player, pauses while the app is in the background, and hides the controls after
// two seconds without interaction.
@Composable
private fun VideoPlayerEffects(
    state: VideoPlaybackState,
    videoUrl: String,
) {
    PlayerListener(state = state, videoUrl = videoUrl)
    PauseInBackground(player = state.player)

    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            state.currentPosition = state.player.currentPosition.coerceAtLeast(0L)
            delay(200L)
        }
    }

    LaunchedEffect(state.lastInteractionTime, state.isOverlayVisible) {
        if (state.isOverlayVisible) {
            delay(2000L)
            if (System.currentTimeMillis() - state.lastInteractionTime >= 2000L) {
                state.isOverlayVisible = false
            }
        }
    }

    SideEffect {
        state.player.volume = if (state.isMuted) 0f else 1f
        state.player.repeatMode = if (state.isLooping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }
}

// Releases the player when it leaves the composition. If playback fails, retries once from the plain URL, which
// works for videos the HLS playlist doesn't.
@Composable
private fun PlayerListener(
    state: VideoPlaybackState,
    videoUrl: String,
) {
    val player = state.player
    DisposableEffect(Unit) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) state.duration = player.duration.coerceAtLeast(0L)
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    state.isPlaying = playing
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        state.videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (!state.hasRetriedWithoutAudio) {
                        state.hasRetriedWithoutAudio = true
                        player.setMediaItem(MediaItem.fromUri(videoUrl))
                        player.prepare()
                        player.play()
                    }
                }
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.stop()
            player.release()
        }
    }
}

// Pause when the app leaves the screen, so the audio doesn't keep playing in the background, and pick up again on
// return if it was playing.
@Composable
private fun PauseInBackground(player: ExoPlayer) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var wasPlaying = false
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        wasPlaying = player.playWhenReady
                        player.pause()
                    }

                    Lifecycle.Event.ON_START -> {
                        if (wasPlaying) player.play()
                    }

                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

// Shows the player's video. Only the most recently created surface gets the video.
@Composable
private fun VideoSurface(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context -> TextureView(context).also { player.setVideoTextureView(it) } },
        onRelease = { player.clearVideoTextureView(it) },
        modifier = modifier,
    )
}

@Composable
private fun VideoControls(
    state: VideoPlaybackState,
    isFullscreen: Boolean,
    hasAudio: Boolean,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.isOverlayVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        VideoControlsOverlay(state = state, isFullscreen = isFullscreen, hasAudio = hasAudio, onFullscreenClick = onFullscreenClick)
    }
}

// The video on its own, filling the screen in any orientation, with pinch to zoom and double tap to reset.
@Composable
private fun FullscreenVideoDialog(
    state: VideoPlaybackState,
    hasAudio: Boolean,
    onExitFullscreen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val zoom = remember { mutableStateOf(VideoZoom()) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        FullscreenWindowSetup()
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(mediaColors().mediaBackgroundColor)
                    .videoZoomGestures(zoom, onTap = { state.toggleOverlay() }),
        ) {
            VideoSurface(
                player = state.player,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .aspectRatio(state.videoAspectRatio)
                        .zoomedBy(zoom.value),
            )
            VideoControls(
                state = state,
                isFullscreen = true,
                hasAudio = hasAudio,
                onFullscreenClick = onExitFullscreen,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

// Hides the system bars and lets the screen rotate while fullscreen, restoring the orientation afterwards.
@Composable
private fun FullscreenWindowSetup() {
    val context = LocalContext.current
    val dialogView = LocalView.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR

        val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
        dialogWindow?.let { window -> hideSystemBars(window, dialogView) }
        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

// The time, play/pause, mute (dimmed when there's no audio), loop and fullscreen buttons, and a seek bar.
@Composable
private fun VideoControlsOverlay(
    state: VideoPlaybackState,
    isFullscreen: Boolean,
    hasAudio: Boolean,
    onFullscreenClick: () -> Unit,
) {
    val mediaColors = mediaColors()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(mediaColors.controlsBackgroundColor)
                .padding(horizontal = if (isFullscreen) 24.dp else 8.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${formatDuration(state.currentPosition)} / ${formatDuration(state.duration)}",
                color = mediaColors.onOverlayColor,
                style = MaterialTheme.typography.labelMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                VideoControlButton(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    onClick = state::playPause,
                )
                VideoControlButton(
                    imageVector =
                        if (!hasAudio ||
                            state.isMuted
                        ) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                    contentDescription = muteDescription(hasAudio, state.isMuted),
                    onClick = { if (hasAudio) state.toggleMute() },
                    tint = if (hasAudio) mediaColors.onOverlayColor else mediaColors.noAudioColor,
                )
                VideoControlButton(
                    imageVector = Icons.Filled.Repeat,
                    contentDescription = if (state.isLooping) "Disable loop" else "Enable loop",
                    onClick = state::toggleLoop,
                    tint = if (state.isLooping) mediaColors.loopActiveColor else mediaColors.onOverlayColor,
                )
                VideoControlButton(
                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                    onClick = onFullscreenClick,
                )
            }
        }
        VideoSeekBar(position = state.currentPosition, duration = state.duration, onSeek = state::seek)
    }
}

private fun muteDescription(
    hasAudio: Boolean,
    isMuted: Boolean,
): String =
    when {
        !hasAudio -> "No audio available"
        isMuted -> "Unmute"
        else -> "Mute"
    }

@Composable
private fun VideoControlButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = mediaColors().onOverlayColor,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(imageVector = imageVector, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun VideoSeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
) {
    val mediaColors = mediaColors()
    Slider(
        value = if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
        onValueChange = { fraction -> onSeek((fraction * duration).toLong()) },
        modifier = Modifier.fillMaxWidth().height(24.dp),
        colors =
            SliderDefaults.colors(
                thumbColor = mediaColors.onOverlayColor,
                activeTrackColor = mediaColors.onOverlayColor,
                inactiveTrackColor = mediaColors.seekBarInactiveTrackColor,
            ),
    )
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
