package com.karmakameleon.android.ui.subreddit

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.testing.MockReddit
import com.karmakameleon.shared.testing.listingOf
import com.karmakameleon.shared.testing.query
import com.karmakameleon.shared.testing.subredditThing
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Subscriptions in the order Reddit sends them, which isn't the order the screen shows them in.
val subscriptions =
    listingOf(
        subredditThing("pics", nsfw = false, subscribers = 32_100_000),
        subredditThing("Kotlin", nsfw = false, subscribers = 108_935),
        subredditThing("androiddev", nsfw = false, subscribers = 245_000),
        subredditThing("NSFW_Art", nsfw = true, subscribers = 12_000),
    )

@RunWith(RobolectricTestRunner::class)
class SubredditListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeReddit = FakeRedditRule()

    private val opened = mutableListOf<String>()

    private fun showList(
        loggedIn: Boolean = true,
        subscribed: String = subscriptions,
    ) {
        fakeReddit.loggedIn = loggedIn
        fakeReddit.reddit.respond("/subreddits/mine/subscriber", subscribed)
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                SubredditListScreen(onSubredditClick = { opened += it })
            }
        }
    }

    @Test
    fun loggedOutAsksToLogIn() {
        showList(loggedIn = false)
        composeRule.onNodeWithText("Log in to see your subscriptions").assertExists()
        assertEquals(emptyList<Any>(), fakeReddit.reddit.requests)
    }

    @Test
    fun showsSubscriptionsWithoutNsfwOnes() {
        showList()
        composeRule.onNodeWithText("Your Subscriptions").assertExists()
        composeRule.onNodeWithText("r/Kotlin").assertExists()
        composeRule.onNodeWithText("108.9K members").assertExists()
        composeRule.onNodeWithText("r/NSFW_Art").assertDoesNotExist()
    }

    @Test
    fun showsNsfwSubscriptionsWhenNsfwIsOn() {
        fakeReddit.settings.setNsfwEnabled(true)
        showList()
        composeRule.onNodeWithText("r/NSFW_Art").assertExists()
    }

    @Test
    fun noSubscriptions() {
        fakeReddit.loggedIn = true
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) { SubredditListScreen(onSubredditClick = {}) }
        }
        composeRule.onNodeWithText("No subscriptions yet").assertExists()
    }

    @Test
    fun tappingSubredditOpensIt() {
        showList()
        composeRule.onNodeWithText("r/Kotlin").performClick()
        assertEquals(listOf("Kotlin"), opened)
    }

    @Test
    fun favoriting() {
        showList()
        // The list is alphabetical, so androiddev's button comes first.
        composeRule.onAllNodesWithContentDescription("Add to favorites").onFirst().performClick()
        assertEquals(setOf("androiddev"), fakeReddit.settings.favoriteSubreddits.value)

        composeRule.onNodeWithContentDescription("Remove from favorites").performClick()
        assertEquals(emptySet<String>(), fakeReddit.settings.favoriteSubreddits.value)
    }

    @Test
    fun searching() {
        fakeReddit.reddit.respond("/subreddits/search", listingOf(subredditThing("Kotlin", nsfw = false, subscribers = 108_935)))
        showList(loggedIn = false)

        composeRule.onNode(hasSetTextAction()).performTextInput("kot")
        composeRule.onNodeWithText("Search Results").assertExists()
        composeRule.onNodeWithText("r/Kotlin").performClick()

        val search = fakeReddit.reddit.requests.last { it.url.encodedPath == "/subreddits/search" }
        assertEquals("kot", search.query()["q"])
        assertEquals("false", search.query()["include_over_18"])
        assertEquals(listOf("Kotlin"), opened)
    }

    // The subreddit names shown, top to bottom.
    private fun shownSubreddits(): List<String> =
        composeRule
            .onAllNodes(hasText("r/", substring = true))
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }
            .map { it.text }
            .filter { it.startsWith("r/") }

    @Test
    fun filtersSubscriptionsAsYouType() {
        showList()

        // One character is too short to search Reddit, so the matches come from the subscriptions alone.
        composeRule.onNode(hasSetTextAction()).performTextInput("D")
        assertEquals(listOf("r/androiddev"), shownSubreddits())
        assertEquals(0, fakeReddit.reddit.requests.count { it.url.encodedPath == "/subreddits/search" })
    }

    @Test
    fun listsSubscriptionsStartingWithTheQueryFirst() {
        showList(
            subscribed =
                listingOf(
                    subredditThing("androiddev", nsfw = false, subscribers = 245_000),
                    subredditThing("Design", nsfw = false, subscribers = 1_000),
                    subredditThing("pics", nsfw = false, subscribers = 32_100_000),
                ),
        )
        composeRule.onNode(hasSetTextAction()).performTextInput("d")
        assertEquals(listOf("r/Design", "r/androiddev"), shownSubreddits())
    }

    @Test
    fun addsRedditsResultsAfterTheMatchingSubscriptions() {
        fakeReddit.reddit.respond(
            "/subreddits/search",
            listingOf(
                subredditThing("KotlinAndroid", nsfw = false, subscribers = 7_000),
                subredditThing("Kotlin", nsfw = false, subscribers = 108_935),
            ),
        )
        showList()

        composeRule.onNode(hasSetTextAction()).performTextInput("kot")
        composeRule.waitForIdle()
        assertEquals(listOf("r/Kotlin", "r/KotlinAndroid"), shownSubreddits())
    }

    @Test
    fun saysWhenNoSubscriptionMatchesAQueryTooShortToSearch() {
        showList()
        composeRule.onNode(hasSetTextAction()).performTextInput("z")
        composeRule.onNodeWithText("No matching subscriptions").assertExists()
        composeRule.onNodeWithText("r/Kotlin").assertDoesNotExist()
    }

    @Test
    fun saysWhenRedditFindsNothingEither() {
        showList()
        composeRule.onNode(hasSetTextAction()).performTextInput("zz")
        composeRule.onNodeWithText("No subreddits found").assertExists()
        composeRule.onNodeWithText("r/Kotlin").assertDoesNotExist()
    }

    @Test
    fun dropsTheResultsOfTheLastQuery() {
        fakeReddit.reddit.respond("/subreddits/search", listingOf(subredditThing("KotlinAndroid", nsfw = false, subscribers = 7_000)))
        showList()
        composeRule.onNode(hasSetTextAction()).performTextInput("ko")
        assertEquals(listOf("r/Kotlin", "r/KotlinAndroid"), shownSubreddits())

        fakeReddit.reddit.respond("/subreddits/search", MockReddit.EMPTY_LISTING)
        composeRule.onNode(hasSetTextAction()).performTextInput("x")
        composeRule.onNodeWithText("No subreddits found").assertExists()
        assertEquals(emptyList<String>(), shownSubreddits())
    }

    @Test
    fun matchesSubscriptionsWithoutTheQuerysSpaces() {
        showList()
        composeRule.onNode(hasSetTextAction()).performTextInput("android dev")
        assertEquals(listOf("r/androiddev"), shownSubreddits())
        // Reddit also searches titles and descriptions, so it gets the spaces.
        val search = fakeReddit.reddit.requests.last { it.url.encodedPath == "/subreddits/search" }
        assertEquals("android dev", search.query()["q"])
    }

    @Test
    fun ignoresSpacesAroundTheQuery() {
        showList()
        composeRule.onNode(hasSetTextAction()).performTextInput("kotlin ")
        assertEquals(listOf("r/Kotlin"), shownSubreddits())
        val search = fakeReddit.reddit.requests.last { it.url.encodedPath == "/subreddits/search" }
        assertEquals("kotlin", search.query()["q"])
    }
}
