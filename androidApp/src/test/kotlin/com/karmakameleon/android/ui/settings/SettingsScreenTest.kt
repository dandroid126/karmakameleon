package com.karmakameleon.android.ui.settings

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.karmakameleon.android.notifications.InboxNotificationWorker
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.ui.components.testPost
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.data.repository.ReadPostsRepository
import com.karmakameleon.shared.domain.model.NotificationInterval
import com.karmakameleon.shared.domain.model.NsfwHistoryMode
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeReddit = FakeRedditRule()

    private val settings get() = fakeReddit.settings
    private var backClicks = 0

    private fun showSettings() {
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                SettingsScreen(onBackClick = { backClicks++ })
            }
        }
    }

    // Settings further down aren't composed until they're scrolled to.
    private fun scrollTo(text: String): SemanticsNodeInteraction {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        return composeRule.onNodeWithText(text)
    }

    // Fails if any row of the list has this text, whether it's on screen or not: scrolling to a node searches the whole
    // list, and fails when nothing matches.
    private fun assertNotInList(text: String) {
        assertThrows(AssertionError::class.java) {
            composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        }
    }

    // A switch setting is one node, holding its title, description and state.
    private fun switchFor(title: String): SemanticsNodeInteraction = scrollTo(title).assert(isToggleable())

    @Test
    fun switchesChangeTheirSetting() {
        showSettings()
        // Compact cards start off and inline images on.
        switchFor("Compact Feed").performClick()
        switchFor("Inline Images").performClick()
        assertTrue(settings.compactFeedEnabled.value)
        assertFalse(settings.inlineImagesEnabled.value)
    }

    @Test
    fun nsfwOptionsOnlyShowWhenNsfwIsOn() {
        showSettings()
        assertNotInList("Cache NSFW Media")

        switchFor("Enable NSFW Content").performClick()
        scrollTo("Prefetch and blur").performClick()
        scrollTo("Don't save history on any NSFW post").performClick()
        assertTrue(settings.nsfwEnabled.value)
        assertEquals(NsfwPreviewMode.PREFETCH_AND_BLUR, settings.nsfwPreviewMode.value)
        assertEquals(NsfwHistoryMode.DONT_SAVE_ANY_NSFW, settings.nsfwHistoryMode.value)
    }

    @Test
    fun choosingNotificationIntervalSchedulesTheCheck() {
        val context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        fakeReddit.loggedIn = true
        showSettings()

        scrollTo("30 minutes").performClick()

        assertEquals(NotificationInterval.THIRTY_MINUTES, settings.notificationInterval.value)
        val work =
            runBlocking {
                WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(InboxNotificationWorker.WORK_NAME).first()
            }
        assertEquals(TimeUnit.MINUTES.toMillis(30), work.single().periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun clearingNsfwHistoryAsksFirst() {
        val readPosts = GlobalContext.get().get<ReadPostsRepository>()
        val nsfwPost = testPost().copy(id = "nsfw", isNsfw = true)
        readPosts.markAsRead(nsfwPost, NsfwHistoryMode.SAVE_ALL)
        readPosts.markAsRead(testPost(), NsfwHistoryMode.SAVE_ALL)
        showSettings()

        scrollTo("Clear NSFW Read History").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertTrue(readPosts.isRead("nsfw"))

        scrollTo("Clear NSFW Read History").performClick()
        composeRule.onNodeWithText("Clear").performClick()
        assertFalse(readPosts.isRead("nsfw"))
        assertTrue(readPosts.isRead(testPost().id))
    }

    @Test
    fun tappingBlockedSubredditUnblocksIt() {
        settings.addBlockedSubreddit("pics")
        showSettings()

        scrollTo("r/pics").performClick()
        assertEquals(emptySet<String>(), settings.blockedSubreddits.value)
        composeRule.onNodeWithText("No blocked subreddits").assertExists()
    }

    @Test
    fun backGoesBack() {
        showSettings()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backClicks)
    }
}
