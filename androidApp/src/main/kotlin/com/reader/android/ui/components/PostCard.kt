package com.reader.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.reader.shared.domain.model.Post
import com.reader.shared.domain.model.VoteState
import androidx.compose.material3.Text

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
    isLoggedIn: Boolean,
    isRead: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    
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
                    FlairChip(text = "NSFW", color = Color(0xFFFF4444))
                }
                if (post.isSpoiler) {
                    FlairChip(text = "Spoiler", color = Color(0xFF888888))
                }
                if (post.isStickied) {
                    FlairChip(text = "Pinned", color = Color(0xFF00AA00))
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
                color = if (isRead) Color(0xFF8090B0) else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            
            // Content preview with max height and fade
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = POST_CARD_MAX_CONTENT_HEIGHT)
                    .clipToBounds()
            ) {
                Column {
                    // Thumbnail/Preview (for both videos and images)
                    val imageUrl = post.preview?.images?.firstOrNull()?.source?.url
                        ?: post.thumbnail?.takeIf { it.startsWith("http") }

                    if (imageUrl != null && !post.isNsfw) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
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

                // Fade-out gradient at bottom when content is tall
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            )
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
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
                        onClick = onUpvote,
                        enabled = isLoggedIn,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (post.voteState == VoteState.UPVOTED) 
                                Icons.Filled.KeyboardArrowUp else Icons.Outlined.KeyboardArrowUp,
                            contentDescription = "Upvote",
                            tint = if (post.voteState == VoteState.UPVOTED) 
                                Color(0xFFFF4500) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Text(
                        text = formatNumber(post.score),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (post.voteState) {
                            VoteState.UPVOTED -> Color(0xFFFF4500)
                            VoteState.DOWNVOTED -> Color(0xFF7193FF)
                            VoteState.NONE -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    
                    IconButton(
                        onClick = onDownvote,
                        enabled = isLoggedIn,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (post.voteState == VoteState.DOWNVOTED) 
                                Icons.Filled.KeyboardArrowDown else Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Downvote",
                            tint = if (post.voteState == VoteState.DOWNVOTED) 
                                Color(0xFF7193FF) else MaterialTheme.colorScheme.onSurfaceVariant
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
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatNumber(post.numComments),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
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
                            text = { Text("Share") },
                            onClick = { showMenu = false },
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = null)
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
            Color(android.graphics.Color.parseColor(colorString))
        } else {
            Color.Gray
        }
    } catch (e: Exception) {
        Color.Gray
    }
}
