package com.karmakameleon.android.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.android.ui.theme.commentColors
import com.karmakameleon.android.ui.theme.voteColors
import com.karmakameleon.shared.data.repository.PostRepository
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.domain.model.VoteState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun CommentItem(
    comment: Comment,
    isSelected: Boolean,
    isHidden: Boolean,
    actions: CommentActions,
    isLoggedIn: Boolean,
    modifier: Modifier = Modifier,
    loggedInUsername: String? = null,
    selectionVersion: Int = 0,
    renderInlineImages: Boolean = true,
    isSingleThreadMode: Boolean = false,
    showTopControls: Boolean = true,
    showSubreddit: Boolean = false,
    postRepository: PostRepository = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val navigationHandler = koinInject<NavigationHandler>()

    val vote = { direction: Int ->
        scope.launch { postRepository.voteComment(comment, direction).onSuccess(actions.onCommentUpdated) }
    }

    val commentColors = commentColors()
    val depthColor = commentColors.depthColors[comment.depth % commentColors.depthColors.size]
    val hasParentComment = comment.parentId.startsWith("t1_")
    val showParentControls = if (isSingleThreadMode) hasParentComment else comment.depth > 0

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = (comment.depth * 12).dp)
                .background(if (isSelected) commentColors.selectedBackground else Color.Transparent)
                .clickable { actions.onSelect() },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (comment.depth > 0) DepthBar(color = depthColor)

            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 8.dp)) {
                CommentHeader(
                    comment = comment,
                    showControls = isSelected && showTopControls,
                    controls = actions.navigationControls(showParentControls),
                    showSubreddit = showSubreddit,
                    isHidden = isHidden,
                    loggedInUsername = loggedInUsername,
                    navigationHandler = navigationHandler,
                )

                if (!isHidden) {
                    CommentBody(
                        body = comment.body,
                        selectionVersion = selectionVersion,
                        onTouchStart = actions.onTouchStart,
                        onTextClick = actions.onSelect,
                        renderInlineImages = renderInlineImages,
                        onInlineImageClick = actions.onInlineImageClick,
                    )
                }

                if (isSelected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CommentActionRow(
                        comment = comment,
                        isLoggedIn = isLoggedIn,
                        isOwnComment = loggedInUsername != null && comment.author == loggedInUsername,
                        onVote = { vote(it) },
                        actions = actions,
                    )
                }
            }
        }
    }
}

// The thread's depth line at the comment's left edge.
@Composable
private fun DepthBar(color: Color) {
    Box(
        modifier =
            Modifier
                .padding(end = 8.dp)
                .width(2.dp)
                .background(color.copy(alpha = 0.5f)),
    )
}

@Composable
private fun CommentBody(
    body: String,
    selectionVersion: Int,
    onTouchStart: () -> Unit,
    onTextClick: () -> Unit,
    renderInlineImages: Boolean,
    onInlineImageClick: (String) -> Unit,
) {
    SelectableArea(
        selectionVersion = selectionVersion,
        onTouchStart = onTouchStart,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        MarkdownText(
            markdown = body,
            style = MaterialTheme.typography.bodyMedium,
            onTextClick = onTextClick,
            renderInlineImages = renderInlineImages,
            onImageClick = onInlineImageClick,
        )
    }
}

// Done and Hide, then a pair of buttons for moving to another comment: Root and Parent move up the thread, Prev and
// Next move between top-level comments.
private class CommentControls(
    val onDone: () -> Unit,
    val onHide: () -> Unit,
    val first: Pair<String, () -> Unit>,
    val second: Pair<String, () -> Unit>,
)

private fun CommentActions.navigationControls(showParentControls: Boolean) =
    if (showParentControls) {
        CommentControls(onDone, onHide, "Root" to onRoot, "Parent" to onParent)
    } else {
        CommentControls(onDone, onHide, "Prev" to onPrev, "Next" to onNext)
    }

// The controls of a selected comment, its subreddit when shown outside its thread, and the byline.
@Composable
private fun CommentHeader(
    comment: Comment,
    showControls: Boolean,
    controls: CommentControls,
    showSubreddit: Boolean,
    isHidden: Boolean,
    loggedInUsername: String?,
    navigationHandler: NavigationHandler,
) {
    Column {
        if (showControls) {
            CommentTopControls(controls)
            Spacer(modifier = Modifier.height(4.dp))
        }
        if (showSubreddit) {
            Text(
                text = "r/${comment.subreddit}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { navigationHandler.onSubredditClick(comment.subreddit) },
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        CommentByline(
            comment = comment,
            isHidden = isHidden,
            loggedInUsername = loggedInUsername,
            onUserClick = { navigationHandler.onUserClick(comment.author) },
        )
    }
}

@Composable
private fun CommentTopControls(controls: CommentControls) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CommentControlButton("Done", controls.onDone)
            CommentControlButton("Hide", controls.onHide)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CommentControlButton(controls.first.first, controls.first.second)
            CommentControlButton(controls.second.first, controls.second.second)
        }
    }
}

@Composable
private fun CommentControlButton(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.height(32.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

// The author (colored for OP, moderators, admins and you), their flair, the score, the age, and whether the comment
// was edited or is hidden.
@Composable
private fun CommentByline(
    comment: Comment,
    isHidden: Boolean,
    loggedInUsername: String?,
    onUserClick: () -> Unit,
) {
    val commentColors = commentColors()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = comment.author,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color =
                when {
                    comment.isSubmitter -> commentColors.submitterColor
                    comment.distinguished == "moderator" -> commentColors.moderatorColor
                    comment.distinguished == "admin" -> commentColors.adminColor
                    loggedInUsername != null && comment.author == loggedInUsername -> commentColors.ownCommentColor
                    else -> MaterialTheme.colorScheme.onSurface
                },
            modifier = Modifier.clickable(onClick = onUserClick),
        )
        if (comment.isSubmitter) {
            Spacer(modifier = Modifier.width(4.dp))
            Text("OP", style = MaterialTheme.typography.labelSmall, color = commentColors.submitterColor)
        }
        AuthorFlair(comment)
        Spacer(modifier = Modifier.width(8.dp))
        CommentScore(comment)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatTimeAgo(comment.createdUtc) + if (comment.isEdited) " (edited)" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isHidden) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Filled.VisibilityOff,
                contentDescription = "Hidden",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Rich flair (with emoji) when the author has it, otherwise plain flair text.
@Composable
private fun RowScope.AuthorFlair(comment: Comment) {
    val flairText = comment.authorFlairText
    if (comment.authorFlairRichtext.isNotEmpty()) {
        Spacer(modifier = Modifier.width(4.dp))
        RichFlairChip(
            richtext = comment.authorFlairRichtext,
            backgroundColor = comment.authorFlairBackgroundColor,
            modifier = Modifier.weight(1f, fill = false),
        )
    } else if (!flairText.isNullOrBlank()) {
        Spacer(modifier = Modifier.width(4.dp))
        SubredditFlairChip(
            text = flairText,
            backgroundColor = comment.authorFlairBackgroundColor,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

// The score in the color of your vote. Subreddits can hide scores for a while; tapping explains the [~].
@Composable
private fun CommentScore(comment: Comment) {
    val voteColors = voteColors()
    val context = LocalContext.current
    Text(
        text = if (comment.scoreHidden) "[~]" else formatNumber(comment.score),
        style = MaterialTheme.typography.labelSmall,
        color =
            when (comment.voteState) {
                VoteState.UPVOTED -> voteColors.upvoteColor
                VoteState.DOWNVOTED -> voteColors.downvoteColor
                VoteState.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier =
            if (comment.scoreHidden) {
                Modifier.clickable { showShortToast(context, "Score hidden by subreddit rules") }
            } else {
                Modifier
            },
    )
}

// Shown under a selected comment: edit and delete for your own, votes, more options, and reply. Voting again in the
// same direction removes the vote.
@Composable
private fun CommentActionRow(
    comment: Comment,
    isLoggedIn: Boolean,
    isOwnComment: Boolean,
    onVote: (direction: Int) -> Unit,
    actions: CommentActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        if (isLoggedIn && isOwnComment) {
            ActionIconButton(Icons.Default.Edit, "Edit", actions.onEdit)
            ActionIconButton(Icons.Default.Delete, "Delete", actions.onDelete, tint = MaterialTheme.colorScheme.error)
        }
        VoteArrows(comment.likes, enabled = isLoggedIn, onVote = onVote)
        CommentOverflowMenu(comment = comment, isLoggedIn = isLoggedIn, actions = actions)
        if (isLoggedIn) ReplyButton(actions.onReply)
    }
}

// Share, save (needs an account) and opening the comment on its own.
@Composable
private fun CommentOverflowMenu(
    comment: Comment,
    isLoggedIn: Boolean,
    actions: CommentActions,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val runAndClose = { action: () -> Unit ->
        showContextMenu = false
        action()
    }
    Box {
        ActionIconButton(Icons.Default.MoreVert, "More", onClick = { showContextMenu = true })
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("Share") },
                onClick = { runAndClose(actions.onShare) },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
            )
            if (isLoggedIn) {
                DropdownMenuItem(
                    text = { Text(if (comment.isSaved) "Unsave" else "Save") },
                    onClick = { runAndClose(actions.onSave) },
                    leadingIcon = {
                        Icon(
                            if (comment.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = null,
                        )
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Go to comment") },
                onClick = { runAndClose { actions.onGoToCommentNav(comment.id) } },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
            )
        }
    }
}
