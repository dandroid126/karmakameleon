package com.karmakameleon.android.ui.components

import android.content.ClipboardManager
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.domain.model.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowToast

/** Records which of a post's actions ran, in order. */
class RecordedPostActions {
    val calls = mutableListOf<String>()

    val actions =
        PostActions(
            onClick = { calls += "open" },
            onSubredditClick = { calls += "subreddit" },
            onUserClick = { calls += "user" },
            onUpvote = { calls += "upvote" },
            onDownvote = { calls += "downvote" },
            onSave = { calls += "save" },
            onHide = { calls += "hide" },
            onBlockSubreddit = { calls += "block" },
            onLinkClick = { calls += "link $it" },
            onCrosspostClick = { calls += "crosspost" },
        )
}

fun copiedText(): String? =
    RuntimeEnvironment
        .getApplication()
        .getSystemService(ClipboardManager::class.java)
        .primaryClip
        ?.getItemAt(0)
        ?.text
        ?.toString()

@RunWith(RobolectricTestRunner::class)
class PostCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    private val recorded = RecordedPostActions()

    private fun showPostCard(
        post: Post = testPost(),
        isLoggedIn: Boolean = true,
    ) {
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                PostCard(post = post, actions = recorded.actions, isLoggedIn = isLoggedIn)
            }
        }
    }

    private fun assertCalls(vararg calls: String) = assertEquals(calls.toList(), recorded.calls)

    @Test
    fun tappingCardOpensPost() {
        showPostCard()
        composeRule.onNodeWithText(testPost().title).performClick()
        assertCalls("open")
    }

    @Test
    fun tappingSubredditAndAuthorOpensThem() {
        showPostCard()
        composeRule.onNodeWithText("r/Kotlin").performClick()
        composeRule.onNodeWithText("u/kotlinfan").performClick()
        assertCalls("subreddit", "user")
    }

    @Test
    fun votingAndSaving() {
        showPostCard()
        composeRule.onNodeWithContentDescription("Upvote").performClick()
        composeRule.onNodeWithContentDescription("Downvote").performClick()
        composeRule.onNodeWithContentDescription("Save").performClick()
        assertCalls("upvote", "downvote", "save")
    }

    @Test
    fun loggedOutCanNotVoteOrSave() {
        showPostCard(isLoggedIn = false)
        composeRule.onNodeWithContentDescription("Upvote").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Downvote").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Save").assertDoesNotExist()
    }

    @Test
    fun lockedPostExplainsWhyItCanNotBeVotedOrCommentedOn() {
        showPostCard(testPost().copy(isLocked = true))

        composeRule.onNodeWithContentDescription("Upvote").performClick()
        assertEquals("This post is locked and cannot be voted on", ShadowToast.getTextOfLatestToast())

        composeRule.onNodeWithText("56").performClick()
        assertEquals("This post is locked and cannot be commented on", ShadowToast.getTextOfLatestToast())
        assertCalls()
    }

    @Test
    fun linkPostOpensItsLinkAndCrosspostItsOriginal() {
        val post = testLinkPost().copy(isCrosspost = true)
        showPostCard(post)
        composeRule.onNodeWithContentDescription("Open link").performClick()
        composeRule.onNodeWithContentDescription("View original post").performClick()
        assertCalls("link ${post.url}", "crosspost")
    }

    @Test
    fun textPostHasNoLinkButtons() {
        showPostCard()
        composeRule.onNodeWithContentDescription("Open link").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("View original post").assertDoesNotExist()
    }

    @Test
    fun menuHidesPost() {
        showPostCard()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Hide").performClick()
        assertCalls("hide")
    }

    @Test
    fun menuOffersUnhideForHiddenPost() {
        showPostCard(testPost().copy(isHidden = true))
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Unhide").assertExists()
    }

    @Test
    fun menuCopiesLinks() {
        val post = testLinkPost()
        showPostCard(post)

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Copy comments link").performClick()
        composeRule.waitForIdle()
        assertEquals("https://www.reddit.com${post.permalink}", copiedText())

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Copy post link").performClick()
        composeRule.waitForIdle()
        assertEquals(post.url, copiedText())
    }

    @Test
    fun textPostHasNoPostLinkToCopy() {
        showPostCard()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Copy post link").assertDoesNotExist()
        assertNull(copiedText())
    }

    @Test
    fun menuBlocksSubreddit() {
        showPostCard()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Block r/Kotlin").performClick()
        assertCalls("block")
        assertEquals("Blocked r/Kotlin", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun tappingSpoilerOpensPost() {
        showPostCard(testImagePost().copy(isSpoiler = true))
        composeRule.onNodeWithText("Tap to view").performClick()
        assertCalls("open")
    }
}
