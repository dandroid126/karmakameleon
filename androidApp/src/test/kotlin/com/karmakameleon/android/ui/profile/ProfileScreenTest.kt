package com.karmakameleon.android.ui.profile

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.assertTopIsInList
import com.karmakameleon.android.testing.lastPage
import com.karmakameleon.android.testing.postListing
import com.karmakameleon.android.testing.reversedListing
import com.karmakameleon.android.ui.components.FakeImagesRule
import com.karmakameleon.android.ui.feed.FIRST_POST_TITLE
import com.karmakameleon.android.ui.feed.LAST_POST_TITLE
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.data.repository.MarkdownRepository
import com.karmakameleon.shared.testing.form
import com.karmakameleon.shared.testing.query
import com.karmakameleon.shared.testing.readFixture
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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

    // The client ID is hidden like a password, so it doesn't show up in screenshots, until it's revealed. Only the
    // shown text is checked: hasText also matches the input itself, which is the real ID.
    @Test
    fun clientIdIsHiddenUntilRevealed() {
        showProfile(username = null)
        val field = composeRule.onNode(hasSetTextAction() and hasText("Reddit Client ID"))
        val shownText = { field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text }
        field.performTextInput("client-id")
        assertEquals("•".repeat("client-id".length), shownText())
        composeRule.onNodeWithContentDescription("Show client ID").performClick()
        assertEquals("client-id", shownText())
        composeRule.onNodeWithContentDescription("Hide client ID").performClick()
        assertEquals("•".repeat("client-id".length), shownText())
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

    // Someone else's profile used to treat you as logged out, which turned off voting and replying (#87).
    @Test
    fun otherProfileCanBeVotedOn() {
        showOtherProfile()
        composeRule
            .onAllNodesWithContentDescription("Upvote")
            .onFirst()
            .assertIsEnabled()
            .performClick()
        composeRule.waitForIdle()
        assertEquals("1", requests("/api/vote").single().form()["dir"])
    }

    @Test
    fun choosingSortReloadsPosts() {
        showOtherProfile()
        composeRule.onNodeWithContentDescription("Sort").performClick()
        composeRule.onNodeWithText("New").performClick()
        composeRule.waitForIdle()
        assertEquals("new", requests("/user/someone/submitted").last().query()["sort"])
    }

    // A new sort used to land on the post that was on screen, partway down the new posts (#127).
    @Test
    fun newSortStartsAtTheTop() {
        fakeReddit.loggedIn = true
        loadOwnAccount(fakeReddit)
        respondWithProfile(fakeReddit, "someone")
        val posts = lastPage(postListing())
        fakeReddit.reddit.respond("/user/someone/submitted", posts)
        showProfile("someone")
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        composeRule.onNodeWithText(FIRST_POST_TITLE).assertIsNotDisplayed()

        fakeReddit.reddit.respond("/user/someone/submitted", reversedListing(posts))
        composeRule.onNodeWithContentDescription("Sort").performClick()
        composeRule.onNodeWithText("New").performClick()
        composeRule.onNodeWithText(LAST_POST_TITLE).assertIsDisplayed()
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

    // Deleting a long comment after scrolling down to its Delete button used to leave the next comments above the
    // screen (#179).
    @Test
    fun deletingTheCommentOnScreenShowsTheNextAtTheTop() {
        fakeReddit.loggedIn = true
        respondWithProfile(fakeReddit, OWN_USERNAME)
        fakeReddit.reddit.respond("/user/$OWN_USERNAME/comments", longCommentFirst())
        loadOwnAccount(fakeReddit)
        showProfile(OWN_USERNAME)
        composeRule.onNodeWithText("Comments").performClick()
        composeRule.onNodeWithText(LONG_COMMENT_START, substring = true).performClick()
        composeRule.onNodeWithContentDescription("Delete").performScrollTo().performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(LONG_COMMENT_START, substring = true).assertDoesNotExist()
        composeRule.assertTopIsInList(composeRule.onNodeWithText("Short comment 1"))
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

private const val LONG_COMMENT_START = "Long paragraph 1,"

// The comment in user_comments.json as a long one, taller than the screen, followed by short ones.
private fun longCommentFirst(): String {
    val listing = Json.parseToJsonElement(readFixture("user_comments.json")).jsonObject
    val data = listing.getValue("data").jsonObject
    val template =
        data
            .getValue("children")
            .jsonArray
            .first()
            .jsonObject

    fun comment(
        id: String,
        body: String,
    ): JsonObject {
        val fields = mapOf("id" to JsonPrimitive(id), "name" to JsonPrimitive("t1_$id"), "body" to JsonPrimitive(body))
        return JsonObject(template + ("data" to JsonObject(template.getValue("data").jsonObject + fields)))
    }
    val long = (1..40).joinToString("\n\n") { "Long paragraph $it, with enough words to wrap across the line." }
    val comments = listOf(comment("long", long)) + (1..8).map { comment("short$it", "Short comment $it") }
    return lastPage(JsonObject(listing + ("data" to JsonObject(data + ("children" to JsonArray(comments))))).toString())
}
