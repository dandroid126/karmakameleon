package com.karmakameleon.android.ui.search

import android.os.Looper
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.postListing
import com.karmakameleon.android.ui.components.FakeImagesRule
import com.karmakameleon.android.ui.feed.FIRST_POST_TITLE
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.testing.query
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class SearchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    @get:Rule
    val fakeReddit = FakeRedditRule()

    private val calls = mutableListOf<String>()

    private fun showSearch() {
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                SearchScreen(
                    onPostClick = { subreddit, id -> calls += "post $subreddit $id" },
                    onSubredditClick = { calls += "subreddit $it" },
                    onUserClick = { calls += "user $it" },
                    onCommentClick = { subreddit, post, comment -> calls += "comment $subreddit $post $comment" },
                )
            }
        }
    }

    // Types into the search field and waits out the pause the screen leaves for more typing before it searches.
    private fun type(text: String) {
        composeRule.onNode(hasSetTextAction()).performTextInput(text)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
    }

    private fun searches() = fakeReddit.reddit.requests.filter { it.url.encodedPath == "/search" }

    @Test
    fun promptsBeforeAnythingIsTyped() {
        showSearch()
        composeRule.onNodeWithText("Search for posts").assertExists()
    }

    @Test
    fun typingSearchesPosts() {
        fakeReddit.reddit.respond("/search", postListing())
        showSearch()
        type("halloween")

        composeRule.onNodeWithText(FIRST_POST_TITLE).performClick()
        assertEquals(listOf("post pics 1x65iks"), calls)
        assertEquals("halloween", searches().single().query()["q"])
        assertEquals("relevance", searches().single().query()["sort"])
    }

    @Test
    fun noResults() {
        showSearch()
        type("zzzz")
        composeRule.onNodeWithText("No results found").assertExists()
        assertEquals("zzzz", searches().single().query()["q"])
    }

    // A failed search used to look like one with no results (#87).
    @Test
    fun errorOffersRetry() {
        fakeReddit.reddit.respond("/search", "", HttpStatusCode.ServiceUnavailable)
        showSearch()
        type("halloween")
        composeRule.onNodeWithText("Reddit is unavailable right now. Try again later.").assertExists()
        composeRule.onNodeWithText("No results found").assertDoesNotExist()

        fakeReddit.reddit.respond("/search", postListing())
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.onNodeWithText(FIRST_POST_TITLE).assertExists()
        assertEquals(2, searches().size)
    }

    @Test
    fun choosingSortSearchesAgain() {
        fakeReddit.reddit.respond("/search", postListing())
        showSearch()
        type("halloween")

        composeRule.onNodeWithText("Relevance").performClick()
        composeRule.onNodeWithText("New").performClick()
        composeRule.waitForIdle()
        assertEquals("new", searches().last().query()["sort"])
    }

    // The chip used to do nothing (#87).
    @Test
    fun choosingTimeFilterSearchesAgain() {
        fakeReddit.reddit.respond("/search", postListing())
        showSearch()
        type("halloween")

        composeRule.onNodeWithText("All Time").performClick()
        composeRule.onNodeWithText("This Week").performClick()
        composeRule.waitForIdle()
        assertEquals("week", searches().last().query()["t"])
        composeRule.onNodeWithText("This Week").assertExists()
    }

    // During the pause before the search runs, the screen shows it's searching instead of "No results found" (#87).
    @Test
    fun noResultsMessageWaitsForTheSearch() {
        showSearch()
        composeRule.onNode(hasSetTextAction()).performTextInput("zzzz")
        composeRule.onNodeWithText("No results found").assertDoesNotExist()
        assertEquals(emptyList<Any>(), searches())

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        composeRule.onNodeWithText("No results found").assertExists()
    }

    // One letter isn't searched, so it isn't "No results found" either.
    @Test
    fun oneLetterShowsThePrompt() {
        showSearch()
        type("z")
        composeRule.onNodeWithText("Search for posts").assertExists()
        composeRule.onNodeWithText("No results found").assertDoesNotExist()
    }

    @Test
    fun clearingGoesBackToThePrompt() {
        showSearch()
        type("zzzz")
        composeRule.onNodeWithContentDescription("Clear").performClick()
        composeRule.onNodeWithText("Search for posts").assertExists()
    }

    @Test
    fun redditLinkOpensInsteadOfSearching() {
        showSearch()
        type("https://www.reddit.com/r/Kotlin/")
        composeRule.onNodeWithText("Open r/Kotlin").performClick()
        assertEquals(listOf("subreddit Kotlin"), calls)
        assertEquals(emptyList<Any>(), searches())
    }

    @Test
    fun commentLinkOpensTheComment() {
        showSearch()
        type("https://www.reddit.com/r/Kotlin/comments/abc123/title/def456/")
        composeRule.onNodeWithText("Open comment in r/Kotlin").performClick()
        assertEquals(listOf("comment Kotlin abc123 def456"), calls)
    }
}
