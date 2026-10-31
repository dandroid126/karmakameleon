package com.karmakameleon.android.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.karmakameleon.android.data.VideoCacheProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoPlayerFullscreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun releaseCache() = VideoCacheProvider.release()

    @Test
    fun fullscreen_followsTheSensorUntilItCloses() {
        var activity: Activity? = null
        composeRule.setContent {
            activity = LocalActivity.current
            VideoPlayer(videoUrl = "https://example.com/video.mp4", isGif = false)
        }
        val before = activity?.requestedOrientation

        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR, activity?.requestedOrientation)

        composeRule.onNodeWithContentDescription("Exit fullscreen").performClick()
        composeRule.waitForIdle()
        assertEquals(before, activity?.requestedOrientation)
    }
}
