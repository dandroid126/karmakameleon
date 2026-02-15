package com.reader.android.ui.subreddit

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
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.reader.android.ui.components.formatNumber
import com.reader.shared.data.repository.SettingsRepository
import com.reader.shared.data.repository.SubredditRepository
import com.reader.shared.data.repository.UserRepository
import com.reader.shared.domain.model.Subreddit
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubredditListScreen(
    onSubredditClick: (String) -> Unit
) {
    val subredditRepository: SubredditRepository = koinInject()
    val userRepository: UserRepository = koinInject()
    val settingsRepository: SettingsRepository = koinInject()
    
    val subscribedSubreddits by subredditRepository.sortedSubscribedSubreddits.collectAsState(emptyList())
    val favoriteSubreddits by settingsRepository.favoriteSubreddits.collectAsState()
    val isLoggedIn by userRepository.isLoggedIn.collectAsState()
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Subreddit>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && subscribedSubreddits.isEmpty()) {
            isLoading = true
            subredditRepository.loadSubscribedSubreddits()
            isLoading = false
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            isSearching = true
            val result = subredditRepository.searchSubreddits(searchQuery)
            result.onSuccess { searchResults = it }
            isSearching = false
        } else {
            searchResults = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subreddits") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search subreddits") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (searchQuery.isNotEmpty() && searchResults.isNotEmpty()) {
                LazyColumn {
                    item {
                        Text(
                            text = "Search Results",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(searchResults) { subreddit ->
                        SubredditListItem(
                            subreddit = subreddit,
                            isFavorite = subreddit.displayName.lowercase() in favoriteSubreddits,
                            onFavoriteClick = { settingsRepository.toggleFavoriteSubreddit(subreddit.displayName) },
                            onClick = { onSubredditClick(subreddit.displayName) }
                        )
                    }
                }
            } else if (!isLoggedIn) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Log in to see your subscriptions")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Use search to find subreddits",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (subscribedSubreddits.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No subscriptions yet")
                }
            } else {
                LazyColumn {
                    item {
                        Text(
                            text = "Your Subscriptions",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(
                        items = subscribedSubreddits,
                        key = { it.id }
                    ) { subreddit ->
                        SubredditListItem(
                            subreddit = subreddit,
                            isFavorite = subreddit.displayName.lowercase() in favoriteSubreddits,
                            onFavoriteClick = { settingsRepository.toggleFavoriteSubreddit(subreddit.displayName) },
                            onClick = { onSubredditClick(subreddit.displayName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubredditListItem(
    subreddit: Subreddit,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = subreddit.displayNamePrefixed,
                fontWeight = FontWeight.Medium
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
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = subreddit.displayName.first().uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
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
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
