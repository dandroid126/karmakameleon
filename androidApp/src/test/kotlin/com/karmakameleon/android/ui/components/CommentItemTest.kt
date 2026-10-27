package com.karmakameleon.android.ui.components

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.karmakameleon.android.testing.FakeRedditApi
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.data.repository.CommentRepository
import com.karmakameleon.shared.data.repository.MarkdownRepository
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.testing.FakeAuthManager
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class CommentItemTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val navigation = NavigationRule()

    private val recorded = RecordedCommentActions()
    private val redditApi = FakeRedditApi(FakeAuthManager())

    private fun showComment(
        comment: Comment = testComment(),
        isSelected: Boolean = true,
        isLoggedIn: Boolean = true,
        isSingleThreadMode: Boolean = false,
        showSubreddit: Boolean = false,
    ) {
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                CommentItem(
                    comment = comment,
                    isSelected = isSelected,
                    isHidden = false,
                    actions = recorded.actions,
                    isLoggedIn = isLoggedIn,
                    loggedInUsername = "me",
                    isSingleThreadMode = isSingleThreadMode,
                    showSubreddit = showSubreddit,
                    commentRepository = CommentRepository(redditApi, MarkdownRepository(Dispatchers.Main)),
                )
            }
        }
    }

    private fun assertCalls(vararg calls: String) = assertEquals(calls.toList(), recorded.calls)

    @Test
    fun tappingCommentSelectsIt() {
        showComment(isSelected = false)
        composeRule.onNodeWithText("Done").assertDoesNotExist()
        composeRule.onNodeWithText("42").performClick()
        assertCalls("select")
    }

    @Test
    fun tappingAuthorOpensProfileWithoutSelecting() {
        showComment(isSelected = false)
        composeRule.onNodeWithText("commenter").performClick()
        assertEquals(listOf("user commenter"), navigation.calls)
        assertCalls()
    }

    @Test
    fun topLevelControlsMoveBetweenTopLevelComments() {
        showComment()
        listOf("Done", "Hide", "Prev", "Next").forEach { composeRule.onNodeWithText(it).performClick() }
        assertCalls("done", "hide", "prev", "next")
    }

    @Test
    fun replyControlsMoveUpTheThread() {
        showComment(testComment().copy(depth = 1, parentId = "t1_parent"))
        composeRule.onNodeWithText("Root").performClick()
        composeRule.onNodeWithText("Parent").performClick()
        assertCalls("root", "parent")
    }

    @Test
    fun singleThreadTopCommentHasNoParentControls() {
        // In a single thread the top comment can be deeper than 0, but has no parent comment to go to.
        showComment(testComment().copy(depth = 3, parentId = "t3_abc123"), isSingleThreadMode = true)
        composeRule.onNodeWithText("Prev").assertExists()
        composeRule.onNodeWithText("Parent").assertDoesNotExist()
    }

    @Test
    fun votingUpdatesComment() {
        showComment()
        composeRule.onNodeWithContentDescription("Upvote").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("t1_c1 1"), redditApi.votes)
        val updated = recorded.updatedComments.single()
        assertEquals(true, updated.likes)
        assertEquals(43, updated.score)
    }

    @Test
    fun votingAgainRemovesVote() {
        showComment(testComment().copy(likes = false))
        composeRule.onNodeWithContentDescription("Downvote").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("t1_c1 0"), redditApi.votes)
        val updated = recorded.updatedComments.single()
        assertEquals(null, updated.likes)
        // The downvote had taken one point off, so removing it puts the point back.
        assertEquals(43, updated.score)
    }

    @Test
    fun loggedOutCanNotVoteReplyOrSave() {
        showComment(isLoggedIn = false)
        composeRule.onNodeWithContentDescription("Upvote").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Reply").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun ownCommentCanBeEditedAndDeleted() {
        showComment(testComment().copy(author = "me"))
        composeRule.onNodeWithContentDescription("Edit").performClick()
        composeRule.onNodeWithContentDescription("Delete").performClick()
        assertCalls("edit", "delete")
    }

    @Test
    fun othersCommentCanNotBeEdited() {
        showComment()
        composeRule.onNodeWithContentDescription("Edit").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Delete").assertDoesNotExist()
    }

    @Test
    fun replyAndMenuActions() {
        showComment()
        composeRule.onNodeWithContentDescription("Reply").performClick()
        listOf("Share", "Save", "Go to comment").forEach {
            composeRule.onNodeWithContentDescription("More").performClick()
            composeRule.onNodeWithText(it).performClick()
        }
        assertCalls("reply", "share", "save", "go to c1")
    }

    @Test
    fun savedCommentOffersUnsave() {
        showComment(testComment().copy(isSaved = true))
        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Unsave").assertExists()
    }

    @Test
    fun subredditOpensSubreddit() {
        showComment(showSubreddit = true)
        composeRule.onNodeWithText("r/Kotlin").performClick()
        assertEquals(listOf("subreddit Kotlin"), navigation.calls)
    }

    @Test
    fun hiddenScoreExplainsWhy() {
        showComment(testComment().copy(scoreHidden = true))
        composeRule.onNodeWithText("[~]").performClick()
        assertEquals("Score hidden by subreddit rules", ShadowToast.getTextOfLatestToast())
    }
}
