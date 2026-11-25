package com.karmakameleon.android.ui.profile

import android.content.ClipData
import android.widget.Toast
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karmakameleon.android.ui.components.CommentItem
import com.karmakameleon.android.ui.components.CommentLinks
import com.karmakameleon.android.ui.components.CommentScreenActions
import com.karmakameleon.android.ui.components.KeepPlaceOnRemoval
import com.karmakameleon.android.ui.components.commentActions
import com.karmakameleon.android.ui.components.loadingMoreItem
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.ui.comment.CommentViewModel
import kotlinx.coroutines.launch

/** What the profile's comment lists do with a comment, besides what [commentActions] gives every comment. */
internal data class ProfileCommentCallbacks(
    val comments: CommentViewModel,
    val onDelete: (commentId: String) -> Unit,
    val onOpen: (subreddit: String, postId: String, commentId: String) -> Unit,
    val onImageClick: (url: String) -> Unit,
)

/** State the profile's comment lists share: who's logged in and which comment is selected. */
internal class ProfileCommentListState(
    val isLoggedIn: Boolean,
    val loggedInUsername: String?,
    val selectedCommentId: String?,
    val onSelectComment: (String?) -> Unit,
)

@Composable
internal fun ProfileCommentsTab(
    comments: List<Comment>,
    isLoadingContent: Boolean,
    isLoadingMore: Boolean,
    listState: LazyListState,
    state: ProfileCommentListState,
    callbacks: ProfileCommentCallbacks,
    emptyText: String = "No comments",
) {
    ProfileListContent(isLoadingContent = isLoadingContent, isEmpty = comments.isEmpty(), emptyText = emptyText) {
        KeepPlaceOnRemoval(listState, comments) { it.id }
        LazyColumn(state = listState) {
            items(comments, key = { it.id }) { comment ->
                ProfileComment(comment = comment, state = state, callbacks = callbacks)
            }
            if (isLoadingMore) loadingMoreItem()
        }
    }
}

// A comment shown outside its thread, with its subreddit and without thread navigation.
@Composable
private fun ProfileComment(
    comment: Comment,
    state: ProfileCommentListState,
    callbacks: ProfileCommentCallbacks,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val isSelected = state.selectedCommentId == comment.id
    CommentItem(
        comment = comment,
        isSelected = isSelected,
        isHidden = false,
        actions =
            commentActions(
                comment = comment,
                comments = callbacks.comments,
                context = context,
                screen =
                    CommentScreenActions(
                        select = { state.onSelectComment(if (isSelected) null else it.id) },
                        deselect = { state.onSelectComment(null) },
                        confirmDelete = { callbacks.onDelete(it.id) },
                        links =
                            CommentLinks(
                                copyLink = { link ->
                                    coroutineScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", link))) }
                                },
                                openThread = { linked, commentId ->
                                    callbacks.onOpen(linked.subreddit, linked.linkId.removePrefix("t3_"), commentId)
                                },
                                openImage = callbacks.onImageClick,
                            ),
                        // Touching a comment marks what a quote goes into, which only the thread on the post screen offers.
                        onTouchStart = {},
                        thread = null,
                    ),
            ),
        isLoggedIn = state.isLoggedIn,
        loggedInUsername = state.loggedInUsername,
        showTopControls = false,
        showSubreddit = true,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
