package com.karmakameleon.android.ui.components

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.domain.model.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class CompactPostCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    private val recorded = RecordedPostActions()

    private fun showCompactPostCard(
        post: Post = testPost(),
        isExpanded: Boolean = true,
        isLoggedIn: Boolean = true,
    ) {
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                CompactPostCard(
                    post = post,
                    isExpanded = isExpanded,
                    isHighlighted = isExpanded,
                    onToggleExpansion = { recorded.calls += "toggle" },
                    actions = recorded.actions,
                    isLoggedIn = isLoggedIn,
                )
            }
        }
    }

    private fun assertCalls(vararg calls: String) = assertEquals(calls.toList(), recorded.calls)

    @Test
    fun tappingCardTogglesControlsAndThumbnailOpensPost() {
        showCompactPostCard(isExpanded = false)
        composeRule.onNodeWithText(testPost().title).performClick()
        composeRule.onNodeWithContentDescription("Open post").performClick()
        assertCalls("toggle", "open")
    }

    @Test
    fun collapsedCardHidesControlsAndAuthor() {
        showCompactPostCard(isExpanded = false)
        composeRule.onNodeWithContentDescription("Share").assertDoesNotExist()
        composeRule.onNodeWithText("by u/kotlinfan").assertDoesNotExist()
    }

    @Test
    fun loggedInActions() {
        val post = testLinkPost().copy(isCrosspost = true)
        showCompactPostCard(post)
        composeRule.onNodeWithContentDescription("Upvote").performClick()
        composeRule.onNodeWithContentDescription("Downvote").performClick()
        composeRule.onNodeWithText("r/Kotlin").performClick()
        composeRule.onNodeWithContentDescription("Save").performClick()
        composeRule.onNodeWithContentDescription("Hide").performClick()
        composeRule.onNodeWithContentDescription("Open link").performClick()
        composeRule.onNodeWithContentDescription("View original post").performClick()
        assertCalls("upvote", "downvote", "subreddit", "save", "hide", "link ${post.url}", "crosspost")
    }

    @Test
    fun loggedOutActionsAskToLogIn() {
        showCompactPostCard(isLoggedIn = false)
        listOf("Upvote", "Downvote", "Save", "Hide").forEach { action ->
            ShadowToast.reset()
            composeRule.onNodeWithContentDescription(action).performClick()
            assertEquals(action, "You need to be logged in to do that", ShadowToast.getTextOfLatestToast())
        }
        assertCalls()
    }

    @Test
    fun lockedPostExplainsWhyItCanNotBeVotedOn() {
        showCompactPostCard(testPost().copy(isLocked = true))
        composeRule.onNodeWithContentDescription("Upvote").performClick()
        assertEquals("This post is locked and cannot be voted on", ShadowToast.getTextOfLatestToast())
        assertCalls()
    }

    @Test
    fun shareMenuCopiesCommentsLink() {
        showCompactPostCard()
        composeRule.onNodeWithContentDescription("Share").performClick()
        composeRule.onNodeWithText("Copy comments link").performClick()
        composeRule.waitForIdle()
        assertEquals("https://www.reddit.com${testPost().permalink}", copiedText())
    }

    @Test
    fun menuOpensAuthorAndBlocksSubreddit() {
        showCompactPostCard()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("u/kotlinfan's profile").performClick()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Block r/Kotlin").performClick()
        assertCalls("user", "block")
    }

    @Test
    fun selectionHighlightsAndExpandsTappedPost() {
        val selection = CompactPostSelection()

        selection.toggle("a")
        assertTrue(selection.isHighlighted("a"))
        assertTrue(selection.isExpanded("a"))

        // Tapping it again collapses it but keeps the highlight.
        selection.toggle("a")
        assertTrue(selection.isHighlighted("a"))
        assertFalse(selection.isExpanded("a"))

        // Tapping another post moves both to it.
        selection.toggle("b")
        assertFalse(selection.isHighlighted("a"))
        assertTrue(selection.isExpanded("b"))
    }

    @Test
    fun selectionSurvivesRecreation() {
        val restorationTester = StateRestorationTester(composeRule)
        lateinit var selection: CompactPostSelection
        restorationTester.setContent { selection = rememberCompactPostSelection() }

        selection.toggle("a")
        restorationTester.emulateSavedInstanceStateRestore()

        assertTrue(selection.isExpanded("a"))
    }
}
