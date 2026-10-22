package com.karmakameleon.android.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso.pressBack
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.test.FakeImageLoaderEngine
import com.karmakameleon.android.R
import com.karmakameleon.android.data.PendingNotificationAction
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.postListing
import com.karmakameleon.android.ui.feed.FIRST_POST_TITLE
import com.karmakameleon.android.ui.profile.OWN_USERNAME
import com.karmakameleon.android.ui.profile.respondWithProfile
import com.karmakameleon.android.ui.profile.userAbout
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// Characters a URL in a route has to keep: a space, a "+" and an escape. Encoding a route's URL the wrong way or
// decoding it twice changes them.
private const val ENCODED_CHARACTERS = "a b+c%20d"

@RunWith(RobolectricTestRunner::class)
class KarmaKameleonAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeReddit = FakeRedditRule()

    private lateinit var navController: NavHostController

    // The images the app asked for, so a test can see which URL a viewer got.
    private val requestedImages = mutableListOf<Any>()

    @OptIn(DelicateCoilApi::class)
    @Before
    fun fakeImages() {
        val context = RuntimeEnvironment.getApplication()
        val image = checkNotNull(context.getDrawable(R.drawable.karma_kameleon)).asImage()
        val engine =
            FakeImageLoaderEngine
                .Builder()
                .intercept({ data ->
                    requestedImages += data
                    false
                }, image)
                .default(image)
                .build()
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(context).components { add(engine) }.build())
    }

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
    fun imageLinkOpensTheViewerWithTheSameUrl() {
        showApp()
        val url = "https://i.redd.it/$ENCODED_CHARACTERS.jpg?width=640&s=x"
        openLink(url)
        assertEquals(DetailScreen.ImageViewer.route, route())
        assertEquals(url, requestedImages.last().toString())
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
