package com.reader.android.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.PostCard
import com.reader.android.ui.components.RedditLink
import com.reader.android.ui.components.SortBottomSheet
import com.reader.android.ui.components.parseRedditLink
import com.reader.shared.domain.model.PostSort
import com.reader.shared.domain.model.TimeFilter
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onPostClick: (subreddit: String, postId: String) -> Unit,
    onSubredditClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onLinkClick: (String) -> Unit = {},
    viewModel: FeedViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var showSortSheet by remember { mutableStateOf(false) }
    
    // Load more when reaching end
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= uiState.posts.size - 5
        }
    }
    
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !uiState.isLoadingMore && uiState.hasMore) {
            viewModel.loadMorePosts()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.currentSubreddit?.let { "r/$it" } ?: "Home")
                },
                actions = {
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort")
                    }
                    IconButton(onClick = { viewModel.loadPosts(forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading && uiState.posts.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.error != null && uiState.posts.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadPosts(forceRefresh = true) }) {
                        Text("Retry")
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = uiState.posts,
                        key = { it.id }
                    ) { post ->
                        PostCard(
                            post = post,
                            onClick = { onPostClick(post.subreddit, post.id) },
                            onSubredditClick = { onSubredditClick(post.subreddit) },
                            onUserClick = { onUserClick(post.author) },
                            onUpvote = { viewModel.vote(post, if (post.likes == true) 0 else 1) },
                            onDownvote = { viewModel.vote(post, if (post.likes == false) 0 else -1) },
                            onSave = { viewModel.save(post) },
                            onHide = { viewModel.hide(post) },
                            isLoggedIn = uiState.isLoggedIn,
                            onLinkClick = { url ->
                                when (val link = parseRedditLink(url)) {
                                    is RedditLink.Subreddit -> onSubredditClick(link.name)
                                    is RedditLink.User -> onUserClick(link.name)
                                    is RedditLink.Post -> onPostClick(link.subreddit, link.postId)
                                    is RedditLink.External -> onLinkClick(url)
                                }
                            }
                        )
                    }
                    
                    if (uiState.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
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

    if (showSortSheet) {
        SortBottomSheet(
            currentSort = uiState.currentSort,
            currentTimeFilter = uiState.currentTimeFilter,
            onSortSelected = { sort ->
                viewModel.setSort(sort)
            },
            onTimeFilterSelected = { filter ->
                viewModel.setTimeFilter(filter)
            },
            onDismiss = { showSortSheet = false }
        )
    }
}
