package com.karmakameleon.android.ui.subreddit

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.postListing
import com.karmakameleon.android.ui.components.FakeImagesRule
import com.karmakameleon.android.ui.feed.FIRST_POST_TITLE
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.testing.form
import com.karmakameleon.shared.testing.readFixture
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubredditScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeImages = FakeImagesRule()

    @get:Rule
    val fakeReddit = FakeRedditRule()

    private val calls = mutableListOf<String>()

    private fun showSubreddit(loggedIn: Boolean = true) {
        fakeReddit.loggedIn = loggedIn
        fakeReddit.reddit.respond("/r/Kotlin/about", readFixture("subreddit_about.json"))
        fakeReddit.reddit.respond("/r/Kotlin/hot", postListing())
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                SubredditScreen(
                    subredditName = "Kotlin",
                    onBackClick = { calls += "back" },
                    onPostClick = { subreddit, id -> calls += "post $subreddit $id" },
                    onUserClick = { calls += "user $it" },
                )
            }
        }
    }

    @Test
    fun showsTheSubredditAndItsPosts() {
        showSubreddit()
        composeRule.onNodeWithText("Discussion about Kotlin", substring = true).assertExists()
        composeRule.onNodeWithText("108.9K members").assertExists()
        composeRule.onNodeWithText(FIRST_POST_TITLE).assertExists()
    }

    @Test
    fun joiningSubscribes() {
        showSubreddit()
        composeRule.onNodeWithText("Join").performClick()
        composeRule.onNodeWithText("Joined").assertExists()
        val subscribe = fakeReddit.reddit.requests.single { it.url.encodedPath == "/api/subscribe" }
        assertEquals("sub", subscribe.form()["action"])
        assertEquals("Kotlin", subscribe.form()["sr_name"])
    }

    @Test
    fun loggedOutCanNotJoin() {
        showSubreddit(loggedIn = false)
        composeRule.onNodeWithText("108.9K members").assertExists()
        composeRule.onNodeWithText("Join").assertDoesNotExist()
    }

    @Test
    fun tappingPostOpensIt() {
        showSubreddit()
        composeRule.onNodeWithText(FIRST_POST_TITLE).performClick()
        assertEquals(listOf("post pics 1x65iks"), calls)
    }

    @Test
    fun backGoesBack() {
        showSubreddit()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), calls)
    }
}
