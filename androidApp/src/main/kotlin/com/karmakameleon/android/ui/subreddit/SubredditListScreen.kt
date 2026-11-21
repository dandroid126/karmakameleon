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
import com.karmakameleon.android.ui.components.UniversalTopAppBar
import com.karmakameleon.android.ui.components.formatNumber
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.data.repository.SubredditRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.domain.model.Subreddit
import org.koin.compose.koinInject

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
    val matches = rememberSearchMatches(query, visibleSubscriptions, search)
    val section = @Composable { title: String, subreddits: List<Subreddit> ->
        SubredditSection(
            title = title,
            subreddits = subreddits,
            favoriteSubreddits = favoriteSubreddits,
            onFavoriteClick = settingsRepository::toggleFavoriteSubreddit,
            onSubredditClick = onSubredditClick,
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
                isLoading -> {
                    LoadingIndicator()
                }

                query.isNotEmpty() && matches.isNotEmpty() -> {
                    section("Search Results", matches)
                }

                query.length >= MIN_SEARCH_LENGTH && search.isDoneFor(query) -> {
                    CenteredMessage("No subreddits found")
                }

                !isLoggedIn -> {
                    CenteredMessage("Log in to see your subscriptions", "Use search to find subreddits")
                }

                // While Reddit's search is running, or before the query is long enough for one.
                query.isNotEmpty() -> {
                    CenteredMessage("No matching subscriptions")
                }

                visibleSubscriptions.isEmpty() -> {
                    CenteredMessage("No subscriptions yet")
                }

                else -> {
                    section("Your Subscriptions", visibleSubscriptions)
                }
            }
        }
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

// Reddit's results for a query, or none yet while the search is running.
private class RedditSearch(
    val query: String,
    val results: List<Subreddit>,
    val isDone: Boolean,
) {
    // Whether this search is for the query and has finished. Right after the query changes,
    // the search for the old one is still here until the new one starts.
    fun isDoneFor(query: String): Boolean = this.query == query && isDone
}

// The subscriptions whose names contain the query, those starting with it first, then Reddit's
// results that aren't among them. The subscriptions show at once, without waiting for Reddit.
// The query comes trimmed: subreddit names can't contain spaces, and keyboards add one after a
// suggested word.
@Composable
private fun rememberSearchMatches(
    query: String,
    subscriptions: List<Subreddit>,
    search: RedditSearch,
): List<Subreddit> = remember(query, subscriptions, search) { searchMatches(query, subscriptions, search) }

private fun searchMatches(
    query: String,
    subscriptions: List<Subreddit>,
    search: RedditSearch,
): List<Subreddit> {
    if (query.isEmpty()) return emptyList()
    val searchResults = if (search.query == query) search.results else emptyList()
    val subscribed =
        subscriptions
            .filter { it.displayName.contains(query, ignoreCase = true) }
            .sortedByDescending { it.displayName.startsWith(query, ignoreCase = true) }
    val subscribedNames = subscribed.mapTo(HashSet()) { it.displayName.lowercase() }
    return subscribed + searchResults.filter { it.displayName.lowercase() !in subscribedNames }
}

// Searches Reddit once the query is at least MIN_SEARCH_LENGTH characters long. A failed search counts as
// finding nothing.
@Composable
private fun searchSubreddits(
    query: String,
    includeOver18: Boolean,
    subredditRepository: SubredditRepository,
): RedditSearch {
    var search by remember { mutableStateOf(RedditSearch("", emptyList(), isDone = true)) }
    LaunchedEffect(query, includeOver18) {
        val searchesReddit = query.length >= MIN_SEARCH_LENGTH
        search = RedditSearch(query, emptyList(), isDone = !searchesReddit)
        if (searchesReddit) {
            val results = subredditRepository.searchSubreddits(query, includeOver18 = includeOver18).getOrDefault(emptyList())
            search = RedditSearch(query, results, isDone = true)
        }
    }
    return search
}

@Composable
private fun SubredditSection(
    title: String,
    subreddits: List<Subreddit>,
    favoriteSubreddits: Set<String>,
    onFavoriteClick: (String) -> Unit,
    onSubredditClick: (String) -> Unit,
) {
    LazyColumn {
        item {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(items = subreddits, key = { it.id }) { subreddit ->
            SubredditListItem(
                subreddit = subreddit,
                isFavorite = subreddit.displayName.lowercase() in favoriteSubreddits,
                onFavoriteClick = { onFavoriteClick(subreddit.displayName) },
                onClick = { onSubredditClick(subreddit.displayName) },
            )
        }
    }
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
