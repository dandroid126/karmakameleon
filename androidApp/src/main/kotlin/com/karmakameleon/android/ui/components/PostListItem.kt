package com.karmakameleon.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import com.karmakameleon.shared.domain.model.Post

/**
 * Tracks which post in a compact feed is highlighted and whether its control bar is open.
 * Tapping a post highlights and expands it; tapping it again collapses it but keeps the highlight.
 */
@Stable
class CompactPostSelection(
    selectedPostId: String? = null,
    expanded: Boolean = false,
) {
    var selectedPostId by mutableStateOf(selectedPostId)
        private set
    var expanded by mutableStateOf(expanded)
        private set

    fun isHighlighted(postId: String) = selectedPostId == postId

    fun isExpanded(postId: String) = selectedPostId == postId && expanded

    fun toggle(postId: String) {
        if (selectedPostId == postId) {
            expanded = !expanded
        } else {
            selectedPostId = postId
            expanded = true
        }
    }

    companion object {
        val Saver =
            listSaver<CompactPostSelection, Any?>(
                save = { listOf(it.selectedPostId, it.expanded) },
                restore = { CompactPostSelection(it[0] as String?, it[1] as Boolean) },
            )
    }
}

@Composable
fun rememberCompactPostSelection(): CompactPostSelection = rememberSaveable(saver = CompactPostSelection.Saver) { CompactPostSelection() }

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
