package com.karmakameleon.android.ui.post

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withHint
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.assertTopIsInList
import com.karmakameleon.android.testing.postListing
import com.karmakameleon.android.ui.components.FakeImagesRule
import com.karmakameleon.android.ui.profile.OWN_USERNAME
import com.karmakameleon.android.ui.profile.loadOwnAccount
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.testing.form
import com.karmakameleon.shared.testing.query
import com.karmakameleon.shared.testing.readFixture
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

// The post and comments in post_with_comments.json.
const val POST_TITLE = "First day of the NBA season... Hit it, Mr. Tesh!"
const val FIRST_COMMENT = "B-B-B-B-B-Basketball!"
const val POST_PATH = "/r/nba/comments/1xawzaz"

@RunWith(RobolectricTestRunner::class)
class PostDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    @get:Rule
    val fakeReddit = FakeRedditRule()

    private val calls = mutableListOf<String>()

    private fun showPost(
        loggedIn: Boolean = true,
        commentId: String? = null,
        selfText: String = "",
        nsfwAndSpoiler: Boolean = false,
        thread: (String) -> String = { it },
    ) {
        fakeReddit.loggedIn = loggedIn
        fakeReddit.reddit.respond(
            POST_PATH,
            thread(
                postListing("post_with_comments.json")
                    .replace("\"selftext\": \"\"", "\"selftext\": \"$selfText\"")
                    .replace("\"over_18\": false", "\"over_18\": $nsfwAndSpoiler")
                    .replace("\"spoiler\": false", "\"spoiler\": $nsfwAndSpoiler"),
            ),
        )
        fakeReddit.reddit.respond("$POST_PATH/comment/phx23wk", postListing("post_with_comments.json"))
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                PostDetailScreen(
                    subreddit = "nba",
                    postId = "1xawzaz",
                    commentId = commentId,
                    onBackClick = { calls += "back" },
                )
            }
        }
    }

    private fun requests(path: String) = fakeReddit.reddit.requests.filter { it.url.encodedPath == path }

    // The reply field is a platform EditText, which Compose's test APIs can't type into.
    private fun typeReply(text: String) {
        composeRule.waitForIdle()
        onView(withHint("Write a reply...")).perform(replaceText(text))
        composeRule.waitForIdle()
    }

    @Test
    fun showsThePostAndItsComments() {
        showPost()
        composeRule.onNodeWithText(POST_TITLE).assertExists()
        composeRule.onNodeWithContentDescription("68 comments").assertExists()
        composeRule.onNodeWithText(FIRST_COMMENT, substring = true).assertExists()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Load 1 more reply"))
    }

    @Test
    fun nsfwSpoilerPostShowsBothWarnings() {
        showPost(nsfwAndSpoiler = true)
        composeRule.onNodeWithText(POST_TITLE).assertExists()
        composeRule.onNodeWithText("NSFW").assertExists()
        composeRule.onNodeWithText("Spoiler").assertExists()
    }

    @Test
    fun votingAndSavingThePost() {
        showPost()
        composeRule.onNodeWithContentDescription("Upvote").performClick()
        composeRule.onNodeWithContentDescription("Save").performClick()
        composeRule.waitForIdle()

        val vote = requests("/api/vote").single().form()
        assertEquals("t3_1xawzaz", vote["id"])
        assertEquals("1", vote["dir"])
        assertEquals("t3_1xawzaz", requests("/api/save").single().form()["id"])
    }

    @Test
    fun collapsingHidesTheBody() {
        showPost(selfText = "The post's text")
        composeRule.onNodeWithText("The post's text").assertExists()
        composeRule.onNodeWithContentDescription("Collapse post body").performClick()
        composeRule.onNodeWithText("The post's text").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Expand post body").performClick()
        composeRule.onNodeWithText("The post's text").assertExists()
    }

    @Test
    fun bylineOpensTheSubredditAndAuthor() {
        val navigation = GlobalContext.get().get<NavigationHandler>()
        navigation.onSubredditClick = { calls += "subreddit $it" }
        navigation.onUserClick = { calls += "user $it" }
        showPost()
        composeRule.onNodeWithText("r/nba").performClick()
        composeRule.onNodeWithText("u/user18").performClick()
        assertEquals(listOf("subreddit nba", "user user18"), calls)
    }

    // On a 360 dp screen, the tests' size, the row of post actions ran past the edge, which cut off all but a sliver
    // of the Reply button (#87). Text only takes its real width with native graphics, as in the screenshot tests.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun replyButtonFitsOnANarrowScreen() {
        showPost()
        val reply = composeRule.onNodeWithContentDescription("Reply").getBoundsInRoot()
        assertTrue("Reply is ${reply.width} wide", reply.width >= 40.dp)
    }

    @Test
    fun replyingToThePost() {
        fakeReddit.reddit.respond("/api/comment", submittedComment("phx2cm1"))
        showPost()
        composeRule.onNodeWithContentDescription("Reply").performClick()
        typeReply("Nice post")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitForIdle()

        val reply = requests("/api/comment").single().form()
        assertEquals("t3_1xawzaz", reply["thing_id"])
        assertEquals("Nice post", reply["text"])
        composeRule.onNodeWithContentDescription("Send").assertDoesNotExist()
    }

    @Test
    fun quoteInsertsWhatIsBeingRepliedTo() {
        showPost()
        composeRule.onNodeWithContentDescription("Reply").performClick()
        composeRule.onNodeWithText("Quote").performClick()
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitForIdle()
        // The post has no text, so the quote is its title. The reply is sent trimmed.
        assertEquals(">$POST_TITLE", requests("/api/comment").single().form()["text"])
    }

    @Test
    fun closingAnUnsavedReplyAsksFirst() {
        showPost()
        composeRule.onNodeWithContentDescription("Reply").performClick()
        typeReply("Half written")
        composeRule.onNodeWithContentDescription("Cancel").performClick()
        composeRule.onNodeWithText("Discard Changes").assertExists()
        composeRule.onNodeWithText("Discard").performClick()
        composeRule.onNodeWithContentDescription("Send").assertDoesNotExist()
    }

    @Test
    fun aSavedDraftComesBack() {
        showPost()
        composeRule.onNodeWithContentDescription("Reply").performClick()
        typeReply("For later")
        composeRule.onNodeWithText("Save Draft").performClick()
        // Saved, so closing doesn't ask.
        composeRule.onNodeWithContentDescription("Cancel").performClick()
        composeRule.onNodeWithText("Discard Changes").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Reply").performClick()
        composeRule.onNodeWithText("Load Draft").performClick()
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitForIdle()
        assertEquals("For later", requests("/api/comment").single().form()["text"])
    }

    @Test
    fun choosingSortReloadsComments() {
        showPost()
        composeRule.onNodeWithContentDescription("Sort comments").performClick()
        composeRule.onNodeWithText("New").performClick()
        composeRule.waitForIdle()
        assertEquals("new", requests(POST_PATH).last().query()["sort"])
    }

    @Test
    fun loadingMoreReplies() {
        showPost()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Load 1 more reply"))
        composeRule.onNodeWithText("Load 1 more reply").performClick()
        composeRule.waitForIdle()
        requests("$POST_PATH/comment/phxmjnl").single()
    }

    @Test
    fun singleThreadCanShowAllComments() {
        showPost(commentId = "phx23wk")
        composeRule.onNodeWithText("View all comments").performClick()
        composeRule.waitForIdle()
        assertEquals(
            listOf("$POST_PATH/comment/phx23wk", POST_PATH),
            fakeReddit.reddit.requests
                .map {
                    it.url.encodedPath
                }.filter { it.startsWith(POST_PATH) },
        )
        composeRule.onNodeWithText("View all comments").assertDoesNotExist()
    }

    @Test
    fun failedLoadCanBeRetried() {
        fakeReddit.reddit.respond(POST_PATH, "", HttpStatusCode.InternalServerError)
        fakeReddit.loggedIn = true
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                PostDetailScreen(subreddit = "nba", postId = "1xawzaz", onBackClick = {})
            }
        }
        fakeReddit.reddit.respond(POST_PATH, postListing("post_with_comments.json"))
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.onNodeWithText(POST_TITLE).assertExists()
    }

    @Test
    fun loggedOutCanNotVoteSaveOrReply() {
        showPost(loggedIn = false)
        composeRule.onNodeWithContentDescription("Upvote").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Save").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Reply").assertDoesNotExist()
    }

    // Deleting a long comment after scrolling down to its Delete button used to leave the next comments above the
    // screen (#179).
    @Test
    fun deletingTheCommentOnScreenShowsTheNextAtTheTop() {
        // Before the account loads, which logs in.
        fakeReddit.loggedIn = true
        loadOwnAccount(fakeReddit)
        showPost(thread = ::ownLongFirstComment)
        composeRule.onNodeWithText(LONG_COMMENT_START, substring = true).performClick()
        composeRule.onNodeWithContentDescription("Delete").performScrollTo().performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()

        assertEquals("t1_phx1a8k", requests("/api/del").single().form()["id"])
        composeRule.assertTopIsInList(composeRule.onNodeWithText(SECOND_COMMENT, substring = true))
    }

    @Test
    fun backGoesBack() {
        showPost()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), calls)
    }
}

/** What Reddit answers when the comment [id] from user_comments.json is posted. */
fun submittedComment(id: String): String {
    val comment =
        Json
            .parseToJsonElement(readFixture("user_comments.json"))
            .jsonObject["data"]!!
            .jsonObject["children"]!!
            .jsonArray
            .single {
                it.jsonObject["data"]!!
                    .jsonObject["id"]!!
                    .jsonPrimitive.content == id
            }
    return """{"json": {"errors": [], "data": {"things": [$comment]}}}"""
}

// The start of the comment after the first one and its replies in post_with_comments.json.
private const val SECOND_COMMENT = "I will always hear Tim Robinson"

private const val LONG_COMMENT_START = "Long paragraph 1,"

// [thread] with its first comment written by the logged in user and taller than the screen, and enough short comments
// after the next one that the list can stay scrolled down once the first is deleted.
private fun ownLongFirstComment(thread: String): String {
    val listings = Json.parseToJsonElement(thread).jsonArray
    val comments = listings[1].jsonObject
    val children =
        comments
            .getValue("data")
            .jsonObject
            .getValue("children")
            .jsonArray
    val first = children.first().jsonObject
    val long = (1..40).joinToString("\n\n") { "Long paragraph $it, with enough words to wrap across the line." }
    val fields = mapOf("author" to JsonPrimitive(OWN_USERNAME), "body" to JsonPrimitive(long))
    val ownFirst = JsonObject(first + ("data" to JsonObject(first.getValue("data").jsonObject + fields)))
    val second = children[1].jsonObject
    val shortOnes =
        (1..8).map { n ->
            val fields =
                mapOf(
                    "id" to JsonPrimitive("short$n"),
                    "name" to JsonPrimitive("t1_short$n"),
                    "body" to JsonPrimitive("Short comment $n"),
                )
            JsonObject(second + ("data" to JsonObject(second.getValue("data").jsonObject + fields)))
        }
    val newChildren = JsonArray(listOf(ownFirst, second) + shortOnes + children.drop(2))
    val newComments = JsonObject(comments + ("data" to JsonObject(comments.getValue("data").jsonObject + ("children" to newChildren))))
    return JsonArray(listOf(listings[0], newComments)).toString()
}
