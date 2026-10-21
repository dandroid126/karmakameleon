package com.karmakameleon.android.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationHandlerTest {
    // Each callback records what it was called with, so a test can check that exactly one of them ran.
    private val calls = mutableListOf<String>()

    private val handler =
        NavigationHandler().apply {
            onSubredditClick = { calls += "subreddit $it" }
            onUserClick = { calls += "user $it" }
            onExternalLinkClick = { calls += "external $it" }
            onPostClick = { subreddit, postId -> calls += "post $subreddit $postId" }
            onCommentClick = { subreddit, postId, commentId, context ->
                calls += "comment $subreddit $postId $commentId $context"
            }
            onShareLinkClick = { subreddit, shareId -> calls += "share $subreddit $shareId" }
            onImageLinkClick = { calls += "image $it" }
            onVideoLinkClick = { calls += "video $it" }
            onYouTubeLinkClick = { calls += "youtube $it" }
        }

    private fun assertHandledAs(
        url: String,
        expected: String,
    ) {
        handler.handleLink(url)
        assertEquals(listOf(expected), calls)
    }

    @Test
    fun imageLinkOpensImage() = assertHandledAs("https://i.redd.it/abc.jpg", "image https://i.redd.it/abc.jpg")

    @Test
    fun videoLinkOpensVideo() = assertHandledAs("https://example.com/clip.mp4", "video https://example.com/clip.mp4")

    @Test
    fun youTubeLinkOpensYouTube() = assertHandledAs("https://www.youtube.com/watch?v=abc", "youtube https://www.youtube.com/watch?v=abc")

    @Test
    fun subredditLinkOpensSubreddit() = assertHandledAs("https://www.reddit.com/r/kotlin", "subreddit kotlin")

    @Test
    fun userLinkOpensUser() = assertHandledAs("/u/testuser", "user testuser")

    @Test
    fun postLinkOpensPost() = assertHandledAs("https://www.reddit.com/r/kotlin/comments/abc123/some_title", "post kotlin abc123")

    @Test
    fun commentLinkOpensCommentWithContext() =
        assertHandledAs(
            "https://www.reddit.com/r/kotlin/comments/abc123/some_title/def456?context=3",
            "comment kotlin abc123 def456 3",
        )

    @Test
    fun shareLinkOpensShareLink() = assertHandledAs("https://www.reddit.com/r/hockey/s/5fboTbvlcR", "share hockey 5fboTbvlcR")

    @Test
    fun otherLinkOpensExternally() = assertHandledAs("https://example.com/page", "external https://example.com/page")
}
