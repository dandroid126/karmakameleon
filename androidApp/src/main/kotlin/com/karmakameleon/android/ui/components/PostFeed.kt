package com.karmakameleon.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karmakameleon.shared.data.repository.ReadPostsRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.domain.model.NsfwHistoryMode
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import com.karmakameleon.shared.domain.model.Post
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.compose.koinInject

// Shared by the screens that show a list of posts.

/** The settings that decide how posts in a list look, with NSFW options already turned off when NSFW is. */
data class PostFeedSettings(
    val readPostsRepository: ReadPostsRepository,
    // Carried so the list recomposes when a post is marked read.
    val readPostIds: Set<String>,
    val nsfwHistoryMode: NsfwHistoryMode,
    val nsfwPreviewMode: NsfwPreviewMode,
    val spoilerPreviewsEnabled: Boolean,
    val compactFeed: Boolean,
    val compactSelection: CompactPostSelection,
)

@Composable
fun rememberPostFeedSettings(): PostFeedSettings {
    val readPostsRepository: ReadPostsRepository = koinInject()
    val settingsRepository: SettingsRepository = koinInject()
    val readPostIds by readPostsRepository.readPostIds.collectAsState()
    val nsfwEnabled by settingsRepository.nsfwEnabled.collectAsState()
    val nsfwHistoryMode by settingsRepository.nsfwHistoryMode.collectAsState()
    val nsfwPreviewMode by settingsRepository.nsfwPreviewMode.collectAsState()
    val spoilerPreviewsEnabled by settingsRepository.spoilerPreviewsEnabled.collectAsState()
    val compactFeedEnabled by settingsRepository.compactFeedEnabled.collectAsState()
    val compactSelection = rememberCompactPostSelection()
    return PostFeedSettings(
        readPostsRepository = readPostsRepository,
        readPostIds = readPostIds,
        nsfwHistoryMode = if (nsfwEnabled) nsfwHistoryMode else NsfwHistoryMode.DONT_SAVE_ANY_NSFW,
        nsfwPreviewMode = if (nsfwEnabled) nsfwPreviewMode else NsfwPreviewMode.DO_NOT_PREFETCH,
        spoilerPreviewsEnabled = spoilerPreviewsEnabled,
        compactFeed = compactFeedEnabled,
        compactSelection = compactSelection,
    )
}

/** What a screen does with the posts in its list. Leave out what the screen doesn't support. */
class PostListCallbacks(
    val onPostClick: (subreddit: String, postId: String) -> Unit,
    val onSubredditClick: (String) -> Unit = {},
    val onUserClick: (String) -> Unit = {},
    val onLinkClick: (String) -> Unit = {},
    val onCrosspostClick: (permalink: String) -> Unit = {},
    val onVote: (post: Post, direction: Int) -> Unit = { _, _ -> },
    val onSave: (Post) -> Unit = {},
    val onHide: (Post) -> Unit = {},
    val onBlockSubreddit: (String) -> Unit = {},
)

/** A post in a list, marked read when it's opened. Voting again in the same direction removes the vote. */
@Composable
fun FeedPostItem(
    post: Post,
    settings: PostFeedSettings,
    callbacks: PostListCallbacks,
    isLoggedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    PostListItem(
        post = post,
        compact = settings.compactFeed,
        compactSelection = settings.compactSelection,
        actions =
            PostActions(
                onClick = {
                    settings.readPostsRepository.markAsRead(post, settings.nsfwHistoryMode)
                    callbacks.onPostClick(post.subreddit, post.id)
                },
                onSubredditClick = { callbacks.onSubredditClick(post.subreddit) },
                onUserClick = { callbacks.onUserClick(post.author) },
                onUpvote = { callbacks.onVote(post, if (post.likes == true) 0 else 1) },
                onDownvote = { callbacks.onVote(post, if (post.likes == false) 0 else -1) },
                onSave = { callbacks.onSave(post) },
                onHide = { callbacks.onHide(post) },
                onBlockSubreddit = { callbacks.onBlockSubreddit(post.subreddit) },
                onLinkClick = callbacks.onLinkClick,
                onCrosspostClick = { post.crosspostParentPermalink?.let { callbacks.onCrosspostClick(it) } },
            ),
        isLoggedIn = isLoggedIn,
        isRead = settings.readPostsRepository.isRead(post, settings.nsfwHistoryMode),
        nsfwPreviewMode = settings.nsfwPreviewMode,
        spoilerPreviewsEnabled = settings.spoilerPreviewsEnabled,
        modifier = modifier,
    )
}

/** Calls onLoadMore when the list is scrolled to within a few items of its end, while enabled. */
@Composable
fun LoadMoreNearEnd(
    listState: LazyListState,
    itemCount: Int,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val currentItemCount by rememberUpdatedState(itemCount)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisible != null && lastVisible.index >= currentItemCount - 5
        }.distinctUntilChanged()
            .filter { nearEnd -> nearEnd }
            .collect { if (currentEnabled) currentOnLoadMore() }
    }
}

/** Scrolls the list back to the top when key changes, but not when it's first shown. */
@Composable
fun ScrollToTopOnChange(
    listState: LazyListState,
    key: Any,
) {
    var previousKey by remember { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (key != previousKey) {
            listState.scrollToItem(0)
            previousKey = key
        }
    }
}

/** Scrolls the list back to the top when a refresh finishes. */
@Composable
fun ScrollToTopAfterRefresh(
    listState: LazyListState,
    isRefreshing: Boolean,
) {
    var wasRefreshing by remember { mutableStateOf(isRefreshing) }
    LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing) {
            listState.animateScrollToItem(0)
        }
        wasRefreshing = isRefreshing
    }
}

/** A spinner at the end of a list while the next page loads. */
fun LazyListScope.loadingMoreItem() {
    item {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
    }
}
