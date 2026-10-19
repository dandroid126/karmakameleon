package com.karmakameleon.android.ui.components

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.karmakameleon.android.ui.theme.postFlairColors
import com.karmakameleon.android.ui.theme.voteColors
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.VoteState
import kotlinx.coroutines.launch

// Pieces shared by PostCard and CompactPostCard.

internal val Post.hasFlairs: Boolean
    get() = isNsfw || isSpoiler || isStickied || linkFlairText != null

/** The NSFW, spoiler and pinned chips, then the subreddit's flair. */
@Composable
internal fun RowScope.PostFlairChips(post: Post) {
    val postFlairColors = postFlairColors()
    if (post.isNsfw) {
        FlairChip(text = "NSFW", color = postFlairColors.nsfwColor)
    }
    if (post.isSpoiler) {
        FlairChip(text = "Spoiler", color = postFlairColors.spoilerColor)
    }
    if (post.isStickied) {
        FlairChip(text = "Pinned", color = postFlairColors.pinnedColor)
    }
    post.linkFlairText?.let { flair ->
        SubredditFlairChip(text = flair, backgroundColor = post.linkFlairBackgroundColor)
    }
}

/** An upvote or downvote arrow, filled and colored when that's the post's current vote, dimmed when voting is closed. */
@Composable
internal fun VoteArrowButton(
    post: Post,
    isUpvote: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val voteColors = voteColors()
    val isCurrentVote = post.voteState == if (isUpvote) VoteState.UPVOTED else VoteState.DOWNVOTED
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
                    post.isVotingDisabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    isCurrentVote -> if (isUpvote) voteColors.upvoteColor else voteColors.downvoteColor
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

/** The post's score, in the color of the current vote. */
@Composable
internal fun PostScore(
    post: Post,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    val voteColors = voteColors()
    Text(
        text = formatNumber(post.score),
        style = style,
        fontWeight = FontWeight.Bold,
        maxLines = maxLines,
        modifier = modifier,
        color =
            when {
                post.isVotingDisabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                post.voteState == VoteState.UPVOTED -> voteColors.upvoteColor
                post.voteState == VoteState.DOWNVOTED -> voteColors.downvoteColor
                else -> MaterialTheme.colorScheme.onSurface
            },
    )
}

/** Menu items that copy the post's comments link and, when it links somewhere, its content link. */
@Composable
internal fun ColumnScope.CopyLinkMenuItems(
    post: Post,
    onDone: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val copy = { link: String ->
        coroutineScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", link))) }
        onDone()
    }
    DropdownMenuItem(
        text = { Text("Copy comments link") },
        onClick = { copy("https://www.reddit.com${post.permalink}") },
        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
    )
    post.contentLink?.let { contentLink ->
        DropdownMenuItem(
            text = { Text("Copy post link") },
            onClick = { copy(contentLink) },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
        )
    }
}

internal fun showShortToast(
    context: Context,
    text: String?,
) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
