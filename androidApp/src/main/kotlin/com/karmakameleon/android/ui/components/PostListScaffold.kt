package com.karmakameleon.android.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.karmakameleon.shared.ui.PostList
import com.karmakameleon.shared.ui.PostListState

/**
 * The screen around a [PostList]: [topBar] (given the action that opens the sort sheet), pull to refresh, loading
 * the next page near the end of [listState], scrolling back to the top after a refresh, and the sort sheet.
 * [content] shows the posts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostListScaffold(
    postList: PostList,
    listState: LazyListState,
    topBar: @Composable (onSortClick: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(PostListState) -> Unit,
) {
    val state by postList.state.collectAsState()
    var showSortSheet by remember { mutableStateOf(false) }

    LoadMoreNearEnd(listState, state.posts.size, enabled = state.canLoadMore) { postList.loadMore() }
    ScrollToTopAfterRefresh(listState, state.isRefreshing)

    Scaffold(
        modifier = modifier,
        topBar = { topBar { showSortSheet = true } },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { postList.load(forceRefresh = true) },
            state = rememberPullToRefreshState(),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            content(state)
        }
    }

    if (showSortSheet) {
        SortBottomSheet(
            currentSort = state.currentSort,
            currentTimeFilter = state.currentTimeFilter,
            onSelectSort = postList::setSort,
            onSelectTimeFilter = postList::setTimeFilter,
            onDismiss = { showSortSheet = false },
        )
    }
}
