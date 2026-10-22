package com.karmakameleon.android.ui.feed

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.postListing
import com.karmakameleon.android.ui.components.FakeImagesRule
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.testing.form
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The first post in the saved listing.
const val FIRST_POST_TITLE = "Enter the Pics Halloween Photo Contest!"

@RunWith(RobolectricTestRunner::class)
class FeedScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    @get:Rule
    val fakeReddit = FakeRedditRule().apply { loggedIn = true }

    private val calls = mutableListOf<String>()

    private fun showFeed() {
        fakeReddit.reddit.respond("/hot", postListing())
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                FeedScreen(
                    onPostClick = { subreddit, id -> calls += "post $subreddit $id" },
                    onSubredditClick = { calls += "subreddit $it" },
                    onUserClick = { calls += "user $it" },
                )
            }
        }
    }

    private fun requestedPaths() = fakeReddit.reddit.requests.map { it.url.encodedPath }

    @Test
    fun showsTheFrontPage() {
        showFeed()
        composeRule.onNodeWithText(FIRST_POST_TITLE).assertExists()
        assertEquals("/hot", requestedPaths().first())
    }

    @Test
    fun tappingPostOpensIt() {
        showFeed()
        composeRule.onNodeWithText(FIRST_POST_TITLE).performClick()
        assertEquals(listOf("post pics 1x65iks"), calls)
    }

    @Test
    fun upvotingSendsTheVote() {
        showFeed()
        composeRule.onAllNodesWithContentDescription("Upvote").onFirst().performClick()
        composeRule.waitForIdle()
        val vote = fakeReddit.reddit.requests.single { it.url.encodedPath == "/api/vote" }
        assertEquals("t3_1x65iks", vote.form()["id"])
        assertEquals("1", vote.form()["dir"])
    }

    @Test
    fun choosingAnotherFeedLoadsIt() {
        showFeed()
        composeRule.onNodeWithContentDescription("Change feed").performClick()
        composeRule.onNodeWithText("r/all").performClick()
        composeRule.waitForIdle()
        assertEquals("/r/all/hot", requestedPaths().last())
        composeRule.onNodeWithText("r/all").assertExists()
    }

    @Test
    fun choosingSortLoadsThePostsSortedThatWay() {
        showFeed()
        composeRule.onNodeWithContentDescription("Sort").performClick()
        composeRule.onNodeWithText("New").performClick()
        composeRule.waitForIdle()
        assertEquals("/new", requestedPaths().last())
    }

    @Test
    fun errorOffersRetry() {
        fakeReddit.reddit.respond("/hot", "", HttpStatusCode.ServiceUnavailable)
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                FeedScreen(onPostClick = { _, _ -> }, onSubredditClick = {}, onUserClick = {})
            }
        }
        composeRule.onNodeWithText(FIRST_POST_TITLE).assertDoesNotExist()

        fakeReddit.reddit.respond("/hot", postListing())
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.onNodeWithText(FIRST_POST_TITLE).assertExists()
    }
}
