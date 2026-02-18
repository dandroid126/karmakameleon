package com.reader.android.ui.subreddit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.reader.android.ui.components.PostCard
import com.reader.android.ui.components.SortBottomSheet
import com.reader.android.ui.components.formatNumber
import com.reader.shared.data.repository.ReadPostsRepository
import com.reader.shared.ui.subreddit.SubredditViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubredditScreen(
    subredditName: String,
    onBackClick: () -> Unit,
    onPostClick: (subreddit: String, postId: String) -> Unit,
    onUserClick: (String) -> Unit,
    onSubredditClick: (String) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    viewModel: SubredditViewModel = koinViewModel { parametersOf(subredditName) }
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val readPostsRepository: ReadPostsRepository = koinInject()
    val readPostIds by readPostsRepository.readPostIds.collectAsState()
    var showSortSheet by remember { mutableStateOf(false) }

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
                title = { Text("r/$subredditName") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(Icons.AutoMirrored.Default.Sort, contentDescription = "Sort")
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
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                // Subreddit header
                uiState.subreddit?.let { subreddit ->
                    item {
                        SubredditHeader(
                            subreddit = subreddit,
                            onSubscribeClick = viewModel::toggleSubscribe,
                            isLoggedIn = uiState.isLoggedIn
                        )
                    }
                }

                if (uiState.isLoading && uiState.posts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    items(
                        items = uiState.posts,
                        key = { it.id }
                    ) { post ->
                        PostCard(
                            post = post,
                            onClick = {
                                readPostsRepository.markAsRead(post.id)
                                onPostClick(post.subreddit, post.id)
                            },
                            onSubredditClick = {},
                            onUserClick = { onUserClick(post.author) },
                            onUpvote = { viewModel.vote(post, if (post.likes == true) 0 else 1) },
                            onDownvote = { viewModel.vote(post, if (post.likes == false) 0 else -1) },
                            onSave = { viewModel.save(post) },
                            onHide = { viewModel.hide(post) },
                            isLoggedIn = uiState.isLoggedIn,
                            onLinkClick = onLinkClick,
                            isRead = readPostIds.contains(post.id)
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
            onSortSelected = viewModel::setSort,
            onTimeFilterSelected = viewModel::setTimeFilter,
            onDismiss = { showSortSheet = false }
        )
    }
}

@Composable
private fun SubredditHeader(
    subreddit: com.reader.shared.domain.model.Subreddit,
    onSubscribeClick: () -> Unit,
    isLoggedIn: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (subreddit.iconUrl != null) {
                    AsyncImage(
                        model = subreddit.iconUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subreddit.displayNamePrefixed,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${formatNumber(subreddit.subscribers.toInt())} members",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    subreddit.activeUserCount?.let { active ->
                        Text(
                            text = "${formatNumber(active)} online",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (isLoggedIn) {
                    Button(
                        onClick = onSubscribeClick,
                        colors = if (subreddit.isSubscribed) {
                            ButtonDefaults.outlinedButtonColors()
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Text(if (subreddit.isSubscribed) "Joined" else "Join")
                    }
                }
            }

            subreddit.publicDescription?.let { description ->
                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
