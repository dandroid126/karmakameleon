package com.karmakameleon.android.ui

import android.webkit.WebView
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import com.karmakameleon.android.data.PendingNotificationAction
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.postListing
import com.karmakameleon.android.ui.components.FakeImagesRule
import com.karmakameleon.android.ui.feed.FIRST_POST_TITLE
import com.karmakameleon.android.ui.profile.OWN_USERNAME
import com.karmakameleon.android.ui.profile.respondWithProfile
import com.karmakameleon.android.ui.profile.userAbout
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.data.api.AuthManager
import io.ktor.http.Url
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

// Characters a URL in a route has to keep: a space, a "+" and an escape. Encoding a route's URL the wrong way or
// decoding it twice changes them.
private const val ENCODED_CHARACTERS = "a b+c%20d"

@RunWith(RobolectricTestRunner::class)
class KarmaKameleonAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    @get:Rule
    val fakeReddit = FakeRedditRule()

    private lateinit var navController: NavHostController

    private fun showApp(loggedIn: Boolean = true) {
        fakeReddit.loggedIn = loggedIn
        fakeReddit.reddit.respond("/hot", postListing())
        // The account the app loads at startup. Reddit only answers when logged in. /api/v1/me returns the user itself,
        // not wrapped in a thing.
        if (loggedIn) fakeReddit.reddit.respond("/api/v1/me", userAbout(OWN_USERNAME).getValue("data").toString())
        // Every post opens as the one in post_with_comments.json, from r/nba by user18.
        fakeReddit.reddit.respond("/r/pics/comments/1x65iks", postListing("post_with_comments.json"))
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                navController = rememberNavController()
                KarmaKameleonApp(navController = navController)
            }
        }
    }

    private fun route() = navController.currentDestination?.route

    private fun argument(name: String) = navController.currentBackStackEntry?.arguments?.getString(name)

    // Opens a link the way tapping one in a post or comment does.
    private fun openLink(url: String) {
        composeRule.runOnUiThread { GlobalContext.get().get<NavigationHandler>().handleLink(url) }
        composeRule.waitForIdle()
    }

    // From the Search tab: the feed's posts have menus of their own.
    private fun openMenuItem(title: String) {
        composeRule.onNode(hasText("Search") and hasClickAction()).performClick()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText(title).performClick()
    }

    @Test
    fun startsOnTheFeed() {
        showApp()
        assertEquals("feed", route())
        composeRule.onNodeWithText(FIRST_POST_TITLE).assertExists()
    }

    @Test
    fun tabsSwitchAndBackReturnsToTheFeed() {
        showApp()
        composeRule.onNodeWithText("Search").performClick()
        assertEquals("search", route())
        composeRule.onNodeWithText("Subreddits").performClick()
        assertEquals("subreddits", route())
        pressBack()
        assertEquals("feed", route())
    }

    @Test
    fun postOpensWithoutTheTabs() {
        showApp()
        composeRule.onNodeWithText(FIRST_POST_TITLE).performClick()
        assertEquals(DetailScreen.PostDetail.route, route())
        assertEquals("pics", argument("subreddit"))
        assertEquals("1x65iks", argument("postId"))
        composeRule.onNodeWithText("Subreddits").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals("feed", route())
    }

    @Test
    fun postBylineOpensTheAuthorAndSubreddit() {
        respondWithProfile(fakeReddit, "user18")
        showApp()
        composeRule.onNodeWithText(FIRST_POST_TITLE).performClick()
        composeRule.onNodeWithText("u/user18").performClick()
        assertEquals(DetailScreen.UserProfile.route, route())
        assertEquals("user18", argument("username"))

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("r/nba").performClick()
        assertEquals(DetailScreen.SubredditDetail.route, route())
        assertEquals("nba", argument("subredditName"))
    }

    @Test
    fun menuOpensSettingsAndInbox() {
        showApp()
        openMenuItem("Settings")
        assertEquals(DetailScreen.Settings.route, route())
        composeRule.onNodeWithContentDescription("Back").performClick()

        openMenuItem("Inbox")
        assertEquals(DetailScreen.Inbox.route, route())
        assertEquals(null, argument("filter"))
    }

    @Test
    fun menuOpensYourProfile() {
        respondWithProfile(fakeReddit, OWN_USERNAME)
        showApp()
        openMenuItem("$OWN_USERNAME's Profile")
        assertEquals(DetailScreen.UserProfile.route, route())
        assertEquals(OWN_USERNAME, argument("username"))
        composeRule.onNodeWithContentDescription("Logout").assertExists()
    }

    @Test
    fun loggedOutMenuOpensSignIn() {
        showApp(loggedIn = false)
        openMenuItem("Log In")
        assertEquals(DetailScreen.OwnProfile.route, route())
        composeRule.onNodeWithText("Sign In").assertExists()
    }

    // Signs in from the menu: Reddit's page loads in a WebView, which can't run here, so this plays its redirect back
    // to the app with the state the page was opened with.
    private fun signInFromTheMenu() {
        openMenuItem("Log In")
        composeRule.onNode(hasSetTextAction() and hasText("Reddit Client ID")).performTextInput("client-id")
        composeRule.onNodeWithText("Sign In").performClick()
        composeRule.waitForIdle()
        lateinit var webView: WebView
        onView(isAssignableFrom(WebView::class.java)).check { view, _ -> webView = view as WebView }
        val state = Url(shadowOf(webView).lastLoadedUrl).parameters["state"]
        composeRule.runOnUiThread {
            shadowOf(webView).webViewClient.onPageStarted(webView, "${AuthManager.DEFAULT_REDIRECT_URI}?state=$state&code=code#_", null)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun signingInReturnsToTheFeedAndShowsYourProfile() {
        respondWithProfile(fakeReddit, OWN_USERNAME)
        showApp(loggedIn = false)
        fakeReddit.reddit.respond("/api/v1/me", userAbout(OWN_USERNAME).getValue("data").toString())
        signInFromTheMenu()
        assertEquals("feed", route())
        composeRule.onNodeWithText(FIRST_POST_TITLE).assertExists()

        openMenuItem("$OWN_USERNAME's Profile")
        composeRule.onNodeWithContentDescription("Logout").assertExists()
    }

    @Test
    fun loggingOutLeavesTheProfile() {
        respondWithProfile(fakeReddit, OWN_USERNAME)
        showApp()
        openMenuItem("$OWN_USERNAME's Profile")
        composeRule.onNodeWithContentDescription("Logout").performClick()
        composeRule.waitForIdle()
        assertEquals("feed", route())
    }

    @Test
    fun notificationOpensUnreadMessages() {
        showApp()
        composeRule.runOnUiThread { PendingNotificationAction.triggerOpenInboxUnread() }
        composeRule.waitForIdle()
        assertEquals(DetailScreen.Inbox.route, route())
        assertEquals("UNREAD", argument("filter"))
        assertFalse(PendingNotificationAction.openInboxUnread.value)
    }

    @Test
    fun commentLinkOpensTheThreadWithContext() {
        showApp()
        openLink("https://www.reddit.com/r/pics/comments/1x65iks/title/phx1a8k/?context=3")
        assertEquals(DetailScreen.PostDetail.route, route())
        assertEquals("phx1a8k", argument("commentId"))
        assertEquals(3, navController.currentBackStackEntry?.arguments?.getInt("context"))
    }

    @Test
    fun shareLinkOpensThePostItRedirectsTo() {
        showApp()
        fakeReddit.reddit.redirect("/r/pics/s/AbCdEf", "https://www.reddit.com/r/pics/comments/1x65iks/title/?share_id=x")
        openLink("https://www.reddit.com/r/pics/s/AbCdEf")
        assertEquals(DetailScreen.PostDetail.route, route())
        assertEquals("pics", argument("subreddit"))
        assertEquals("1x65iks", argument("postId"))
        assertEquals(null, argument("commentId"))
    }

    // Reddit serves a profile's share links under /u/<name>/, and this one is for a comment.
    @Test
    fun profileShareLinkOpensTheCommentItRedirectsTo() {
        showApp()
        fakeReddit.reddit.redirect(
            "/u/user18/s/AbCdEf",
            "https://www.reddit.com/user/user18/comments/1x65iks/comment/phx1a8k/?share_id=x",
        )
        openLink("https://www.reddit.com/u/user18/s/AbCdEf")
        assertEquals(DetailScreen.PostDetail.route, route())
        assertEquals("u_user18", argument("subreddit"))
        assertEquals("1x65iks", argument("postId"))
        assertEquals("phx1a8k", argument("commentId"))
    }

    @Test
    fun shareLinkThatDoesNotLeadToAPostStaysPut() {
        showApp()
        fakeReddit.reddit.redirect("/r/pics/s/AbCdEf", "https://www.reddit.com/r/pics/")
        openLink("https://www.reddit.com/r/pics/s/AbCdEf")
        assertEquals("feed", route())
    }

    @Test
    fun imageLinkOpensTheViewerWithTheSameUrl() {
        showApp()
        val url = "https://i.redd.it/$ENCODED_CHARACTERS.jpg?width=640&s=x"
        openLink(url)
        assertEquals(DetailScreen.ImageViewer.route, route())
        assertEquals(url, fakeImages.requested.last().toString())
    }

    @Test
    fun videoLinkOpensItsViewer() {
        showApp()
        val url = "https://example.com/$ENCODED_CHARACTERS.mp4"
        openLink(url)
        assertEquals(DetailScreen.VideoViewer.route, route())
        // The player draws on a TextureView and keeps its URL to itself, so this checks the route's argument, which
        // would have its space turned into "+" if the route encoded it wrongly.
        assertEquals(url, argument("url"))
    }

    @Test
    fun youTubeLinkOpensItsViewer() {
        showApp()
        openLink("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals(DetailScreen.YouTubeViewer.route, route())
        assertEquals("dQw4w9WgXcQ", argument("videoId"))
    }

    @Test
    fun otherLinksOpenInTheBrowser() {
        showApp()
        val url = "https://example.com/$ENCODED_CHARACTERS?x=1&y=2"
        openLink(url)
        assertEquals(DetailScreen.WebBrowser.route, route())
        // The browser shows its URL as its title.
        composeRule.onNodeWithText(url).assertExists()
    }
}
