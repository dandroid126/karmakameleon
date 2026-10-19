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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karmakameleon.android.ui.theme.mediaColors
import com.karmakameleon.android.ui.theme.postFlairColors
import com.karmakameleon.android.ui.theme.readStateColors
import com.karmakameleon.android.ui.theme.voteColors
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.VoteState
import com.karmakameleon.shared.util.flairNeedsDarkText
import com.karmakameleon.shared.util.parseFlairColor
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val DAYS_PER_WEEK = 7
private const val DAYS_PER_MONTH = 30
private const val DAYS_PER_YEAR = 365
private const val THOUSAND = 1_000
private const val MILLION = 1_000_000

private val POST_CARD_MAX_CONTENT_HEIGHT = 400.dp

@Composable
fun PostCard(
    post: Post,
    actions: PostActions,
    isLoggedIn: Boolean,
    modifier: Modifier = Modifier,
    isRead: Boolean = false,
    nsfwPreviewMode: NsfwPreviewMode = NsfwPreviewMode.DO_NOT_PREFETCH,
    spoilerPreviewsEnabled: Boolean = false,
) {
    val readStateColors = readStateColors()
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable(onClick = actions.onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            PostCardHeader(post = post, onSubredditClick = actions.onSubredditClick, onUserClick = actions.onUserClick)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PostFlairChips(post)
            }
            if (post.hasFlairs) {
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isRead) FontWeight.Normal else FontWeight.Bold,
                color = if (isRead) readStateColors.readTitleColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (post.isSpoiler && !spoilerPreviewsEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                SpoilerBlackBox(
                    modifier = Modifier.clickable { actions.onClick() },
                )
            } else {
                PostCardPreview(post = post, nsfwPreviewMode = nsfwPreviewMode)
            }

            Spacer(modifier = Modifier.height(8.dp))
            PostCardActionRow(post = post, isLoggedIn = isLoggedIn, actions = actions)
        }
    }
}

@Composable
private fun PostCardHeader(
    post: Post,
    onSubredditClick: () -> Unit,
    onUserClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "r/${post.subreddit}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onSubredditClick),
        )
        Text(
            text = " • ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "u/${post.author}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onUserClick),
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = formatTimeAgo(post.createdUtc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// The post's image, text or link, cut off at a maximum height.
@Composable
private fun PostCardPreview(
    post: Post,
    nsfwPreviewMode: NsfwPreviewMode,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = POST_CARD_MAX_CONTENT_HEIGHT)
                .clipToBounds(),
    ) {
        Column {
            // Thumbnail/Preview (for both videos and images)
            val previewImage = post.preview?.images?.firstOrNull()
            val galleryUrl =
                post.galleryData
                    ?.items
                    ?.firstOrNull()
                    ?.url
            val highResUrl =
                previewImage?.source?.url
                    ?: galleryUrl
                    ?: post.thumbnail?.takeIf { it.startsWith("http") }
                    ?: post.url.takeIf { post.isImagePost }
            val lowResUrl =
                previewImage?.resolutions?.firstOrNull()?.url
                    ?: post.thumbnail?.takeIf { it.startsWith("http") }

            if (highResUrl != null) {
                Spacer(modifier = Modifier.height(8.dp))
                PostPreviewImage(post = post, lowResUrl = lowResUrl, highResUrl = highResUrl, nsfwPreviewMode = nsfwPreviewMode)
            }

            // Self text preview (plain text, no clickable links)
            val selfText = post.selfText
            if (post.isTextPost && !selfText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = selfText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (post.isLinkPost) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = post.domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

// NSFW previews are hidden, blurred or shown depending on the setting.
@Composable
private fun PostPreviewImage(
    post: Post,
    lowResUrl: String?,
    highResUrl: String,
    nsfwPreviewMode: NsfwPreviewMode,
) {
    when {
        !post.isNsfw || nsfwPreviewMode == NsfwPreviewMode.SHOW_PREVIEWS -> PreviewImage(post, lowResUrl, highResUrl)
        nsfwPreviewMode == NsfwPreviewMode.DO_NOT_PREFETCH -> NsfwBlackBox()
        else -> NsfwBlurredImage(lowResUrl = lowResUrl, highResUrl = highResUrl)
    }
}

// The preview image, marked "1/N" for a gallery.
@Composable
private fun PreviewImage(
    post: Post,
    lowResUrl: String?,
    highResUrl: String,
) {
    val mediaColors = mediaColors()
    Box {
        ProgressiveAsyncImage(
            lowResUrl = lowResUrl,
            highResUrl = highResUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .clip(RoundedCornerShape(8.dp)),
            // Commenting this out for now, but this is where we would add the clickability of the image to
            // follow the link.
            // I can't decide if I like it or not.
            // Maybe this will be configurable in the settings.
//                .then(
//                    if (post.isLinkPost) Modifier.clickable { onLinkClick(post.url) }
//                    else Modifier
//                ),
            contentScale = ContentScale.Crop,
        )
        val galleryCount = post.galleryData?.items?.size ?: 0
        if (galleryCount > 1) {
            Surface(
                color = mediaColors.overlayBadgeColor,
                shape = RoundedCornerShape(12.dp),
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
            ) {
                Text(
                    text = "1/$galleryCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = mediaColors.onOverlayColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PostCardActionRow(
    post: Post,
    isLoggedIn: Boolean,
    actions: PostActions,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        PostVotePill(post = post, isLoggedIn = isLoggedIn, onUpvote = actions.onUpvote, onDownvote = actions.onDownvote)
        PostCommentCount(post)
        Spacer(modifier = Modifier.weight(1f))

        if (post.isLinkPost) {
            PostActionIcon(Icons.AutoMirrored.Outlined.OpenInNew, "Open link") { actions.onLinkClick(post.url) }
        }
        if (post.isCrosspost) {
            PostActionIcon(Icons.AutoMirrored.Filled.CallMerge, "View original post", actions.onCrosspostClick)
        }
        if (isLoggedIn) {
            IconButton(
                onClick = actions.onSave,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (post.isSaved) "Unsave" else "Save",
                    tint = if (post.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PostMoreMenu(post = post, isLoggedIn = isLoggedIn, onHide = actions.onHide, onBlockSubreddit = actions.onBlockSubreddit)
    }
}

// Votes need an account, so the arrows are disabled when logged out. When voting is closed they explain why.
@Composable
private fun PostVotePill(
    post: Post,
    isLoggedIn: Boolean,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
) {
    val context = LocalContext.current
    val voteOrExplain = { vote: () -> Unit ->
        if (post.isVotingDisabled) showShortToast(context, post.votingDisabledReason) else vote()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(20.dp),
                ).padding(horizontal = 4.dp),
    ) {
        VoteArrowButton(
            post = post,
            isUpvote = true,
            onClick = { voteOrExplain(onUpvote) },
            enabled = isLoggedIn,
            modifier = Modifier.size(32.dp),
        )
        PostScore(post)
        VoteArrowButton(
            post = post,
            isUpvote = false,
            onClick = { voteOrExplain(onDownvote) },
            enabled = isLoggedIn,
            modifier = Modifier.size(32.dp),
        )
    }
}

// The comment count. When commenting is closed it's dimmed and explains why when tapped.
@Composable
private fun PostCommentCount(post: Post) {
    val context = LocalContext.current
    val commentingDisabled = post.isCommentingDisabled
    val color =
        if (commentingDisabled) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(20.dp),
                ).padding(horizontal = 12.dp, vertical = 6.dp)
                .then(
                    if (commentingDisabled) {
                        Modifier.clickable { showShortToast(context, post.commentingDisabledReason) }
                    } else {
                        Modifier
                    },
                ),
    ) {
        Icon(
            imageVector = Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = color,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = formatNumber(post.numComments),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun PostActionIcon(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PostMoreMenu(
    post: Post,
    isLoggedIn: Boolean,
    onHide: () -> Unit,
    onBlockSubreddit: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box {
        PostActionIcon(Icons.Default.MoreVert, "More options") { showMenu = true }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
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
                            contentDescription = null,
                        )
                    },
                )
            }
            CopyLinkMenuItems(post = post, onDone = { showMenu = false })
            DropdownMenuItem(
                text = { Text("Block r/${post.subreddit}") },
                onClick = {
                    onBlockSubreddit()
                    showShortToast(context, "Blocked r/${post.subreddit}")
                    showMenu = false
                },
                leadingIcon = {
                    Icon(Icons.Default.Block, contentDescription = null)
                },
            )
        }
    }
}

@Composable
fun FlairChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Flair whose color is chosen by the subreddit. Drawn filled like on Reddit, with black or white
 * text (whichever is more readable) so it stays legible on any background in light and dark mode.
 * Flairs without a color get a neutral chip.
 */
@Composable
fun SubredditFlairChip(
    text: String,
    backgroundColor: String?,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor) = subredditFlairColors(backgroundColor)
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun subredditFlairColors(backgroundColor: String?): Pair<Color, Color> {
    val argb =
        parseFlairColor(backgroundColor)
            ?: return MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    val flairColors = postFlairColors()
    return Color(argb) to if (flairNeedsDarkText(argb)) flairColors.flairDarkTextColor else flairColors.flairLightTextColor
}

@Composable
fun RichFlairChip(
    richtext: List<com.karmakameleon.shared.domain.model.FlairRichtext>,
    backgroundColor: String?,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor) = subredditFlairColors(backgroundColor)
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.clipToBounds(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            richtext.forEach { part ->
                when (part.type) {
                    "text" -> {
                        part.text?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    "emoji" -> {
                        part.url?.let { url ->
                            coil3.compose.AsyncImage(
                                model = url,
                                contentDescription = part.text,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatTimeAgo(timestampSeconds: Long): String {
    val age = System.currentTimeMillis().milliseconds - timestampSeconds.seconds
    val days = age.inWholeDays

    return when {
        age < 1.minutes -> "now"
        age < 1.hours -> "${age.inWholeMinutes}m"
        age < 1.days -> "${age.inWholeHours}h"
        days < DAYS_PER_WEEK -> "${days}d"
        days < DAYS_PER_MONTH -> "${days / DAYS_PER_WEEK}w"
        days < DAYS_PER_YEAR -> "${days / DAYS_PER_MONTH}mo"
        else -> "${days / DAYS_PER_YEAR}y"
    }
}

fun formatNumber(num: Int): String =
    when {
        num >= MILLION -> String.format(Locale.getDefault(), "%.1fM", num.toDouble() / MILLION)
        num >= THOUSAND -> String.format(Locale.getDefault(), "%.1fK", num.toDouble() / THOUSAND)
        else -> num.toString()
    }
