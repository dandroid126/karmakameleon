package com.karmakameleon.android.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@Composable
fun YouTubePlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        factory = { context ->
            YouTubePlayerView(context).apply {
                enableAutomaticInitialization = false

                val iFramePlayerOptions = IFramePlayerOptions.Builder(context)
                    .controls(1)
                    .fullscreen(1)
                    .build()

                val activity = context as? Activity
                val fullscreenContainer = FrameLayout(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }

                var originalOrientation: Int? = null

                addFullscreenListener(object : FullscreenListener {
                    override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {
                        fullscreenContainer.addView(fullscreenView)
                        activity?.findViewById<ViewGroup>(android.R.id.content)?.addView(fullscreenContainer)
                        originalOrientation = activity?.requestedOrientation
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                        activity?.window?.let { window -> hideSystemBars(window, window.decorView) }
                    }

                    override fun onExitFullscreen() {
                        fullscreenContainer.removeAllViews()
                        activity?.findViewById<ViewGroup>(android.R.id.content)?.removeView(fullscreenContainer)
                        activity?.window?.let { window -> showSystemBars(window, window.decorView) }
                        activity?.requestedOrientation =
                            originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                })

                lifecycleOwner.lifecycle.addObserver(this)

                initialize(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        if (autoPlay) {
                            youTubePlayer.loadVideo(videoId, 0f)
                        } else {
                            youTubePlayer.cueVideo(videoId, 0f)
                        }
                    }
                }, iFramePlayerOptions)
            }
        },
        modifier = modifier
    )
}
