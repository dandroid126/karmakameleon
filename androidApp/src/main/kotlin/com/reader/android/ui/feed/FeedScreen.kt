package com.reader.android.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import com.reader.android.ui.components.SortBottomSheet
import com.reader.shared.data.repository.ReadPostsRepository
import com.reader.shared.data.repository.SettingsRepository
import com.reader.shared.domain.model.NsfwHistoryMode
import com.reader.shared.domain.model.NsfwPreviewMode
import com.reader.shared.ui.feed.FeedType
import com.reader.shared.ui.feed.FeedViewModel
import com.reader.android.ui.components.UniversalTopAppBar
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    currentRoute: String? = null,
    onPostClick: (subreddit: String, postId: String) -> Unit,
    onSubredditClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onLinkClick: (String) -> Unit = {},
    viewModel: FeedViewModel = koinViewModel(),
    navigationHandler: NavigationHandler = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var showSortSheet by remember { mutableStateOf(false) }
    var showFeedTypeMenu by remember { mutableStateOf(false) }
    val readPostsRepository: ReadPostsRepository = koinInject()
    val settingsRepository: SettingsRepository = koinInject()
    val readPostIds by readPostsRepository.readPostIds.collectAsState()
    val nsfwEnabled by settingsRepository.nsfwEnabled.collectAsState()
    val nsfwHistoryMode by settingsRepository.nsfwHistoryMode.collectAsState()
    val nsfwPreviewMode by settingsRepository.nsfwPreviewMode.collectAsState()
    val effectiveNsfwPreviewMode = if (nsfwEnabled) nsfwPreviewMode else NsfwPreviewMode.DO_NOT_PREFETCH
    val effectiveNsfwHistoryMode = if (nsfwEnabled) nsfwHistoryMode else NsfwHistoryMode.DONT_SAVE_ANY_NSFW
    val spoilerPreviewsEnabled by settingsRepository.spoilerPreviewsEnabled.collectAsState()
    
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

    val pullToRefreshState = rememberPullToRefreshState()

    var previousFeedType by remember { mutableStateOf(uiState.currentFeedType) }
    LaunchedEffect(uiState.currentFeedType) {
        if (uiState.currentFeedType != previousFeedType) {
            listState.scrollToItem(0)
            previousFeedType = uiState.currentFeedType
        }
    }

    Scaffold(
        topBar = {
            UniversalTopAppBar(
                currentRoute = currentRoute,
                title = {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showFeedTypeMenu = true }
                        ) {
                            Text(uiState.currentFeedType.displayName)
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Change feed"
                            )
                        }
                        DropdownMenu(
                            expanded = showFeedTypeMenu,
                            onDismissRequest = { showFeedTypeMenu = false }
                        ) {
                            FeedType.entries.forEach { feedType ->
                                DropdownMenuItem(
                                    text = { Text(feedType.displayName) },
                                    onClick = {
                                        viewModel.setFeedType(feedType)
                                        showFeedTypeMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(Icons.AutoMirrored.Default.Sort, contentDescription = "Sort")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.loadPosts(forceRefresh = true) },
            state = pullToRefreshState,
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
                            onClick = {
                                readPostsRepository.markAsRead(post, effectiveNsfwHistoryMode)
                                onPostClick(post.subreddit, post.id)
                            },
                            onSubredditClick = { onSubredditClick(post.subreddit) },
                            onUserClick = { onUserClick(post.author) },
                            onUpvote = { viewModel.vote(post, if (post.likes == true) 0 else 1) },
                            onDownvote = { viewModel.vote(post, if (post.likes == false) 0 else -1) },
                            onSave = { viewModel.save(post) },
                            onHide = { viewModel.hide(post) },
                            onBlockSubreddit = { viewModel.blockSubreddit(post.subreddit) },
                            isLoggedIn = uiState.isLoggedIn,
                            onLinkClick = onLinkClick,
                            onCrosspostClick = {
                                post.crosspostParentPermalink?.let { navigationHandler.handleLink(it) }
                            },
                            isRead = readPostsRepository.isRead(post, effectiveNsfwHistoryMode),
                            nsfwPreviewMode = effectiveNsfwPreviewMode,
                            spoilerPreviewsEnabled = spoilerPreviewsEnabled
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
