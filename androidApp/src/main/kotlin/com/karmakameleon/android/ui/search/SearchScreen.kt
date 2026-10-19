package com.karmakameleon.android.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.android.ui.components.FeedPostItem
import com.karmakameleon.android.ui.components.LoadMoreNearEnd
import com.karmakameleon.android.ui.components.PostFeedSettings
import com.karmakameleon.android.ui.components.PostListCallbacks
import com.karmakameleon.android.ui.components.PostListItem
import com.karmakameleon.android.ui.components.UniversalTopAppBar
import com.karmakameleon.android.ui.components.loadingMoreItem
import com.karmakameleon.android.ui.components.rememberCompactPostSelection
import com.karmakameleon.android.ui.components.rememberPostFeedSettings
import com.karmakameleon.shared.data.repository.ReadPostsRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.domain.model.NsfwHistoryMode
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.SearchSort
import com.karmakameleon.shared.domain.model.SearchType
import com.karmakameleon.shared.ui.search.SearchUiState
import com.karmakameleon.shared.ui.search.SearchViewModel
import com.karmakameleon.shared.util.RedditLink
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    currentRoute: String? = null,
    onPostClick: (subreddit: String, postId: String) -> Unit,
    onCommentClick: (subreddit: String, postId: String, commentId: String) -> Unit = { s, p, _ -> onPostClick(s, p) },
    onSubredditClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onLinkClick: (String) -> Unit = {},
    viewModel: SearchViewModel = koinViewModel(),
    navigationHandler: NavigationHandler = koinInject(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val feedSettings = rememberPostFeedSettings()
    var showSortMenu by remember { mutableStateOf(false) }

    // Only post results come in pages.
    val canLoadMore = !uiState.isLoading && uiState.hasMore && uiState.searchType == SearchType.POST
    LoadMoreNearEnd(listState, uiState.posts.size, enabled = canLoadMore) { viewModel.loadMore() }

    Scaffold(
        topBar = {
            SearchTopBar(
                currentRoute = currentRoute,
                uiState = uiState,
                onQueryChange = viewModel::setQuery,
                onSortClick = { showSortMenu = true },
            )
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            SearchContent(
                uiState = uiState,
                listState = listState,
                feedSettings = feedSettings,
                onPostClick = onPostClick,
                onCommentClick = onCommentClick,
                onSubredditClick = onSubredditClick,
                onUserClick = onUserClick,
                onLinkClick = onLinkClick,
                navigationHandler = navigationHandler,
            )
        }
    }

    if (showSortMenu) {
        SearchSortSheet(
            currentSort = uiState.searchSort,
            onSelectSort = {
                viewModel.setSearchSort(it)
                showSortMenu = false
            },
            onDismiss = { showSortMenu = false },
        )
    }
}

// A prompt before anything is typed, a way to open a pasted Reddit link, or the results.
@Composable
private fun BoxScope.SearchContent(
    uiState: SearchUiState,
    listState: LazyListState,
    feedSettings: PostFeedSettings,
    onPostClick: (subreddit: String, postId: String) -> Unit,
    onCommentClick: (subreddit: String, postId: String, commentId: String) -> Unit,
    onSubredditClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    navigationHandler: NavigationHandler,
) {
    val link = uiState.detectedLink
    when {
        uiState.query.isEmpty() -> {
            SearchPrompt()
        }

        link != null -> {
            DetectedLinkCard(
                link = link,
                query = uiState.query,
                onPostClick = onPostClick,
                onCommentClick = onCommentClick,
                onSubredditClick = onSubredditClick,
                onUserClick = onUserClick,
                onShareLinkClick = { subreddit, shareId -> navigationHandler.onShareLinkClick(subreddit, shareId) },
            )
        }

        uiState.isLoading && uiState.posts.isEmpty() -> {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        uiState.posts.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No results found")
            }
        }

        else -> {
            // Results can't be voted on, saved or hidden here.
            SearchResults(
                posts = uiState.posts,
                isLoading = uiState.isLoading,
                listState = listState,
                feedSettings = feedSettings,
                callbacks =
                    PostListCallbacks(
                        onPostClick = onPostClick,
                        onSubredditClick = onSubredditClick,
                        onUserClick = onUserClick,
                        onLinkClick = onLinkClick,
                        onCrosspostClick = navigationHandler::handleLink,
                    ),
            )
        }
    }
}

// The app bar, the search field and, while searching posts, the sort and time filter.
@Composable
private fun SearchTopBar(
    currentRoute: String?,
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSortClick: () -> Unit,
) {
    Column {
        UniversalTopAppBar(
            currentRoute = currentRoute,
            title = { Text("Search") },
        )

        OutlinedTextField(
            value = uiState.query,
            onValueChange = onQueryChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search Reddit") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
        )

        if (uiState.query.isNotEmpty() && uiState.detectedLink == null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = false,
                    onClick = onSortClick,
                    label = { Text(uiState.searchSort.name.capitalized()) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp)) },
                )
                FilterChip(
                    selected = false,
                    onClick = {},
                    label = { Text(uiState.timeFilter.name.capitalized()) },
                )
            }
        }
    }
}

private fun String.capitalized(): String = lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun SearchPrompt() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Search for posts")
    }
}

// Shown instead of results when the query is a Reddit link, to open it directly.
@Composable
private fun DetectedLinkCard(
    link: RedditLink,
    query: String,
    onPostClick: (subreddit: String, postId: String) -> Unit,
    onCommentClick: (subreddit: String, postId: String, commentId: String) -> Unit,
    onSubredditClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onShareLinkClick: (subreddit: String, shareId: String) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable {
                    when (link) {
                        is RedditLink.Post -> {
                            onPostClick(link.subreddit, link.postId)
                        }

                        is RedditLink.Comment -> {
                            onCommentClick(link.subreddit, link.postId, link.commentId)
                        }

                        is RedditLink.Subreddit -> {
                            onSubredditClick(link.name)
                        }

                        is RedditLink.User -> {
                            onUserClick(link.name)
                        }

                        is RedditLink.ShareLink -> {
                            onShareLinkClick(link.subreddit, link.shareId)
                        }

                        is RedditLink.External -> {}
                    }
                },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = link.openLabel(),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = query.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun RedditLink.openLabel(): String =
    when (this) {
        is RedditLink.Post -> "Open post in r/$subreddit"
        is RedditLink.Comment -> "Open comment in r/$subreddit"
        is RedditLink.Subreddit -> "Open r/$name"
        is RedditLink.User -> "Open u/$name"
        is RedditLink.ShareLink -> "Open post in r/$subreddit"
        is RedditLink.External -> ""
    }

@Composable
private fun SearchResults(
    posts: List<Post>,
    isLoading: Boolean,
    listState: LazyListState,
    feedSettings: PostFeedSettings,
    callbacks: PostListCallbacks,
) {
    LazyColumn(state = listState) {
        items(posts, key = { it.id }) { post ->
            FeedPostItem(post = post, settings = feedSettings, callbacks = callbacks, isLoggedIn = false)
        }
        if (isLoading) loadingMoreItem()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSortSheet(
    currentSort: SearchSort,
    onSelectSort: (SearchSort) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                "Sort by",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            SearchSort.entries.forEach { sort ->
                ListItem(
                    headlineContent = { Text(sort.name.capitalized()) },
                    leadingContent =
                        if (sort == currentSort) {
                            { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                    modifier = Modifier.clickable { onSelectSort(sort) },
                )
            }
        }
    }
}
