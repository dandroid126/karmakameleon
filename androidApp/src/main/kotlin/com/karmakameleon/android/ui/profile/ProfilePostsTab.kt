package com.karmakameleon.android.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.karmakameleon.android.ui.components.FeedPostItem
import com.karmakameleon.android.ui.components.PostFeedSettings
import com.karmakameleon.android.ui.components.PostListCallbacks
import com.karmakameleon.android.ui.components.loadingMoreItem
import com.karmakameleon.android.ui.components.rememberCompactPostSelection
import com.karmakameleon.shared.domain.model.Post

@Composable
internal fun ProfilePostsTab(
    posts: List<Post>,
    isLoadingContent: Boolean,
    isLoadingMore: Boolean,
    isLoggedIn: Boolean,
    listState: LazyListState,
    feedSettings: PostFeedSettings,
    callbacks: PostListCallbacks,
    emptyText: String,
) {
    // Each list remembers which compact post is expanded.
    val settings = feedSettings.copy(compactSelection = rememberCompactPostSelection())
    ProfileListContent(isLoadingContent = isLoadingContent, isEmpty = posts.isEmpty(), emptyText = emptyText) {
        LazyColumn(state = listState) {
            items(posts, key = { it.id }) { post ->
                FeedPostItem(post = post, settings = settings, callbacks = callbacks, isLoggedIn = isLoggedIn)
            }
            if (isLoadingMore) loadingMoreItem()
        }
    }
}

/** A spinner while a list's first page loads, a message when it's empty, otherwise the list. */
@Composable
internal fun ProfileListContent(
    isLoadingContent: Boolean,
    isEmpty: Boolean,
    emptyText: String,
    list: @Composable () -> Unit,
) {
    when {
        isLoadingContent && isEmpty -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        isEmpty -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyText)
            }
        }

        else -> {
            list()
        }
    }
}
