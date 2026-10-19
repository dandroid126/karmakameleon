package com.karmakameleon.android.ui.subreddit

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.android.ui.components.FeedPostItem
import com.karmakameleon.android.ui.components.LoadMoreNearEnd
import com.karmakameleon.android.ui.components.PostFeedSettings
import com.karmakameleon.android.ui.components.PostListCallbacks
import com.karmakameleon.android.ui.components.PostListItem
import com.karmakameleon.android.ui.components.ScrollToTopAfterRefresh
import com.karmakameleon.android.ui.components.SortBottomSheet
import com.karmakameleon.android.ui.components.UniversalTopAppBar
import com.karmakameleon.android.ui.components.formatNumber
import com.karmakameleon.android.ui.components.loadingMoreItem
import com.karmakameleon.android.ui.components.rememberCompactPostSelection
import com.karmakameleon.android.ui.components.rememberPostFeedSettings
import com.karmakameleon.shared.data.repository.ReadPostsRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.domain.model.NsfwHistoryMode
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import com.karmakameleon.shared.ui.subreddit.SubredditUiState
import com.karmakameleon.shared.ui.subreddit.SubredditViewModel
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
    modifier: Modifier = Modifier,
    currentRoute: String? = null,
    onSubredditClick: (String) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    viewModel: SubredditViewModel = koinViewModel { parametersOf(subredditName) },
    navigationHandler: NavigationHandler = koinInject(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val feedSettings = rememberPostFeedSettings()
    var showSortSheet by remember { mutableStateOf(false) }
    // Posts here are all in this subreddit, so tapping their subreddit does nothing and blocking isn't offered.
    val callbacks =
        PostListCallbacks(
            onPostClick = onPostClick,
            onUserClick = onUserClick,
            onLinkClick = onLinkClick,
            onCrosspostClick = navigationHandler::handleLink,
            onVote = viewModel::vote,
            onSave = viewModel::save,
            onHide = viewModel::hide,
        )

    LoadMoreNearEnd(listState, uiState.posts.size, enabled = !uiState.isLoadingMore && uiState.hasMore) { viewModel.loadMorePosts() }
    ScrollToTopAfterRefresh(listState, uiState.isRefreshing)

    Scaffold(
        modifier = modifier,
        topBar = {
            SubredditTopBar(
                subredditName = subredditName,
                currentRoute = currentRoute,
                onBackClick = onBackClick,
                onSortClick = { showSortSheet = true },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.loadPosts(forceRefresh = true) },
            state = rememberPullToRefreshState(),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            SubredditPostList(
                uiState = uiState,
                listState = listState,
                feedSettings = feedSettings,
                callbacks = callbacks,
                onSubscribeClick = viewModel::toggleSubscribe,
            )
        }
    }

    if (showSortSheet) {
        SortBottomSheet(
            currentSort = uiState.currentSort,
            currentTimeFilter = uiState.currentTimeFilter,
            onSelectSort = viewModel::setSort,
            onSelectTimeFilter = viewModel::setTimeFilter,
            onDismiss = { showSortSheet = false },
        )
    }
}

@Composable
private fun SubredditTopBar(
    subredditName: String,
    currentRoute: String?,
    onBackClick: () -> Unit,
    onSortClick: () -> Unit,
) {
    UniversalTopAppBar(
        currentRoute = currentRoute,
        title = { Text("r/$subredditName") },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onSortClick) {
                Icon(Icons.AutoMirrored.Default.Sort, contentDescription = "Sort")
            }
        },
    )
}

// The subreddit's header, then its posts.
@Composable
private fun SubredditPostList(
    uiState: SubredditUiState,
    listState: LazyListState,
    feedSettings: PostFeedSettings,
    callbacks: PostListCallbacks,
    onSubscribeClick: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        uiState.subreddit?.let { subreddit ->
            item {
                SubredditHeader(
                    subreddit = subreddit,
                    onSubscribeClick = onSubscribeClick,
                    isLoggedIn = uiState.isLoggedIn,
                )
            }
        }

        if (uiState.isLoading && uiState.posts.isEmpty()) {
            item {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            items(
                items = uiState.posts,
                key = { it.id },
            ) { post ->
                FeedPostItem(post = post, settings = feedSettings, callbacks = callbacks, isLoggedIn = uiState.isLoggedIn)
            }
            if (uiState.isLoadingMore) loadingMoreItem()
        }
    }
}

@Composable
private fun SubredditHeader(
    subreddit: com.karmakameleon.shared.domain.model.Subreddit,
    onSubscribeClick: () -> Unit,
    isLoggedIn: Boolean,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (subreddit.iconUrl != null) {
                    AsyncImage(
                        model = subreddit.iconUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }

                SubredditNameAndStats(subreddit = subreddit, modifier = Modifier.weight(1f))

                if (isLoggedIn) {
                    Button(
                        onClick = onSubscribeClick,
                        colors =
                            if (subreddit.isSubscribed) {
                                ButtonDefaults.outlinedButtonColors()
                            } else {
                                ButtonDefaults.buttonColors()
                            },
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubredditNameAndStats(
    subreddit: com.karmakameleon.shared.domain.model.Subreddit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = subreddit.displayNamePrefixed,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${formatNumber(subreddit.subscribers.toInt())} members",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        subreddit.activeUserCount?.let { active ->
            Text(
                text = "${formatNumber(active)} online",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
