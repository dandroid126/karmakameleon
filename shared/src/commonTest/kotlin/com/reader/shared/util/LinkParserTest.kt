package com.reader.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkParserTest {

    // ==================== isImageUrl ====================

    @Test
    fun isImageUrl_jpg() {
        assertTrue(isImageUrl("https://i.imgur.com/abc.jpg"))
    }

    @Test
    fun isImageUrl_jpeg() {
        assertTrue(isImageUrl("https://example.com/photo.jpeg"))
    }

    @Test
    fun isImageUrl_png() {
        assertTrue(isImageUrl("https://example.com/image.png"))
    }

    @Test
    fun isImageUrl_webp() {
        assertTrue(isImageUrl("https://example.com/image.webp"))
    }

    @Test
    fun isImageUrl_gif() {
        assertTrue(isImageUrl("https://example.com/anim.gif"))
    }

    @Test
    fun isImageUrl_bmp() {
        assertTrue(isImageUrl("https://example.com/image.bmp"))
    }

    @Test
    fun isImageUrl_avif() {
        assertTrue(isImageUrl("https://example.com/image.avif"))
    }

    @Test
    fun isImageUrl_withQueryParams() {
        assertTrue(isImageUrl("https://example.com/image.jpg?width=100"))
    }

    @Test
    fun isImageUrl_withFragment() {
        assertTrue(isImageUrl("https://example.com/image.png#section"))
    }

    @Test
    fun isImageUrl_caseInsensitive() {
        assertTrue(isImageUrl("https://example.com/image.JPG"))
    }

    @Test
    fun isImageUrl_notImage() {
        assertFalse(isImageUrl("https://example.com/page.html"))
    }

    @Test
    fun isImageUrl_noExtension() {
        assertFalse(isImageUrl("https://example.com/image"))
    }

    @Test
    fun isImageUrl_mp4NotImage() {
        assertFalse(isImageUrl("https://example.com/video.mp4"))
    }

    // ==================== isVideoUrl ====================

    @Test
    fun isVideoUrl_mp4() {
        assertTrue(isVideoUrl("https://example.com/video.mp4"))
    }

    @Test
    fun isVideoUrl_webm() {
        assertTrue(isVideoUrl("https://example.com/video.webm"))
    }

    @Test
    fun isVideoUrl_withQueryParams() {
        assertTrue(isVideoUrl("https://example.com/video.mp4?quality=hd"))
    }

    @Test
    fun isVideoUrl_notVideo() {
        assertFalse(isVideoUrl("https://example.com/image.jpg"))
    }

    @Test
    fun isVideoUrl_noExtension() {
        assertFalse(isVideoUrl("https://example.com/video"))
    }

    // ==================== extractYouTubeVideoId ====================

    @Test
    fun extractYouTubeVideoId_watchUrl() {
        assertEquals("dQw4w9WgXcQ", extractYouTubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun extractYouTubeVideoId_shortUrl() {
        assertEquals("dQw4w9WgXcQ", extractYouTubeVideoId("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun extractYouTubeVideoId_embedUrl() {
        assertEquals("dQw4w9WgXcQ", extractYouTubeVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
    }

    @Test
    fun extractYouTubeVideoId_vUrl() {
        assertEquals("dQw4w9WgXcQ", extractYouTubeVideoId("https://www.youtube.com/v/dQw4w9WgXcQ"))
    }

    @Test
    fun extractYouTubeVideoId_shortsUrl() {
        assertEquals("dQw4w9WgXcQ", extractYouTubeVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
    }

    @Test
    fun extractYouTubeVideoId_mobileUrl() {
        assertEquals("dQw4w9WgXcQ", extractYouTubeVideoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun extractYouTubeVideoId_noCookieUrl() {
        assertEquals("dQw4w9WgXcQ", extractYouTubeVideoId("https://youtube-nocookie.com/embed/dQw4w9WgXcQ"))
    }

    @Test
    fun extractYouTubeVideoId_nonYouTubeUrl() {
        assertNull(extractYouTubeVideoId("https://example.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun extractYouTubeVideoId_invalidUrl() {
        assertNull(extractYouTubeVideoId("not a url"))
    }

    @Test
    fun extractYouTubeVideoId_emptyVideoId() {
        assertNull(extractYouTubeVideoId("https://www.youtube.com/watch?v="))
    }

    // ==================== isYouTubeUrl ====================

    @Test
    fun isYouTubeUrl_validUrl() {
        assertTrue(isYouTubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun isYouTubeUrl_invalidUrl() {
        assertFalse(isYouTubeUrl("https://example.com"))
    }

    // ==================== parseRedditLink ====================

    @Test
    fun parseRedditLink_subredditUrl() {
        val result = parseRedditLink("https://www.reddit.com/r/kotlin")
        assertTrue(result is RedditLink.Subreddit)
        assertEquals("kotlin", result.name)
    }

    @Test
    fun parseRedditLink_subredditRelativeUrl() {
        val result = parseRedditLink("/r/kotlin")
        assertTrue(result is RedditLink.Subreddit)
        assertEquals("kotlin", result.name)
    }

    @Test
    fun parseRedditLink_userUrl() {
        val result = parseRedditLink("https://www.reddit.com/u/testuser")
        assertTrue(result is RedditLink.User)
        assertEquals("testuser", result.name)
    }

    @Test
    fun parseRedditLink_userUrlLong() {
        val result = parseRedditLink("https://www.reddit.com/user/testuser")
        assertTrue(result is RedditLink.User)
        assertEquals("testuser", result.name)
    }

    @Test
    fun parseRedditLink_userRelativeUrl() {
        val result = parseRedditLink("/u/testuser")
        assertTrue(result is RedditLink.User)
        assertEquals("testuser", result.name)
    }

    @Test
    fun parseRedditLink_postUrl() {
        val result = parseRedditLink("https://www.reddit.com/r/kotlin/comments/abc123/some_title")
        assertTrue(result is RedditLink.Post)
        assertEquals("kotlin", result.subreddit)
        assertEquals("abc123", result.postId)
    }

    @Test
    fun parseRedditLink_commentUrl() {
        val result = parseRedditLink("https://www.reddit.com/r/kotlin/comments/abc123/some_title/def456")
        assertTrue(result is RedditLink.Comment)
        assertEquals("kotlin", result.subreddit)
        assertEquals("abc123", result.postId)
        assertEquals("def456", result.commentId)
    }

    @Test
    fun parseRedditLink_commentUrlWithContext() {
        val result = parseRedditLink("https://www.reddit.com/r/kotlin/comments/abc123/some_title/def456?context=3")
        assertTrue(result is RedditLink.Comment)
        assertEquals("kotlin", result.subreddit)
        assertEquals("abc123", result.postId)
        assertEquals("def456", result.commentId)
        assertEquals(3, result.context)
    }

    @Test
    fun parseRedditLink_oldRedditUrl() {
        val result = parseRedditLink("https://old.reddit.com/r/kotlin")
        assertTrue(result is RedditLink.Subreddit)
        assertEquals("kotlin", result.name)
    }

    @Test
    fun parseRedditLink_newRedditUrl() {
        val result = parseRedditLink("https://new.reddit.com/r/kotlin")
        assertTrue(result is RedditLink.Subreddit)
        assertEquals("kotlin", result.name)
    }

    @Test
    fun parseRedditLink_npRedditUrl() {
        val result = parseRedditLink("https://np.reddit.com/r/kotlin")
        assertTrue(result is RedditLink.Subreddit)
        assertEquals("kotlin", result.name)
    }

    @Test
    fun parseRedditLink_externalUrl() {
        val result = parseRedditLink("https://example.com/page")
        assertTrue(result is RedditLink.External)
        assertEquals("https://example.com/page", result.url)
    }

    @Test
    fun parseRedditLink_invalidUrl() {
        val result = parseRedditLink("not a url")
        assertTrue(result is RedditLink.External)
    }

    @Test
    fun parseRedditLink_redditHomepage() {
        val result = parseRedditLink("https://www.reddit.com/")
        assertTrue(result is RedditLink.External)
    }
}
