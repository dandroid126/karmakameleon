package com.karmakameleon.android.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.karmakameleon.android.ui.theme.voteColors

// Small buttons shared by the action rows of posts, comments and messages.

/**
 * An upvote or downvote arrow, filled and colored when it's the current vote ([likes] is true for an upvote, false for
 * a downvote, null for none), and dimmed when voting is closed.
 */
@Composable
internal fun VoteArrowButton(
    likes: Boolean?,
    isUpvote: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isVotingDisabled: Boolean = false,
) {
    val voteColors = voteColors()
    val isCurrentVote = likes == isUpvote
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(
            imageVector =
                when {
                    isUpvote && isCurrentVote -> Icons.Filled.KeyboardArrowUp
                    isUpvote -> Icons.Outlined.KeyboardArrowUp
                    isCurrentVote -> Icons.Filled.KeyboardArrowDown
                    else -> Icons.Outlined.KeyboardArrowDown
                },
            contentDescription = if (isUpvote) "Upvote" else "Downvote",
            tint =
                when {
                    isVotingDisabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    isCurrentVote -> if (isUpvote) voteColors.upvoteColor else voteColors.downvoteColor
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

/**
 * Upvote and downvote arrows for a comment's or message's action row. Voting again in the same direction removes the
 * vote.
 */
@Composable
internal fun RowScope.VoteArrows(
    likes: Boolean?,
    enabled: Boolean,
    onVote: (direction: Int) -> Unit,
) {
    VoteArrowButton(likes, isUpvote = true, onClick = { onVote(if (likes == true) 0 else 1) }, ActionButtonModifier, enabled)
    VoteArrowButton(likes, isUpvote = false, onClick = { onVote(if (likes == false) 0 else -1) }, ActionButtonModifier, enabled)
}

/** One of the small icon buttons in a comment's or message's action row. */
@Composable
internal fun ActionIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(onClick = onClick, modifier = ActionButtonModifier) {
        Icon(imageVector, contentDescription = contentDescription, modifier = Modifier.size(22.dp), tint = tint)
    }
}

@Composable
internal fun ReplyButton(onClick: () -> Unit) {
    ActionIconButton(Icons.AutoMirrored.Filled.Reply, "Reply", onClick, tint = LocalContentColor.current)
}

private val ActionButtonModifier = Modifier.size(36.dp)
