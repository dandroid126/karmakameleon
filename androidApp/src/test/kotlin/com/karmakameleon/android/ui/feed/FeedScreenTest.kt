package com.karmakameleon.android.ui.feed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.assertTopIsInList
import com.karmakameleon.android.testing.lastPage
import com.karmakameleon.android.testing.postListing
import com.karmakameleon.android.testing.reversedListing
import com.karmakameleon.android.testing.scrollIntoItem
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

// The last of the saved listing's four posts.
const val LAST_POST_TITLE =
    "Our new kitten is the same color as our dog. " +
        "This has confused them both into thinking they are mother and son."

// The start of the second post's title in the saved listing.
const val SECOND_POST_TITLE = "Nice looking cougar headed up the driveway."

@RunWith(RobolectricTestRunner::class)
class FeedScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    @get:Rule
    val fakeReddit = FakeRedditRule().apply { loggedIn = true }

    private val calls = mutableListOf<String>()

    private fun showFeed(hot: String = postListing()) {
        fakeReddit.reddit.respond("/hot", hot)
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

    // A new sort used to open on the post that was on screen, partway down the new posts (#127).
    @Test
    fun newSortStartsAtTheTop() {
        val posts = lastPage(postListing())
        showFeed(hot = posts)
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        composeRule.onNodeWithText(FIRST_POST_TITLE).assertIsNotDisplayed()

        fakeReddit.reddit.respond("/new", reversedListing(posts))
        composeRule.onNodeWithContentDescription("Sort").performClick()
        composeRule.onNodeWithText("New").performClick()
        composeRule.onNodeWithText(LAST_POST_TITLE).assertIsDisplayed()
    }

    // Hiding the post on screen used to leave the next one as far above the screen as the list was scrolled into the
    // hidden one (#179).
    @Test
    fun hidingThePostOnScreenShowsTheNextAtTheTop() {
        showFeed()
        val firstMenu = composeRule.onAllNodesWithContentDescription("More options").onFirst()
        composeRule.scrollIntoItem(
            itemTop = composeRule.onNodeWithText(FIRST_POST_TITLE).getUnclippedBoundsInRoot().top,
            itemBottom = firstMenu.getUnclippedBoundsInRoot().bottom,
        )
        firstMenu.performClick()
        composeRule.onNodeWithText("Hide").performClick()
        composeRule.waitForIdle()

        assertEquals(
            "t3_1x65iks",
            fakeReddit.reddit.requests
                .single { it.url.encodedPath == "/api/hide" }
                .form()["id"],
        )
        composeRule.assertTopIsInList(composeRule.onNodeWithText(SECOND_POST_TITLE, substring = true))
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
        composeRule.onNodeWithText("Reddit is unavailable right now. Try again later.").assertExists()

        fakeReddit.reddit.respond("/hot", postListing())
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.onNodeWithText(FIRST_POST_TITLE).assertExists()
    }

    // Reddit sends no posts for some sorts, like Top / This Year on r/all (#38).
    @Test
    fun noPostsShowsMessage() {
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                FeedScreen(onPostClick = { _, _ -> }, onSubredditClick = {}, onUserClick = {})
            }
        }
        composeRule.onNodeWithText("No posts to show").assertExists()
        composeRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    // When the filters leave a page empty and Reddit has more, the next page loads straight away. The message waits
    // for it.
    @Test
    fun noPostsMessageWaitsForTheNextPage() {
        fakeReddit.settings.addBlockedSubreddit("pics")
        fakeReddit.settings.addBlockedSubreddit("aww")
        fakeReddit.reddit.respond("/hot", postListing().replace(Regex(""""after": *null"""), "\"after\": \"t3_next\""))
        fakeReddit.reddit.holdRequests("/hot")
        fakeReddit.reddit.letThrough("/hot")
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                FeedScreen(onPostClick = { _, _ -> }, onSubredditClick = {}, onUserClick = {})
            }
        }
        composeRule.waitForIdle()
        assertEquals(2, requestedPaths().count { it == "/hot" })
        composeRule.onNodeWithText("No posts to show").assertDoesNotExist()

        fakeReddit.reddit.respond("/hot", postListing())
        fakeReddit.reddit.letThrough("/hot")
        composeRule.onNodeWithText("No posts to show").assertExists()
    }

    @Test
    fun postsHideTheNoPostsMessage() {
        showFeed()
        composeRule.onNodeWithText(FIRST_POST_TITLE).assertExists()
        composeRule.onNodeWithText("No posts to show").assertDoesNotExist()
    }
}
