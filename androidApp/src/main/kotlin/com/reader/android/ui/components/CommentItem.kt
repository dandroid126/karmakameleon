package com.reader.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.reader.android.navigation.NavigationHandler
import com.reader.shared.data.repository.PostRepository
import com.reader.shared.domain.model.Comment
import com.reader.shared.domain.model.VoteState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.core.graphics.toColorInt

@Composable
fun CommentItem(
    comment: Comment,
    isSelected: Boolean,
    isHidden: Boolean,
    onSelect: () -> Unit,
    onDone: () -> Unit,
    onHide: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRoot: () -> Unit,
    onParent: () -> Unit,
    onCommentUpdated: (Comment) -> Unit = {},
    onShare: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit = {},
    isLoggedIn: Boolean,
    loggedInUsername: String? = null,
    selectionVersion: Int = 0,
    onTouchStart: () -> Unit = {},
    renderInlineImages: Boolean = true,
    onInlineImageClick: (String) -> Unit = {},
    isSingleThreadMode: Boolean = false,
    onGoToCommentNav: (commentId: String) -> Unit = {},
    showTopControls: Boolean = true,
    showSubreddit: Boolean = false,
    modifier: Modifier = Modifier,
    postRepository: PostRepository = koinInject()
) {
    val scope = rememberCoroutineScope()
    val navigationHandler = koinInject<NavigationHandler>()
    
    fun vote(direction: Int) {
        scope.launch {
            val result = postRepository.voteComment(comment, direction)
            result.onSuccess { updatedComment ->
                onCommentUpdated(updatedComment)
            }
        }
    }
    
    val depthColors = listOf(
        Color(0xFFFF4500),
        Color(0xFF0079D3),
        Color(0xFF46A508),
        Color(0xFFFFD635),
        Color(0xFF7193FF),
        Color(0xFFFF66AC)
    )
    val depthColor = depthColors[comment.depth % depthColors.size]
    val hasParentComment = comment.parentId.startsWith("t1_")
    val showParentControls = if (isSingleThreadMode) hasParentComment else comment.depth > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (comment.depth * 12).dp)
            .then(
                if (isSelected) Modifier.background(Color(0xFF0079D3).copy(alpha = 0.08f))
                else Modifier
            )
            .clickable { onSelect() }
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (comment.depth > 0) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .background(depthColor.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 8.dp)) {
                if (isSelected && showTopControls) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onDone, modifier = Modifier.height(32.dp)) {
                                Text("Done", style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(onClick = onHide, modifier = Modifier.height(32.dp)) {
                                Text("Hide", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (showParentControls) {
                                TextButton(onClick = onRoot, modifier = Modifier.height(32.dp)) {
                                    Text("Root", style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(onClick = onParent, modifier = Modifier.height(32.dp)) {
                                    Text("Parent", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                TextButton(onClick = onPrev, modifier = Modifier.height(32.dp)) {
                                    Text("Prev", style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(onClick = onNext, modifier = Modifier.height(32.dp)) {
                                    Text("Next", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (showSubreddit) {
                    Text(
                        text = "r/${comment.subreddit}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { navigationHandler.onSubredditClick(comment.subreddit) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.author,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            comment.isSubmitter -> Color(0xFF0079D3)
                            comment.distinguished == "moderator" -> Color(0xFF46A508)
                            comment.distinguished == "admin" -> Color(0xFFFF4500)
                            loggedInUsername != null && comment.author == loggedInUsername -> Color(0xFFFF0000)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.clickable { navigationHandler.onUserClick(comment.author) }
                    )
                    if (comment.isSubmitter) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("OP", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0079D3))
                    }
                    val flairText = comment.authorFlairText
                    val flairColor = comment.authorFlairBackgroundColor?.let {
                        try { Color(it.toColorInt()) } catch (_: Exception) { MaterialTheme.colorScheme.secondary }
                    } ?: MaterialTheme.colorScheme.secondary
                    if (comment.authorFlairRichtext.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        RichFlairChip(
                            richtext = comment.authorFlairRichtext,
                            color = flairColor
                        )
                    } else if (!flairText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        FlairChip(
                            text = flairText,
                            color = flairColor
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (comment.scoreHidden) "[score hidden]" else formatNumber(comment.score),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (comment.voteState) {
                            VoteState.UPVOTED -> Color(0xFFFF4500)
                            VoteState.DOWNVOTED -> Color(0xFF7193FF)
                            VoteState.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatTimeAgo(comment.createdUtc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (comment.isEdited) {
                        Text(
                            text = " (edited)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isHidden) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.VisibilityOff,
                            contentDescription = "Hidden",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isHidden) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Box(modifier = Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                onTouchStart()
                            }
                        }
                    }) {
                        key(selectionVersion) {
                            SelectionContainer {
                                MarkdownText(
                                    markdown = comment.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    onTextClick = onSelect,
                                    renderInlineImages = renderInlineImages,
                                    onImageClick = onInlineImageClick
                                )
                            }
                        }
                    }
                }

                if (isSelected) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        if (isLoggedIn) {
                            if (loggedInUsername != null && comment.author == loggedInUsername) {
                                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        IconButton(
                            onClick = { vote(if (comment.likes == true) 0 else 1) },
                            enabled = isLoggedIn,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (comment.voteState == VoteState.UPVOTED) Icons.Filled.KeyboardArrowUp else Icons.Outlined.KeyboardArrowUp,
                                contentDescription = "Upvote",
                                modifier = Modifier.size(24.dp),
                                tint = if (comment.voteState == VoteState.UPVOTED) Color(0xFFFF4500) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { vote(if (comment.likes == false) 0 else -1) },
                            enabled = isLoggedIn,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (comment.voteState == VoteState.DOWNVOTED) Icons.Filled.KeyboardArrowDown else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Downvote",
                                modifier = Modifier.size(24.dp),
                                tint = if (comment.voteState == VoteState.DOWNVOTED) Color(0xFF7193FF) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            var showContextMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showContextMenu = true }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showContextMenu,
                                onDismissRequest = { showContextMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    onClick = {
                                        showContextMenu = false
                                        onShare()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                                )
                                if (isLoggedIn) {
                                    DropdownMenuItem(
                                        text = { Text(if (comment.isSaved) "Unsave" else "Save") },
                                        onClick = {
                                            showContextMenu = false
                                            onSave()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (comment.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Go to comment") },
                                    onClick = {
                                        showContextMenu = false
                                        onGoToCommentNav(comment.id)
                                    },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) }
                                )
                            }
                        }
                        if (isLoggedIn) {
                            IconButton(onClick = onReply, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply", modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
