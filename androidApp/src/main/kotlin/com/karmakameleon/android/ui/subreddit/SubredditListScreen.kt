package com.karmakameleon.android.ui.subreddit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.karmakameleon.android.ui.components.LoadError
import com.karmakameleon.android.ui.components.UniversalTopAppBar
import com.karmakameleon.android.ui.components.formatNumber
import com.karmakameleon.shared.data.api.RedditHttpException
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.data.repository.SubredditRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.domain.model.Subreddit
import kotlinx.serialization.SerializationException
import org.koin.compose.koinInject
import java.io.IOException

@Composable
fun SubredditListScreen(
    onSubredditClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    subredditRepository: SubredditRepository = koinInject(),
    userRepository: UserRepository = koinInject(),
    settingsRepository: SettingsRepository = koinInject(),
) {
    val subscribedSubreddits by subredditRepository.sortedSubscribedSubreddits.collectAsState(emptyList())
    val favoriteSubreddits by settingsRepository.favoriteSubreddits.collectAsState()
    val nsfwEnabled by settingsRepository.nsfwEnabled.collectAsState()
    val nsfwSearchEnabled by settingsRepository.nsfwSearchEnabled.collectAsState()
    val isLoggedIn by userRepository.isLoggedIn.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val query = searchQuery.trim()
    val isLoading = loadSubscriptionsOnLogin(isLoggedIn, hasSubscriptions = subscribedSubreddits.isNotEmpty(), subredditRepository)
    val search = searchSubreddits(query, includeOver18 = nsfwEnabled && nsfwSearchEnabled, subredditRepository)
    val visibleSubscriptions = remember(subscribedSubreddits, nsfwEnabled) { subscribedSubreddits.filter { nsfwEnabled || !it.isNsfw } }
    val matchingSubscriptions = remember(query, visibleSubscriptions) { matchingSubscriptions(query, visibleSubscriptions) }
    val subredditItem = @Composable { subreddit: Subreddit ->
        SubredditListItem(
            subreddit = subreddit,
            isFavorite = subreddit.displayName.lowercase() in favoriteSubreddits,
            onFavoriteClick = { settingsRepository.toggleFavoriteSubreddit(subreddit.displayName) },
            onClick = { onSubredditClick(subreddit.displayName) },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = { UniversalTopAppBar(title = { Text("Subreddits") }) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            SubredditSearchField(query = searchQuery, onQueryChange = { searchQuery = it })

            when {
                isLoading && query.isEmpty() -> {
                    LoadingIndicator()
                }

                !isLoggedIn && query.isEmpty() -> {
                    CenteredMessage("Log in to see your subscriptions", "Use search to find subreddits")
                }

                visibleSubscriptions.isEmpty() && query.isEmpty() -> {
                    CenteredMessage("No subscriptions yet")
                }

                else -> {
                    SubredditSections(
                        subscriptions = if (query.isEmpty()) visibleSubscriptions else matchingSubscriptions,
                        isLoadingSubscriptions = isLoading,
                        isLoggedIn = isLoggedIn,
                        search = search.takeIf { query.isNotEmpty() },
                        subredditItem = subredditItem,
                    )
                }
            }
        }
    }
}

// The subscriptions, or those matching the query, and below them Reddit's search for it, if there's a query. Each
// section says when it has nothing, so an empty search isn't mistaken for one still running or one that failed.
@Composable
private fun SubredditSections(
    subscriptions: List<Subreddit>,
    isLoadingSubscriptions: Boolean,
    isLoggedIn: Boolean,
    search: RedditSearch?,
    subredditItem: @Composable (Subreddit) -> Unit,
) {
    val showsSubscriptions = !isLoadingSubscriptions && isLoggedIn
    val shownAbove = if (showsSubscriptions) subscriptions.mapTo(HashSet()) { it.displayName.lowercase() } else emptySet()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        sectionTitle("Your Subscriptions")
        when {
            isLoadingSubscriptions -> item(key = "subscriptions-loading") { LoadingIndicator() }
            !isLoggedIn -> sectionMessage("Log in to see your subscriptions")
            subscriptions.isEmpty() -> sectionMessage("No matching subscriptions")
            else -> items(subscriptions, key = { "subscription-${it.id}" }) { subredditItem(it) }
        }
        if (search != null) {
            sectionTitle("Search Results")
            searchResults(search, shownAbove, subredditItem)
        }
    }
}

private fun LazyListScope.sectionTitle(title: String) {
    item(key = "title-$title") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(16.dp),
        )
    }
}

private fun LazyListScope.sectionMessage(text: String) {
    item(key = "message-$text") {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

// Reddit's results, without the subscriptions listed above. When those were all Reddit found, it says so, so that
// isn't mistaken for Reddit finding nothing.
private fun LazyListScope.searchResults(
    search: RedditSearch,
    shownAbove: Set<String>,
    subredditItem: @Composable (Subreddit) -> Unit,
) {
    when (val state = search.state) {
        RedditSearch.State.TooShort -> {
            sectionMessage("Type at least $MIN_SEARCH_LENGTH characters to search Reddit")
        }

        RedditSearch.State.Searching -> {
            item(key = "results-loading") { LoadingIndicator() }
        }

        is RedditSearch.State.Failed -> {
            retryableMessage(state.message, search.retry)
        }

        is RedditSearch.State.Found -> {
            val results = state.results.filter { it.displayName.lowercase() !in shownAbove }
            when {
                // Reddit's search sometimes finds nothing for a while, and a subreddit made since the search wasn't
                // there yet, so finding nothing can be retried too.
                state.results.isEmpty() -> retryableMessage("No subreddits found", search.retry)

                results.isEmpty() -> sectionMessage("Only your subscriptions matched")

                else -> items(results, key = { "result-${it.id}" }) { subredditItem(it) }
            }
        }
    }
}

private fun LazyListScope.retryableMessage(
    text: String,
    onRetry: () -> Unit,
) {
    item(key = "results-retry") {
        LoadError(
            error = text,
            onRetry = onRetry,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
    }
}

@Composable
private fun SubredditSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search subreddits") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
    )
}

// Loads the subscriptions once logged in, if they aren't loaded yet. Returns whether they're loading.
@Composable
private fun loadSubscriptionsOnLogin(
    isLoggedIn: Boolean,
    hasSubscriptions: Boolean,
    subredditRepository: SubredditRepository,
): Boolean {
    var isLoading by remember { mutableStateOf(false) }
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && !hasSubscriptions) {
            isLoading = true
            subredditRepository.loadSubscribedSubreddits()
            isLoading = false
        }
    }
    return isLoading
}

private const val MIN_SEARCH_LENGTH = 2

// Reddit's search for the query the screen shows, and a way to run it again after it failed.
private class RedditSearch(
    val state: State,
    val retry: () -> Unit,
) {
    sealed interface State {
        // The query is too short for Reddit to search.
        data object TooShort : State

        data object Searching : State

        data class Found(
            val results: List<Subreddit>,
        ) : State

        data class Failed(
            val message: String,
        ) : State
    }
}

// The subscriptions whose names contain the query, those starting with it first. They show at once, without waiting
// for Reddit. Subreddit names can't contain spaces, so they're matched without the query's spaces: "baby big" finds
// r/babybigcatgifs. Reddit gets the query as typed, since it also searches titles and descriptions.
private fun matchingSubscriptions(
    query: String,
    subscriptions: List<Subreddit>,
): List<Subreddit> {
    if (query.isEmpty()) return emptyList()
    val name = query.filterNot(Char::isWhitespace)
    return subscriptions
        .filter { it.displayName.contains(name, ignoreCase = true) }
        .sortedByDescending { it.displayName.startsWith(name, ignoreCase = true) }
}

// Searches Reddit once the query is at least MIN_SEARCH_LENGTH characters long. The state is always for the query
// passed in: right after the query changes, the old query's results aren't shown while the new search starts.
@Composable
private fun searchSubreddits(
    query: String,
    includeOver18: Boolean,
    subredditRepository: SubredditRepository,
): RedditSearch {
    var attempt by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf<Pair<String, RedditSearch.State>?>(null) }
    LaunchedEffect(query, includeOver18, attempt) {
        finished = null
        if (query.length >= MIN_SEARCH_LENGTH) {
            val state =
                subredditRepository.searchSubreddits(query, includeOver18 = includeOver18).fold(
                    onSuccess = { RedditSearch.State.Found(it) },
                    onFailure = { RedditSearch.State.Failed(searchErrorMessage(it)) },
                )
            finished = query to state
        }
    }
    val done = finished
    val state =
        when {
            query.length < MIN_SEARCH_LENGTH -> RedditSearch.State.TooShort
            done != null && done.first == query -> done.second
            else -> RedditSearch.State.Searching
        }
    return RedditSearch(state, retry = { attempt++ })
}

// Reddit's error says what went wrong. A timeout or no network is an IOException, and an answer that isn't the JSON
// expected is a SerializationException. Anything else, like having no client ID, has its own message, as on the other
// screens.
private fun searchErrorMessage(error: Throwable): String =
    when (error) {
        is RedditHttpException -> error.message.orEmpty()
        is IOException -> "Couldn't reach Reddit. Check your connection and try again."
        is SerializationException -> "Reddit's answer couldn't be read. Try again."
        else -> error.message ?: "Couldn't search Reddit. Try again."
    }

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredMessage(
    text: String,
    detail: String? = null,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text)
            if (detail != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SubredditListItem(
    subreddit: Subreddit,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = subreddit.displayNamePrefixed,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            Text("${formatNumber(subreddit.subscribers.toInt())} members")
        },
        leadingContent = {
            if (subreddit.iconUrl != null) {
                AsyncImage(
                    model = subreddit.iconUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = subreddit.displayName.first().uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
