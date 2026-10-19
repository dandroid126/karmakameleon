package com.karmakameleon.android.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyListState
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
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.android.ui.components.FeedPostItem
import com.karmakameleon.android.ui.components.LoadMoreNearEnd
import com.karmakameleon.android.ui.components.PostFeedSettings
import com.karmakameleon.android.ui.components.PostListCallbacks
import com.karmakameleon.android.ui.components.PostListItem
import com.karmakameleon.android.ui.components.ScrollToTopAfterRefresh
import com.karmakameleon.android.ui.components.ScrollToTopOnChange
import com.karmakameleon.android.ui.components.SortBottomSheet
import com.karmakameleon.android.ui.components.UniversalTopAppBar
import com.karmakameleon.android.ui.components.loadingMoreItem
import com.karmakameleon.android.ui.components.rememberCompactPostSelection
import com.karmakameleon.android.ui.components.rememberPostFeedSettings
import com.karmakameleon.shared.data.repository.ReadPostsRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.domain.model.NsfwHistoryMode
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import com.karmakameleon.shared.ui.PostListState
import com.karmakameleon.shared.ui.feed.FeedType
import com.karmakameleon.shared.ui.feed.FeedViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onPostClick: (subreddit: String, postId: String) -> Unit,
    onSubredditClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
    viewModel: FeedViewModel = koinViewModel(),
    navigationHandler: NavigationHandler = koinInject(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val postListState by viewModel.postList.state.collectAsState()
    val listState = rememberLazyListState()
    var showSortSheet by remember { mutableStateOf(false) }
    val feedSettings = rememberPostFeedSettings()

    LoadMoreNearEnd(listState, postListState.posts.size, enabled = postListState.canLoadMore) { viewModel.postList.loadMore() }
    ScrollToTopAfterRefresh(listState, postListState.isRefreshing)

    ScrollToTopOnChange(listState, uiState.currentFeedType)

    Scaffold(
        modifier = modifier,
        topBar = {
            FeedTopBar(
                currentFeedType = uiState.currentFeedType,
                onSelectFeedType = viewModel::setFeedType,
                onSortClick = { showSortSheet = true },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = postListState.isRefreshing,
            onRefresh = { viewModel.postList.load(forceRefresh = true) },
            state = rememberPullToRefreshState(),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            FeedContent(
                postList = postListState,
                isLoggedIn = uiState.isLoggedIn,
                listState = listState,
                feedSettings = feedSettings,
                callbacks =
                    PostListCallbacks(
                        onPostClick = onPostClick,
                        onSubredditClick = onSubredditClick,
                        onUserClick = onUserClick,
                        onLinkClick = onLinkClick,
                        onCrosspostClick = navigationHandler::handleLink,
                        onVote = viewModel.postList::vote,
                        onSave = viewModel.postList::save,
                        onHide = viewModel.postList::hide,
                        onBlockSubreddit = viewModel::blockSubreddit,
                    ),
                onRetry = { viewModel.postList.load(forceRefresh = true) },
            )
        }
    }

    if (showSortSheet) {
        SortBottomSheet(
            currentSort = postListState.currentSort,
            currentTimeFilter = postListState.currentTimeFilter,
            onSelectSort = viewModel.postList::setSort,
            onSelectTimeFilter = viewModel.postList::setTimeFilter,
            onDismiss = { showSortSheet = false },
        )
    }
}

@Composable
private fun FeedTopBar(
    currentFeedType: FeedType,
    onSelectFeedType: (FeedType) -> Unit,
    onSortClick: () -> Unit,
) {
    UniversalTopAppBar(
        title = { FeedTypeMenu(currentFeedType = currentFeedType, onSelectFeedType = onSelectFeedType) },
        actions = {
            IconButton(onClick = onSortClick) {
                Icon(Icons.AutoMirrored.Default.Sort, contentDescription = "Sort")
            }
        },
    )
}

// The feed's name, opening a menu of the other feeds.
@Composable
private fun FeedTypeMenu(
    currentFeedType: FeedType,
    onSelectFeedType: (FeedType) -> Unit,
) {
    var showFeedTypeMenu by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { showFeedTypeMenu = true },
        ) {
            Text(currentFeedType.displayName)
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "Change feed",
            )
        }
        DropdownMenu(
            expanded = showFeedTypeMenu,
            onDismissRequest = { showFeedTypeMenu = false },
        ) {
            FeedType.entries.forEach { feedType ->
                DropdownMenuItem(
                    text = { Text(feedType.displayName) },
                    onClick = {
                        onSelectFeedType(feedType)
                        showFeedTypeMenu = false
                    },
                )
            }
        }
    }
}

// A spinner or an error while there are no posts yet, otherwise the posts.
@Composable
private fun BoxScope.FeedContent(
    postList: PostListState,
    isLoggedIn: Boolean,
    listState: LazyListState,
    feedSettings: PostFeedSettings,
    callbacks: PostListCallbacks,
    onRetry: () -> Unit,
) {
    when {
        postList.isLoading && postList.posts.isEmpty() -> {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
        }

        postList.error != null && postList.posts.isEmpty() -> {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = postList.error ?: "Unknown error",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }

        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(
                    items = postList.posts,
                    key = { it.id },
                ) { post ->
                    FeedPostItem(post = post, settings = feedSettings, callbacks = callbacks, isLoggedIn = isLoggedIn)
                }
                if (postList.isLoadingMore) loadingMoreItem()
            }
        }
    }
}
