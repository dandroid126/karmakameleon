package com.reader.android.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import com.reader.android.navigation.NavigationHandler
import com.reader.android.ui.components.PostCard
import com.reader.shared.data.repository.ReadPostsRepository
import com.reader.shared.data.repository.SettingsRepository
import com.reader.shared.domain.model.NsfwHistoryMode
import com.reader.shared.domain.model.NsfwPreviewMode
import com.reader.shared.domain.model.SearchSort
import com.reader.shared.domain.model.SearchType
import com.reader.shared.ui.search.SearchViewModel
import com.reader.shared.util.RedditLink
import com.reader.android.ui.components.UniversalTopAppBar
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
    navigationHandler: NavigationHandler = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val readPostsRepository: ReadPostsRepository = koinInject()
    val settingsRepository: SettingsRepository = koinInject()
    val readPostIds by readPostsRepository.readPostIds.collectAsState()
    val nsfwEnabled by settingsRepository.nsfwEnabled.collectAsState()
    val nsfwHistoryMode by settingsRepository.nsfwHistoryMode.collectAsState()
    val nsfwPreviewMode by settingsRepository.nsfwPreviewMode.collectAsState()
    val effectiveNsfwPreviewMode = if (nsfwEnabled) nsfwPreviewMode else NsfwPreviewMode.DO_NOT_PREFETCH
    val effectiveNsfwHistoryMode = if (nsfwEnabled) nsfwHistoryMode else NsfwHistoryMode.DONT_SAVE_ANY_NSFW
    var showSortMenu by remember { mutableStateOf(false) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= uiState.posts.size - 5
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !uiState.isLoading && uiState.hasMore && uiState.searchType == SearchType.POST) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            Column {
                UniversalTopAppBar(
                    currentRoute = currentRoute,
                    title = { Text("Search") }
                )
                
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search Reddit") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true
                )

                if (uiState.query.isNotEmpty() && uiState.detectedLink == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = { showSortMenu = true },
                            label = { Text(uiState.searchSort.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp)) }
                        )
                        FilterChip(
                            selected = false,
                            onClick = {},
                            label = { Text(uiState.timeFilter.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.query.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Search for posts")
                }
            } else if (uiState.detectedLink != null) {
                val link = uiState.detectedLink
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable {
                            when (link) {
                                is RedditLink.Post -> onPostClick(link.subreddit, link.postId)
                                is RedditLink.Comment -> onCommentClick(link.subreddit, link.postId, link.commentId)
                                is RedditLink.Subreddit -> onSubredditClick(link.name)
                                is RedditLink.User -> onUserClick(link.name)
                                is RedditLink.External, null -> {}
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (link) {
                                    is RedditLink.Post -> "Open post in r/${link.subreddit}"
                                    is RedditLink.Comment -> "Open comment in r/${link.subreddit}"
                                    is RedditLink.Subreddit -> "Open r/${link.name}"
                                    is RedditLink.User -> "Open u/${link.name}"
                                    is RedditLink.External, null -> ""
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = uiState.query.trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            } else if (uiState.isLoading && uiState.posts.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                if (uiState.posts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results found")
                    }
                } else {
                    LazyColumn(state = listState) {
                        items(uiState.posts, key = { it.id }) { post ->
                            PostCard(
                                post = post,
                                onClick = {
                                    readPostsRepository.markAsRead(post, effectiveNsfwHistoryMode)
                                    onPostClick(post.subreddit, post.id)
                                },
                                onSubredditClick = { onSubredditClick(post.subreddit) },
                                onUserClick = { onUserClick(post.author) },
                                onUpvote = {},
                                onDownvote = {},
                                onSave = {},
                                onHide = {},
                                isLoggedIn = false,
                                onLinkClick = onLinkClick,
                                onCrosspostClick = {
                                    post.crosspostParentPermalink?.let { navigationHandler.handleLink(it) }
                                },
                                isRead = readPostsRepository.isRead(post, effectiveNsfwHistoryMode),
                                nsfwPreviewMode = effectiveNsfwPreviewMode
                            )
                        }

                        if (uiState.isLoading) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSortMenu) {
        ModalBottomSheet(onDismissRequest = { showSortMenu = false }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    "Sort by",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                SearchSort.entries.forEach { sort ->
                    if (uiState.searchSort == sort) {
                        ListItem(
                            headlineContent = { Text(sort.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            leadingContent = { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                viewModel.setSearchSort(sort)
                                showSortMenu = false
                            }
                        )
                    } else {
                        ListItem(
                            headlineContent = { Text(sort.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.clickable {
                                viewModel.setSearchSort(sort)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

