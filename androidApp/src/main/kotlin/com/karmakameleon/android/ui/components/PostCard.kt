package com.karmakameleon.android.ui.components

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karmakameleon.android.ui.theme.postFlairColors
import com.karmakameleon.android.ui.theme.readStateColors
import com.karmakameleon.android.ui.theme.voteColors
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.VoteState
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt

private val POST_CARD_MAX_CONTENT_HEIGHT = 400.dp

@Composable
fun PostCard(
    post: Post,
    onClick: () -> Unit,
    onSubredditClick: () -> Unit,
    onUserClick: () -> Unit,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onSave: () -> Unit,
    onHide: () -> Unit,
    onBlockSubreddit: () -> Unit = {},
    isLoggedIn: Boolean,
    onLinkClick: (String) -> Unit = {},
    onCrosspostClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    isRead: Boolean = false,
    nsfwPreviewMode: NsfwPreviewMode = NsfwPreviewMode.DO_NOT_PREFETCH,
    spoilerPreviewsEnabled: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val voteColors = voteColors()
    val postFlairColors = postFlairColors()
    val readStateColors = readStateColors()
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "r/${post.subreddit}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onSubredditClick)
                )
                Text(
                    text = " • ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "u/${post.author}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onUserClick)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatTimeAgo(post.createdUtc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Flairs
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
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
                    FlairChip(
                        text = flair,
                        color = post.linkFlairBackgroundColor?.let { parseColor(it) }
                            ?: MaterialTheme.colorScheme.secondaryContainer
                    )
                }
            }
            
            if (post.isNsfw || post.isSpoiler || post.isStickied || post.linkFlairText != null) {
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            // Title
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isRead) FontWeight.Normal else FontWeight.Bold,
                color = if (isRead) readStateColors.readTitleColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            
            // Content preview with max height and fade
            if (post.isSpoiler && !spoilerPreviewsEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                SpoilerBlackBox(
                    modifier = Modifier.clickable { onClick() }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = POST_CARD_MAX_CONTENT_HEIGHT)
                        .clipToBounds()
                ) {
                    Column {
                        // Thumbnail/Preview (for both videos and images)
                        val previewImage = post.preview?.images?.firstOrNull()
                        val galleryUrl = post.galleryData?.items?.firstOrNull()?.url
                        val highResUrl = previewImage?.source?.url
                            ?: galleryUrl
                            ?: post.thumbnail?.takeIf { it.startsWith("http") }
                            ?: post.url.takeIf { post.isImagePost }
                        val lowResUrl = previewImage?.resolutions?.firstOrNull()?.url
                            ?: post.thumbnail?.takeIf { it.startsWith("http") }

                        if (highResUrl != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (post.isNsfw) {
                                when (nsfwPreviewMode) {
                                    NsfwPreviewMode.DO_NOT_PREFETCH -> NsfwBlackBox()
                                    NsfwPreviewMode.PREFETCH_AND_BLUR -> NsfwBlurredImage(
                                        lowResUrl = lowResUrl,
                                        highResUrl = highResUrl
                                    )
                                    NsfwPreviewMode.SHOW_PREVIEWS -> Box {
                                        ProgressiveAsyncImage(
                                            lowResUrl = lowResUrl,
                                            highResUrl = highResUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 300.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        val galleryCount = post.galleryData?.items?.size ?: 0
                                        if (galleryCount > 1) {
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                            ) {
                                                Text(
                                                    text = "1/$galleryCount",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box {
                                    ProgressiveAsyncImage(
                                        lowResUrl = lowResUrl,
                                        highResUrl = highResUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 300.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        // Commenting this out for now, but this is where we would add the clickability of the image to follow the link.
                                        // I can't decide if I like it or not.
                                        // Maybe this will be configurable in the settings.
//                                    .then(
//                                        if (post.isLinkPost) Modifier.clickable { onLinkClick(post.url) }
//                                        else Modifier
//                                    ),
                                        contentScale = ContentScale.Crop
                                    )
                                    val galleryCount = post.galleryData?.items?.size ?: 0
                                    if (galleryCount > 1) {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = "1/$galleryCount",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Self text preview (plain text, no clickable links)
                        if (post.isTextPost) {
                            post.selfText?.let { text ->
                                if (text.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 6,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Link domain
                        if (post.isLinkPost) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = post.domain,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val votingDisabled = post.isVotingDisabled
                val commentingDisabled = post.isCommentingDisabled

                // Vote buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 4.dp)
                ) {
                    IconButton(
                        onClick = if (votingDisabled) {
                            { Toast.makeText(context, post.votingDisabledReason, Toast.LENGTH_SHORT).show() }
                        } else onUpvote,
                        enabled = isLoggedIn,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (post.voteState == VoteState.UPVOTED)
                                Icons.Filled.KeyboardArrowUp else Icons.Outlined.KeyboardArrowUp,
                            contentDescription = "Upvote",
                            tint = when {
                                votingDisabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                post.voteState == VoteState.UPVOTED -> voteColors.upvoteColor
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Text(
                        text = formatNumber(post.score),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            votingDisabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            post.voteState == VoteState.UPVOTED -> voteColors.upvoteColor
                            post.voteState == VoteState.DOWNVOTED -> voteColors.downvoteColor
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )

                    IconButton(
                        onClick = if (votingDisabled) {
                            { Toast.makeText(context, post.votingDisabledReason, Toast.LENGTH_SHORT).show() }
                        } else onDownvote,
                        enabled = isLoggedIn,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (post.voteState == VoteState.DOWNVOTED)
                                Icons.Filled.KeyboardArrowDown else Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Downvote",
                            tint = when {
                                votingDisabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                post.voteState == VoteState.DOWNVOTED -> voteColors.downvoteColor
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Comments
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .then(
                            if (commentingDisabled) Modifier.clickable {
                                Toast.makeText(context, post.commentingDisabledReason, Toast.LENGTH_SHORT).show()
                            } else Modifier
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (commentingDisabled)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatNumber(post.numComments),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (commentingDisabled)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Open link
                if (post.isLinkPost) {
                    IconButton(
                        onClick = { onLinkClick(post.url) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = "Open link",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Crosspost link
                if (post.isCrosspost) {
                    IconButton(
                        onClick = onCrosspostClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallMerge,
                            contentDescription = "View original post",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Save
                if (isLoggedIn) {
                    IconButton(
                        onClick = onSave,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (post.isSaved) "Unsave" else "Save",
                            tint = if (post.isSaved) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // More options
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (isLoggedIn) {
                            DropdownMenuItem(
                                text = { Text(if (post.isHidden) "Unhide" else "Hide") },
                                onClick = {
                                    onHide()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Copy comments link") },
                            onClick = {
                                val link = "https://www.reddit.com${post.permalink}"
                                coroutineScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", link))) }
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Link, contentDescription = null)
                            }
                        )
                        post.contentLink?.let { contentLink ->
                            DropdownMenuItem(
                                text = { Text("Copy post link") },
                                onClick = {
                                    coroutineScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", contentLink))) }
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Link, contentDescription = null)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Block r/${post.subreddit}") },
                            onClick = {
                                onBlockSubreddit()
                                Toast.makeText(context, "Blocked r/${post.subreddit}", Toast.LENGTH_SHORT).show()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Block, contentDescription = null)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FlairChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun RichFlairChip(
    richtext: List<com.karmakameleon.shared.domain.model.FlairRichtext>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            richtext.forEach { part ->
                when (part.type) {
                    "text" -> {
                        part.text?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = color
                            )
                        }
                    }
                    "emoji" -> {
                        part.url?.let { url ->
                            coil3.compose.AsyncImage(
                                model = url,
                                contentDescription = part.text,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatTimeAgo(timestampSeconds: Long): String {
    val now = System.currentTimeMillis() / 1000
    val diff = now - timestampSeconds
    
    return when {
        diff < 60 -> "now"
        diff < 3600 -> "${diff / 60}m"
        diff < 86400 -> "${diff / 3600}h"
        diff < 604800 -> "${diff / 86400}d"
        diff < 2592000 -> "${diff / 604800}w"
        diff < 31536000 -> "${diff / 2592000}mo"
        else -> "${diff / 31536000}y"
    }
}

fun formatNumber(num: Int): String {
    return when {
        num >= 1_000_000 -> String.format("%.1fM", num / 1_000_000.0)
        num >= 1_000 -> String.format("%.1fK", num / 1_000.0)
        else -> num.toString()
    }
}

fun parseColor(colorString: String): Color {
    return try {
        if (colorString.startsWith("#") && colorString.length >= 7) {
            Color(colorString.toColorInt())
        } else {
            Color.Gray
        }
    } catch (e: Exception) {
        Color.Gray
    }
}
