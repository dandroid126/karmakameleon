package com.reader.android.ui.profile

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reader.android.navigation.NavigationHandler
import com.reader.android.ui.components.CommentItem
import com.reader.android.ui.components.PostCard
import com.reader.shared.data.repository.ReadPostsRepository
import com.reader.shared.domain.model.Comment
import com.reader.shared.domain.model.NsfwHistoryMode
import com.reader.shared.domain.model.NsfwPreviewMode
import com.reader.shared.domain.model.Post
import com.reader.shared.ui.profile.SavedContentType
import kotlinx.coroutines.launch

@Composable
internal fun ProfileSavedTab(
    savedContentType: SavedContentType,
    savedPosts: List<Post>,
    savedComments: List<Comment>,
    isLoadingContent: Boolean,
    isLoadingMorePosts: Boolean,
    isLoadingMoreComments: Boolean,
    isLoggedIn: Boolean,
    loggedInUsername: String?,
    savedPostsListState: LazyListState,
    savedCommentsListState: LazyListState,
    selectedCommentId: String?,
    readPostsRepository: ReadPostsRepository,
    nsfwHistoryMode: NsfwHistoryMode,
    nsfwPreviewMode: NsfwPreviewMode,
    spoilerPreviewsEnabled: Boolean,
    navigationHandler: NavigationHandler,
    onSetSavedContentType: (SavedContentType) -> Unit,
    onPostClick: (subreddit: String, postId: String) -> Unit,
    onSubredditClick: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    onVote: (Post, Int) -> Unit,
    onSavePost: (Post) -> Unit,
    onSelectComment: (String?) -> Unit,
    onCommentUpdated: (Comment) -> Unit,
    onReply: (String) -> Unit,
    onEdit: (Comment) -> Unit,
    onDelete: (String) -> Unit,
    onSaveComment: (Comment) -> Unit,
    onCommentClick: (subreddit: String, postId: String, commentId: String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var showSavedTypeMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showSavedTypeMenu = true }
            ) {
                Text(
                    savedContentType.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Change saved type")
            }
            DropdownMenu(
                expanded = showSavedTypeMenu,
                onDismissRequest = { showSavedTypeMenu = false }
            ) {
                SavedContentType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.displayName) },
                        onClick = {
                            onSetSavedContentType(type)
                            showSavedTypeMenu = false
                        }
                    )
                }
            }
        }

        when (savedContentType) {
            SavedContentType.POSTS -> {
                if (isLoadingContent && savedPosts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (savedPosts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No saved posts")
                    }
                } else {
                    LazyColumn(state = savedPostsListState) {
                        items(savedPosts, key = { it.id }) { post ->
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
                                onSave = { onSavePost(post) },
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
                        if (isLoadingMorePosts) {
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
            SavedContentType.COMMENTS -> {
                if (isLoadingContent && savedComments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (savedComments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No saved comments")
                    }
                } else {
                    LazyColumn(state = savedCommentsListState) {
                        items(savedComments, key = { it.id }) { comment ->
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
                                    onSaveComment(comment)
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
                        if (isLoadingMoreComments) {
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
        }
    }
}
