package com.karmakameleon.android.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.android.ui.components.PostCard
import com.karmakameleon.shared.data.repository.ReadPostsRepository
import com.karmakameleon.shared.domain.model.NsfwHistoryMode
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import com.karmakameleon.shared.domain.model.Post

@Composable
internal fun ProfilePostsTab(
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
    emptyText: String = "No posts",
) {
    if (isLoadingContent && posts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (posts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText)
        }
    } else {
        LazyColumn(state = listState) {
            items(posts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    onClick = {
                        readPostsRepository.markAsRead(post, nsfwHistoryMode)
                        onPostClick(post.subreddit, post.id)
                    },
                    onSubredditClick = { onSubredditClick(post.subreddit) },
                    onUserClick = {},
                    onUpvote = { onVote(post, if (post.likes == true) 0 else 1) },
                    onDownvote = { onVote(post, if (post.likes == false) 0 else -1) },
                    onSave = { onSave(post) },
                    onHide = {},
                    isLoggedIn = isLoggedIn,
                    onLinkClick = onLinkClick,
                    onCrosspostClick = {
                        post.crosspostParentPermalink?.let { navigationHandler.handleLink(it) }
                    },
                    isRead = readPostsRepository.isRead(post, nsfwHistoryMode),
                    nsfwPreviewMode = nsfwPreviewMode,
                    spoilerPreviewsEnabled = spoilerPreviewsEnabled
                )
            }
            if (isLoadingMore) {
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
