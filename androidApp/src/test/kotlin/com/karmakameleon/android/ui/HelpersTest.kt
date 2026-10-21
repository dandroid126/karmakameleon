package com.karmakameleon.android.ui

import com.karmakameleon.android.ui.components.formatDuration
import com.karmakameleon.android.ui.components.formatNumber
import com.karmakameleon.android.ui.components.formatTimeAgo
import com.karmakameleon.android.ui.components.imageFilenameAndMimeType
import com.karmakameleon.android.ui.components.playbackUrl
import com.karmakameleon.android.ui.components.videoExtension
import com.karmakameleon.android.ui.inbox.postOfContextUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// The small helpers behind the UI that don't draw anything.
class HelpersTest {
    private val defaultLocale = Locale.getDefault()

    // formatNumber uses the default locale's decimal separator.
    @Before
    fun setLocale() = Locale.setDefault(Locale.US)

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    @Test
    fun playbackUrlUsesHlsPlaylistForRedditVideos() {
        assertEquals(
            "https://v.redd.it/abc123/HLSPlaylist.m3u8",
            playbackUrl("https://v.redd.it/abc123/DASH_720.mp4?source=fallback"),
        )
    }

    @Test
    fun playbackUrlKeepsOtherVideos() {
        assertEquals("https://example.com/clip.mp4?x=1", playbackUrl("https://example.com/clip.mp4?x=1"))
    }

    @Test
    fun formatDurationShowsMinutesAndPaddedSeconds() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:05", formatDuration(5_999))
        assertEquals("1:05", formatDuration(65_000))
        assertEquals("75:00", formatDuration(4_500_000))
    }

    @Test
    fun postOfContextUrlReturnsSubredditAndPost() {
        assertEquals("kotlin" to "abc123", postOfContextUrl("/r/kotlin/comments/abc123/some_title/def456/?context=3"))
    }

    @Test
    fun postOfContextUrlIsNullWithoutPost() {
        assertNull(postOfContextUrl("/r/kotlin/comments"))
        assertNull(postOfContextUrl(""))
    }

    @Test
    fun imageFilenameAndMimeTypeComeFromUrl() {
        assertEquals("a.png" to "image/png", imageFilenameAndMimeType("https://i.redd.it/a.png?width=100"))
        assertEquals("a.gif" to "image/gif", imageFilenameAndMimeType("https://i.redd.it/a.gif"))
        assertEquals("a.webp" to "image/webp", imageFilenameAndMimeType("https://i.redd.it/a.webp"))
        assertEquals("a.jpeg" to "image/jpeg", imageFilenameAndMimeType("https://i.redd.it/a.jpeg"))
    }

    @Test
    fun imageWithoutExtensionIsSavedAsJpeg() {
        assertEquals("abc.jpg" to "image/jpeg", imageFilenameAndMimeType("https://i.imgur.com/abc"))
    }

    @Test
    fun videoExtensionComesFromUrl() {
        assertEquals("WEBM", videoExtension("https://example.com/clip.webm?x=1"))
        assertEquals("MP4", videoExtension("https://v.redd.it/abc/DASH_720.mp4"))
    }

    @Test
    fun videoWithoutShortExtensionIsMp4() {
        assertEquals("MP4", videoExtension("https://v.redd.it/abc"))
        assertEquals("MP4", videoExtension("https://example.com/clip.toolong"))
        assertEquals("MP4", videoExtension("https://example.com/clip."))
    }

    @Test
    fun formatNumberAbbreviatesThousandsAndMillions() {
        assertEquals("999", formatNumber(999))
        assertEquals("1.0K", formatNumber(1_000))
        assertEquals("12.3K", formatNumber(12_345))
        assertEquals("1.5M", formatNumber(1_500_000))
    }

    @Test
    fun formatTimeAgoUsesLargestWholeUnit() {
        assertEquals("now", formatTimeAgo(secondsAgo(30.seconds)))
        assertEquals("5m", formatTimeAgo(secondsAgo(5.minutes)))
        assertEquals("3h", formatTimeAgo(secondsAgo(3.hours)))
        assertEquals("2d", formatTimeAgo(secondsAgo(2.days)))
        assertEquals("2w", formatTimeAgo(secondsAgo(15.days)))
        assertEquals("3mo", formatTimeAgo(secondsAgo(100.days)))
        assertEquals("2y", formatTimeAgo(secondsAgo(800.days)))
    }

    // A timestamp in seconds, a bit more than [age] ago. Ages only grow while the test runs, so it stays in the same unit.
    private fun secondsAgo(age: Duration): Long = (System.currentTimeMillis().milliseconds - age - 10.seconds).inWholeSeconds
}
