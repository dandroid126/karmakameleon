package com.karmakameleon.android.ui.components

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageItemTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val navigation = NavigationRule()

    private val recorded = RecordedMessageActions()

    private fun showMessage(
        message: Message,
        isSelected: Boolean = true,
        isLoggedIn: Boolean = true,
    ) {
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                MessageItem(message = message, isSelected = isSelected, isLoggedIn = isLoggedIn, actions = recorded.actions)
            }
        }
    }

    @Test
    fun tappingSelectsAndAuthorOpensProfile() {
        showMessage(testPrivateMessage(), isSelected = false)
        composeRule.onNodeWithText("Question about your app").performClick()
        composeRule.onNodeWithText("u/replier").performClick()
        assertEquals(listOf("select"), recorded.calls)
        assertEquals(listOf("user replier"), navigation.calls)
    }

    @Test
    fun loggedOutCanNotVoteOrReply() {
        showMessage(testCommentReply(), isLoggedIn = false)
        composeRule.onNodeWithContentDescription("Upvote").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Reply").assertDoesNotExist()
    }

    @Test
    fun selectedPrivateMessageHasNoActions() {
        showMessage(testPrivateMessage())
        composeRule.onNodeWithContentDescription("Context").assertDoesNotExist()
    }

    @Test
    fun votingAgainRemovesVote() {
        showMessage(testCommentReply().copy(likes = true))
        composeRule.onNodeWithContentDescription("Upvote").performClick()
        composeRule.onNodeWithContentDescription("Downvote").performClick()
        assertEquals(listOf("vote 0", "vote -1"), recorded.calls)
    }

    @Test
    fun actionsAndMenu() {
        showMessage(testCommentReply())
        composeRule.onNodeWithContentDescription("Context").performClick()
        composeRule.onNodeWithContentDescription("Reply").performClick()
        listOf("Full comments", "Report", "Block user", "Mark as unread").forEach {
            composeRule.onNodeWithContentDescription("More").performClick()
            composeRule.onNodeWithText(it).performClick()
        }
        assertEquals(listOf("context", "reply", "full comments", "report", "block", "mark unread"), recorded.calls)
    }
}
