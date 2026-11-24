package com.karmakameleon.android.ui.subreddit

import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.testing.MockReddit
import com.karmakameleon.shared.testing.listingOf
import com.karmakameleon.shared.testing.query
import com.karmakameleon.shared.testing.subredditThing
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.IOException

private val isProgressIndicator = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

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
    private fun isKeyboardShown(): Boolean {
        val inputMethods = RuntimeEnvironment.getApplication().getSystemService(InputMethodManager::class.java)
        return shadowOf(inputMethods).isSoftInputVisible
    }

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
    fun showsRedditsResultsBelowTheMatchingSubscriptions() {
        fakeReddit.reddit.respond(
            "/subreddits/search",
            listingOf(
                subredditThing("KotlinAndroid", nsfw = false, subscribers = 7_000),
                subredditThing("Kotlin", nsfw = false, subscribers = 108_935),
            ),
        )
        showList()

        composeRule.onNode(hasSetTextAction()).performTextInput("kot")
        composeRule.onNodeWithText("Your Subscriptions").assertExists()
        composeRule.onNodeWithText("Search Results").assertExists()
        // r/Kotlin shows once, as a subscription.
        assertEquals(listOf("r/Kotlin", "r/KotlinAndroid"), shownSubreddits())
    }

    @Test
    fun saysWhenRedditFoundOnlyTheSubscriptions() {
        fakeReddit.reddit.respond("/subreddits/search", listingOf(subredditThing("Kotlin", nsfw = false, subscribers = 108_935)))
        showList()
        composeRule.onNode(hasSetTextAction()).performTextInput("kot")
        composeRule.onNodeWithText("Only your subscriptions matched").assertExists()
        assertEquals(listOf("r/Kotlin"), shownSubreddits())
    }

    @Test
    fun showsASpinnerWhileRedditSearches() {
        fakeReddit.reddit.respond("/subreddits/search", listingOf(subredditThing("KotlinAndroid", nsfw = false, subscribers = 7_000)))
        fakeReddit.reddit.holdRequests("/subreddits/search")
        showList()

        composeRule.onNode(hasSetTextAction()).performTextInput("kot")
        composeRule.onNode(isProgressIndicator).assertExists()
        assertEquals(listOf("r/Kotlin"), shownSubreddits())

        fakeReddit.reddit.letThrough("/subreddits/search")
        composeRule.waitUntil { shownSubreddits().size == 2 }
        composeRule.onNode(isProgressIndicator).assertDoesNotExist()
    }

    @Test
    fun showsASpinnerWhileTheSubscriptionsLoad() {
        fakeReddit.reddit.holdRequests("/subreddits/mine/subscriber")
        showList()
        composeRule.onNode(isProgressIndicator).assertExists()

        // Searching keeps the subscriptions' spinner in their own section.
        composeRule.onNode(hasSetTextAction()).performTextInput("kot")
        composeRule.onNodeWithText("Your Subscriptions").assertExists()
        composeRule.onNode(isProgressIndicator).assertExists()
        fakeReddit.reddit.letThrough("/subreddits/mine/subscriber")
    }

    @Test
    fun explainsAFailedSearchAndRetriesIt() {
        fakeReddit.reddit.respond("/subreddits/search", "", HttpStatusCode.ServiceUnavailable)
        showList()

        composeRule.onNode(hasSetTextAction()).performTextInput("kot")
        composeRule.onNodeWithText("Reddit is unavailable right now. Try again later.").assertExists()
        // The error is Reddit's search's alone, so the matching subscriptions still show.
        assertEquals(listOf("r/Kotlin"), shownSubreddits())

        fakeReddit.reddit.respond("/subreddits/search", listingOf(subredditThing("KotlinAndroid", nsfw = false, subscribers = 7_000)))
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("r/Kotlin", "r/KotlinAndroid"), shownSubreddits())
        composeRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun saysWhenRedditCantBeReached() {
        fakeReddit.reddit.fail("/subreddits/search", IOException("Connection reset"))
        showList()
        composeRule.onNode(hasSetTextAction()).performTextInput("kot")
        composeRule.onNodeWithText("Couldn't reach Reddit. Check your connection and try again.").assertExists()

        fakeReddit.reddit.respond("/subreddits/search", listingOf(subredditThing("KotlinAndroid", nsfw = false, subscribers = 7_000)))
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("r/Kotlin", "r/KotlinAndroid"), shownSubreddits())
    }

    // Like having no client ID, which RedditApi reports before making the request.
    @Test
    fun showsTheMessageOfAnyOtherError() {
        fakeReddit.reddit.fail("/subreddits/search", IllegalStateException("No Reddit Client ID configured."))
        showList()
        composeRule.onNode(hasSetTextAction()).performTextInput("kot")
        composeRule.onNodeWithText("No Reddit Client ID configured.").assertExists()
    }

    @Test
    fun saysWhenRedditsAnswerCantBeRead() {
        fakeReddit.reddit.respond("/subreddits/search", "<html>")
        showList()
        composeRule.onNode(hasSetTextAction()).performTextInput("kot")
        composeRule.onNodeWithText("Reddit's answer couldn't be read. Try again.").assertExists()
    }

    @Test
    fun searchingLoggedOutStillAsksToLogInForSubscriptions() {
        fakeReddit.reddit.respond("/subreddits/search", listingOf(subredditThing("Kotlin", nsfw = false, subscribers = 108_935)))
        showList(loggedIn = false)
        composeRule.onNode(hasSetTextAction()).performTextInput("kot")
        composeRule.onNodeWithText("Log in to see your subscriptions").assertExists()
        assertEquals(listOf("r/Kotlin"), shownSubreddits())
    }

    @Test
    fun saysWhenNoSubscriptionMatchesAQueryTooShortToSearch() {
        showList()
        composeRule.onNode(hasSetTextAction()).performTextInput("z")
        composeRule.onNodeWithText("No matching subscriptions").assertExists()
        composeRule.onNodeWithText("Type at least 2 characters to search Reddit").assertExists()
        composeRule.onNodeWithText("Go to", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("r/Kotlin").assertDoesNotExist()
    }

    @Test
    fun saysWhenRedditFindsNothingEither() {
        showList()
        composeRule.onNode(hasSetTextAction()).performTextInput("zz")
        composeRule.onNodeWithText("No subreddits found").assertExists()
        composeRule.onNodeWithText("r/Kotlin").assertDoesNotExist()
    }

    // Reddit's search sometimes finds nothing for a while, so finding nothing can be retried.
    @Test
    fun retriesASearchThatFoundNothing() {
        showList()
        composeRule.onNode(hasSetTextAction()).performTextInput("zz")
        composeRule.onNodeWithText("No subreddits found").assertExists()

        fakeReddit.reddit.respond("/subreddits/search", listingOf(subredditThing("zzz", nsfw = false, subscribers = 12)))
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("r/zzz"), shownSubreddits())
        composeRule.onNodeWithText("No subreddits found").assertDoesNotExist()
    }

    // Reddit's search sometimes finds nothing, so a subreddit whose name is known can be opened without it.
    @Test
    fun goesToTheTypedSubredditWhateverRedditFinds() {
        showList()
        composeRule.onNode(hasSetTextAction()).performTextInput("r/AskHockey")
        composeRule.onNodeWithText("No subreddits found").assertExists()
        composeRule.onNodeWithText("Go to r/AskHockey").performClick()
        assertEquals(listOf("AskHockey"), opened)
    }

    @Test
    fun goesToTheTypedSubredditFromTheKeyboard() {
        showList()
        composeRule.onNode(hasSetTextAction()).performTextInput(" ask hockey ")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        assertEquals(listOf("askhockey"), opened)
    }

    @Test
    fun offersNoSubredditForAQueryThatCantBeOne() {
        showList()
        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("c++")
        assertTrue(isKeyboardShown())
        composeRule.onNodeWithText("Go to", substring = true).assertDoesNotExist()
        composeRule.onNode(hasSetTextAction()).performImeAction()
        assertEquals(emptyList<String>(), opened)
        // The keyboard still closes, so the search results under it show.
        composeRule.waitForIdle()
        assertFalse(isKeyboardShown())
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
