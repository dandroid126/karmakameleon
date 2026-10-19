package com.karmakameleon.android.ui.components

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/**
 * The post's vote arrows and score on a rounded background. Votes need an account, so the arrows are disabled when
 * logged out. When voting is closed they explain why.
 */
@Composable
internal fun PostVotePill(
    post: Post,
    isLoggedIn: Boolean,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    buttonSize: Dp = 32.dp,
    scoreStyle: TextStyle = MaterialTheme.typography.labelMedium,
) {
    val context = LocalContext.current
    val voteOrExplain = { vote: () -> Unit ->
        if (post.isVotingDisabled) showShortToast(context, post.votingDisabledReason) else vote()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                .padding(horizontal = 4.dp),
    ) {
        PostVoteArrow(post, isUpvote = true, onClick = { voteOrExplain(onUpvote) }, Modifier.size(buttonSize), enabled = isLoggedIn)
        PostScore(post, style = scoreStyle)
        PostVoteArrow(post, isUpvote = false, onClick = { voteOrExplain(onDownvote) }, Modifier.size(buttonSize), enabled = isLoggedIn)
    }
}

/** A [VoteArrowButton] for a post, dimmed when voting on it is closed. */
@Composable
internal fun PostVoteArrow(
    post: Post,
    isUpvote: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    VoteArrowButton(post.likes, isUpvote, onClick, modifier, enabled, isVotingDisabled = post.isVotingDisabled)
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

/** The save button, filled in the primary color when the post is saved. */
@Composable
internal fun SavePostButton(
    post: Post,
    onClick: () -> Unit,
    unsavedTint: Color,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = if (post.isSaved) "Unsave" else "Save",
            tint = if (post.isSaved) MaterialTheme.colorScheme.primary else unsavedTint,
        )
    }
}

/** A menu item that blocks the post's subreddit and confirms it with a toast. */
@Composable
internal fun BlockSubredditMenuItem(
    post: Post,
    onBlockSubreddit: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    DropdownMenuItem(
        text = { Text("Block r/${post.subreddit}") },
        onClick = {
            onBlockSubreddit()
            showShortToast(context, "Blocked r/${post.subreddit}")
            onDone()
        },
        leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
    )
}
