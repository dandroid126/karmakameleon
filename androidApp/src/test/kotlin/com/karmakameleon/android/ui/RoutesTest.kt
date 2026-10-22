package com.karmakameleon.android.ui

import com.karmakameleon.shared.domain.model.InboxFilter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for android.net.Uri, which encodes the URLs in routes.
@RunWith(RobolectricTestRunner::class)
class RoutesTest {
    @Test
    fun postRouteAddsTheCommentAndItsContext() {
        assertEquals("post/Kotlin/abc", DetailScreen.PostDetail.createRoute("Kotlin", "abc"))
        assertEquals("post/Kotlin/abc?commentId=def", DetailScreen.PostDetail.createRoute("Kotlin", "abc", "def"))
        assertEquals("post/Kotlin/abc?commentId=def&context=3", DetailScreen.PostDetail.createRoute("Kotlin", "abc", "def", 3))
        // Context only means something for a comment.
        assertEquals("post/Kotlin/abc", DetailScreen.PostDetail.createRoute("Kotlin", "abc", context = 3))
    }

    @Test
    fun inboxRouteNamesTheFilter() {
        assertEquals("inbox", DetailScreen.Inbox.createRoute())
        assertEquals("inbox?filter=UNREAD", DetailScreen.Inbox.createRoute(InboxFilter.UNREAD))
    }

    @Test
    fun urlRoutesEncodeTheWholeUrl() {
        val url = "https://example.com/a b+c?x=1&y=%20"
        val encoded = "https%3A%2F%2Fexample.com%2Fa%20b%2Bc%3Fx%3D1%26y%3D%2520"
        assertEquals("web/$encoded", DetailScreen.WebBrowser.createRoute(url))
        assertEquals("image/$encoded", DetailScreen.ImageViewer.createRoute(url))
        assertEquals("video/$encoded", DetailScreen.VideoViewer.createRoute(url))
    }

    @Test
    fun simpleRoutes() {
        assertEquals("subreddit/Kotlin", DetailScreen.SubredditDetail.createRoute("Kotlin"))
        assertEquals("user/someone", DetailScreen.UserProfile.createRoute("someone"))
        assertEquals("youtube/dQw4w9WgXcQ", DetailScreen.YouTubeViewer.createRoute("dQw4w9WgXcQ"))
    }
}
