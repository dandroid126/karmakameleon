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
import com.karmakameleon.shared.domain.model.Comment
import kotlinx.coroutines.launch

@Composable
internal fun ProfileCommentsTab(
    comments: List<Comment>,
    isLoadingContent: Boolean,
    isLoadingMore: Boolean,
    isLoggedIn: Boolean,
    loggedInUsername: String?,
    listState: LazyListState,
    selectedCommentId: String?,
    onSelectComment: (String?) -> Unit,
    onCommentUpdated: (Comment) -> Unit,
    onReply: (String) -> Unit,
    onEdit: (Comment) -> Unit,
    onDelete: (String) -> Unit,
    onSave: (Comment) -> Unit,
    onCommentClick: (subreddit: String, postId: String, commentId: String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    if (isLoadingContent && comments.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (comments.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No comments")
        }
    } else {
        LazyColumn(state = listState) {
            items(comments, key = { it.id }) { comment ->
                CommentItem(
                    comment = comment,
                    isSelected = selectedCommentId == comment.id,
                    isHidden = false,
                    onSelect = { onSelectComment(if (selectedCommentId == comment.id) null else comment.id) },
                    onDone = { onSelectComment(null) },
                    onHide = {},
                    onPrev = {},
                    onNext = {},
                    onRoot = {},
                    onParent = {},
                    onCommentUpdated = onCommentUpdated,
                    onShare = {
                        coroutineScope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", "https://reddit.com${comment.permalink}")))
                        }
                    },
                    onReply = { onReply(comment.name) },
                    onEdit = { onEdit(comment) },
                    onDelete = { onDelete(comment.id) },
                    onSave = {
                        onSave(comment)
                        Toast.makeText(
                            context,
                            if (comment.isSaved) "Unsaved comment" else "Saved comment",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    isLoggedIn = isLoggedIn,
                    loggedInUsername = loggedInUsername,
                    showTopControls = false,
                    showSubreddit = true,
                    onGoToCommentNav = { commentId ->
                        val postId = comment.linkId.removePrefix("t3_")
                        onCommentClick(comment.subreddit, postId, commentId)
                    },
                    modifier = Modifier.padding(vertical = 4.dp)
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
