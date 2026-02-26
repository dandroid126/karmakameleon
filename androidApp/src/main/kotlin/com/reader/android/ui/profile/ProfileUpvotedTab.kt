package com.reader.android.ui.profile

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.reader.android.navigation.NavigationHandler
import com.reader.shared.data.repository.ReadPostsRepository
import com.reader.shared.domain.model.NsfwHistoryMode
import com.reader.shared.domain.model.NsfwPreviewMode
import com.reader.shared.domain.model.Post

@Composable
internal fun ProfileUpvotedTab(
    posts: List<Post>,
    isLoadingContent: Boolean,
    isLoadingMore: Boolean,
    isLoggedIn: Boolean,
    listState: LazyListState,
    readPostsRepository: ReadPostsRepository,
    nsfwHistoryMode: NsfwHistoryMode,
    nsfwPreviewMode: NsfwPreviewMode,
    spoilerPreviewsEnabled: Boolean,
    navigationHandler: NavigationHandler,
    onPostClick: (subreddit: String, postId: String) -> Unit,
    onSubredditClick: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    onVote: (Post, Int) -> Unit,
    onSave: (Post) -> Unit,
) {
    ProfilePostsTab(
        posts = posts,
        isLoadingContent = isLoadingContent,
        isLoadingMore = isLoadingMore,
        isLoggedIn = isLoggedIn,
        listState = listState,
        readPostsRepository = readPostsRepository,
        nsfwHistoryMode = nsfwHistoryMode,
        nsfwPreviewMode = nsfwPreviewMode,
        spoilerPreviewsEnabled = spoilerPreviewsEnabled,
        navigationHandler = navigationHandler,
        onPostClick = onPostClick,
        onSubredditClick = onSubredditClick,
        onLinkClick = onLinkClick,
        onVote = onVote,
        onSave = onSave,
        emptyText = "No upvoted posts",
    )
}
