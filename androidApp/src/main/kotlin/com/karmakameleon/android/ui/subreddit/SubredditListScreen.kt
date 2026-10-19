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
    currentRoute: String? = null,
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
    val isLoading = loadSubscriptionsOnLogin(isLoggedIn, hasSubscriptions = subscribedSubreddits.isNotEmpty(), subredditRepository)
    val searchResults = searchSubreddits(searchQuery, includeOver18 = nsfwEnabled && nsfwSearchEnabled, subredditRepository)
    val visibleSubscriptions = remember(subscribedSubreddits, nsfwEnabled) { subscribedSubreddits.filter { nsfwEnabled || !it.isNsfw } }
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
        topBar = { UniversalTopAppBar(currentRoute = currentRoute, title = { Text("Subreddits") }) },
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
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                searchQuery.isNotEmpty() && searchResults.isNotEmpty() -> {
                    section("Search Results", searchResults)
                }

                !isLoggedIn -> {
                    CenteredMessage("Log in to see your subscriptions", "Use search to find subreddits")
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

// Searches once the query is at least two characters long.
@Composable
private fun searchSubreddits(
    query: String,
    includeOver18: Boolean,
    subredditRepository: SubredditRepository,
): List<Subreddit> {
    var results by remember { mutableStateOf<List<Subreddit>>(emptyList()) }
    LaunchedEffect(query, includeOver18) {
        if (query.length >= 2) {
            subredditRepository.searchSubreddits(query, includeOver18 = includeOver18).onSuccess { results = it }
        } else {
            results = emptyList()
        }
    }
    return results
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
