package com.karmakameleon.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import com.karmakameleon.shared.domain.model.Post

/** Shows a post as a full [PostCard] or a [CompactPostCard] depending on the compact feed setting. */
@Composable
fun PostListItem(
    post: Post,
    compact: Boolean,
    compactSelection: CompactPostSelection,
    actions: PostActions,
    isLoggedIn: Boolean,
    modifier: Modifier = Modifier,
    isRead: Boolean = false,
    nsfwPreviewMode: NsfwPreviewMode = NsfwPreviewMode.DO_NOT_PREFETCH,
    spoilerPreviewsEnabled: Boolean = false,
) {
    if (compact) {
        CompactPostCard(
            post = post,
            isExpanded = compactSelection.isExpanded(post.id),
            isHighlighted = compactSelection.isHighlighted(post.id),
            onToggleExpansion = { compactSelection.toggle(post.id) },
            actions = actions,
            isLoggedIn = isLoggedIn,
            modifier = modifier,
            isRead = isRead,
            nsfwPreviewMode = nsfwPreviewMode,
            spoilerPreviewsEnabled = spoilerPreviewsEnabled,
        )
    } else {
        PostCard(
            post = post,
            actions = actions,
            isLoggedIn = isLoggedIn,
            modifier = modifier,
            isRead = isRead,
            nsfwPreviewMode = nsfwPreviewMode,
            spoilerPreviewsEnabled = spoilerPreviewsEnabled,
        )
    }
}
