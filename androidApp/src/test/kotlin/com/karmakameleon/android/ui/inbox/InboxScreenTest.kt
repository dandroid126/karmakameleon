package com.karmakameleon.android.ui.inbox

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withHint
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.lastPage
import com.karmakameleon.android.testing.postListing
import com.karmakameleon.android.ui.post.submittedComment
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.testing.form
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner

// The first message in message_listing.json, a reply to a comment.
const val FIRST_MESSAGE = "Coroutines & flows work well here."

@RunWith(RobolectricTestRunner::class)
class InboxScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeReddit = FakeRedditRule()

    private val calls = mutableListOf<String>()

    private fun showInbox(loggedIn: Boolean = true) {
        fakeReddit.loggedIn = loggedIn
        fakeReddit.reddit.respond("/message/inbox", messageListing())
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                InboxScreen(onBackClick = { calls += "back" })
            }
        }
    }

    private fun requests(path: String) = fakeReddit.reddit.requests.filter { it.url.encodedPath == path }

    private fun requestedPaths() = fakeReddit.reddit.requests.map { it.url.encodedPath }

    @Test
    fun loggedOutAsksToSignIn() {
        showInbox(loggedIn = false)
        composeRule.onNodeWithText("Sign in to view your inbox").assertExists()
        composeRule.onNodeWithContentDescription("Mark all read").assertDoesNotExist()
        assertEquals(emptyList<String>(), requestedPaths())
    }

    @Test
    fun showsTheMessages() {
        showInbox()
        composeRule.onNodeWithText(FIRST_MESSAGE).assertExists()
        composeRule.onNodeWithText("Question about your app", substring = true).assertExists()
    }

    @Test
    fun noMessages() {
        fakeReddit.loggedIn = true
        composeRule.setContent { KarmaKameleonTheme(dynamicColor = false) { InboxScreen() } }
        composeRule.onNodeWithText("No messages").assertExists()
    }

    @Test
    fun tappingMarksItRead() {
        showInbox()
        composeRule.onNodeWithText(FIRST_MESSAGE).performClick()
        composeRule.waitForIdle()
        assertEquals("t1_phx9z01", requests("/api/read_message").single().form()["id"])
    }

    @Test
    fun markingAllRead() {
        showInbox()
        composeRule.onNodeWithContentDescription("Mark all read").performClick()
        composeRule.waitForIdle()
        assertEquals(1, requests("/api/read_all_messages").size)
    }

    @Test
    fun choosingFilterLoadsIt() {
        showInbox()
        composeRule.onNodeWithContentDescription("Filter").performClick()
        composeRule.onNodeWithText("Unread").performClick()
        composeRule.waitForIdle()
        assertEquals("/message/unread", requestedPaths().last())
    }

    // Opening the inbox at a filter, as a notification does, is left out until it's fixed: the filter is ignored while
    // the first load runs, so it shows every message (#87).

    @Test
    fun replying() {
        fakeReddit.reddit.respond("/api/comment", submittedComment("phx2cm1"))
        showInbox()
        composeRule.onNodeWithText(FIRST_MESSAGE).performClick()
        composeRule.onNodeWithContentDescription("Reply").performClick()
        composeRule.waitForIdle()
        // The reply field is a platform EditText, which Compose's test APIs can't type into.
        onView(withHint("Write a reply...")).perform(replaceText("Thanks!"))
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitForIdle()

        val reply = requests("/api/comment").single().form()
        assertEquals("t1_phx9z01", reply["thing_id"])
        assertEquals("Thanks!", reply["text"])
        composeRule.onNodeWithContentDescription("Send").assertDoesNotExist()
    }

    @Test
    fun blockingAsksFirst() {
        showInbox()
        composeRule.onNodeWithText(FIRST_MESSAGE).performClick()
        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Block user").performClick()
        composeRule.onNodeWithText("Are you sure you want to block u/user1?", substring = true).assertExists()
        composeRule.onNodeWithText("Block").performClick()
        composeRule.waitForIdle()
        assertEquals("user1", requests("/api/block_user").single().form()["name"])
    }

    @Test
    fun fullCommentsOpensThePost() {
        GlobalContext.get().get<NavigationHandler>().onPostClick = { subreddit, id -> calls += "post $subreddit $id" }
        showInbox()
        composeRule.onNodeWithText(FIRST_MESSAGE).performClick()
        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Full comments").performClick()
        assertEquals(listOf("post Kotlin 1xa2b3c"), calls)
    }

    @Test
    fun backGoesBack() {
        showInbox()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), calls)
    }
}

/** message_listing.json, three hours old and without a next page. */
fun messageListing(): String = lastPage(postListing("message_listing.json"))
