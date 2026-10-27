package com.karmakameleon.android.ui.profile

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.lastPage
import com.karmakameleon.android.testing.postListing
import com.karmakameleon.android.ui.components.FakeImagesRule
import com.karmakameleon.android.ui.feed.FIRST_POST_TITLE
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.data.repository.MarkdownRepository
import com.karmakameleon.shared.testing.form
import com.karmakameleon.shared.testing.query
import com.karmakameleon.shared.testing.readFixture
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner

// The comment in user_comments.json, which its author sees on their own profile.
const val OWN_COMMENT = "Thanks for sharing this"

@RunWith(RobolectricTestRunner::class)
class ProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    @get:Rule
    val fakeReddit = FakeRedditRule()

    private val calls = mutableListOf<String>()

    private fun showProfile(username: String?) {
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                ProfileScreen(
                    username = username,
                    onBackClick = { calls += "back" },
                    onPostClick = { subreddit, id -> calls += "post $subreddit $id" },
                    onSubredditClick = { calls += "subreddit $it" },
                    onCommentClick = { subreddit, post, comment -> calls += "comment $subreddit $post $comment" },
                )
            }
        }
    }

    private fun showOwnProfile() {
        fakeReddit.loggedIn = true
        respondWithProfile(fakeReddit, OWN_USERNAME)
        loadOwnAccount(fakeReddit)
        showProfile(OWN_USERNAME)
    }

    // Logged in, so the screen knows the profile isn't yours.
    private fun showOtherProfile() {
        fakeReddit.loggedIn = true
        loadOwnAccount(fakeReddit)
        respondWithProfile(fakeReddit, "someone")
        showProfile("someone")
    }

    private fun requests(path: String) = fakeReddit.reddit.requests.filter { it.url.encodedPath == path }

    @Test
    fun loggedOutAsksToSignIn() {
        showProfile(username = null)
        composeRule.onNodeWithText("Access your profile, saved posts, and more").assertExists()
        composeRule.onNodeWithText("Sign In").assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction() and hasText("Reddit Client ID")).performTextInput("client-id")
        composeRule.onNodeWithText("Sign In").assertIsEnabled()
    }

    @Test
    fun ownProfileHasEveryTab() {
        showOwnProfile()
        composeRule.onNodeWithText("u/$OWN_USERNAME").assertExists()
        composeRule.onNodeWithText("2.0K karma").assertExists()
        composeRule.onNodeWithText(FIRST_POST_TITLE).assertExists()
        listOf("Saved", "Upvoted", "Downvoted").forEach { composeRule.onNodeWithText(it).assertExists() }
        composeRule.onNodeWithContentDescription("Logout").assertExists()
    }

    @Test
    fun otherProfileHasPostsCommentsAndAbout() {
        showOtherProfile()
        composeRule.onNodeWithText("u/someone").assertExists()
        composeRule.onNodeWithText("Saved").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Logout").assertDoesNotExist()
        composeRule.onNodeWithText(FIRST_POST_TITLE).performClick()
        assertEquals(listOf("post pics 1x65iks"), calls)
    }

    // That you can vote and reply on someone else's profile is left out until it's fixed: the profile treats you as
    // logged out (#87).

    @Test
    fun choosingSortReloadsPosts() {
        showOtherProfile()
        composeRule.onNodeWithContentDescription("Sort").performClick()
        composeRule.onNodeWithText("New").performClick()
        composeRule.waitForIdle()
        assertEquals("new", requests("/user/someone/submitted").last().query()["sort"])
    }

    @Test
    fun commentOpensInItsThread() {
        showOtherProfile()
        composeRule.onNodeWithText("Comments").performClick()
        composeRule.onNodeWithText(OWN_COMMENT, substring = true).performClick()
        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Go to comment").performClick()
        assertEquals(listOf("comment Kotlin 1wovlgy phx2cm1"), calls)
    }

    @Test
    fun commentsShowTheirTextOnTheirFirstFrame() {
        // Parses only when the test runs it, which it does only until the comment is shown. Text the screen had to
        // parse itself, after showing the comment, would never appear.
        val parsing = StandardTestDispatcher()
        loadKoinModules(module { single { MarkdownRepository(parsing) } })
        showOtherProfile()
        composeRule.onNodeWithText("Comments").performClick()
        // The comment's author, which only its item shows.
        val comment = composeRule.onNodeWithText(OWN_USERNAME)
        repeat(PARSE_ROUNDS) {
            composeRule.waitForIdle()
            if (comment.isDisplayed()) return@repeat
            parsing.scheduler.advanceUntilIdle()
        }

        comment.assertExists()
        composeRule.onNodeWithText(OWN_COMMENT, substring = true).assertExists()
    }

    @Test
    fun aboutShowsKarmaAndOwnBadges() {
        showOwnProfile()
        // Your own profile has more tabs than fit, so About starts off screen.
        composeRule.onNodeWithText("About").performScrollTo().performClick()
        composeRule.onNodeWithText("Comment Karma").assertExists()
        composeRule.onNodeWithText("Reddit Premium").assertExists()
        composeRule.onNodeWithText("Moderator").assertExists()
    }

    @Test
    fun othersBadgesAreNotShown() {
        showOtherProfile()
        composeRule.onNodeWithText("About").performClick()
        composeRule.onNodeWithText("Comment Karma").assertExists()
        composeRule.onNodeWithText("Reddit Premium").assertDoesNotExist()
    }

    @Test
    fun savedCanSwitchToComments() {
        showOwnProfile()
        composeRule.onNodeWithText("Saved").performClick()
        composeRule.onNodeWithContentDescription("Change saved type").performClick()
        composeRule.onNode(hasText("Comments") and hasAnyAncestor(isPopup())).performClick()
        composeRule.waitForIdle()
        assertEquals("comments", requests("/user/$OWN_USERNAME/saved").last().query()["type"])
    }

    @Test
    fun deletingOwnCommentAsksFirst() {
        showOwnProfile()
        composeRule.onNodeWithText("Comments").performClick()
        composeRule.onNodeWithText(OWN_COMMENT, substring = true).performClick()
        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete Comment").assertExists()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()

        assertEquals("t1_phx2cm1", requests("/api/del").single().form()["id"])
        composeRule.onNodeWithText(OWN_COMMENT, substring = true).assertDoesNotExist()
    }

    @Test
    fun backGoesBack() {
        showOtherProfile()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), calls)
    }
}

/** Answers for [username]'s profile: the user, and a page each of their posts and comments. */
fun respondWithProfile(
    fakeReddit: FakeRedditRule,
    username: String,
) {
    fakeReddit.reddit.respond("/user/$username/about", userAbout(username).toString())
    fakeReddit.reddit.respond("/user/$username/submitted", postListing())
    fakeReddit.reddit.respond("/user/$username/comments", lastPage(postListing("user_comments.json")))
}

// More than the times the profile waits for parsing before it shows its comments.
private const val PARSE_ROUNDS = 5
