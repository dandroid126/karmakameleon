package com.karmakameleon.android.ui.components

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.karmakameleon.android.ui.theme.mediaColors
import com.karmakameleon.android.ui.theme.postFlairColors
import com.karmakameleon.android.ui.theme.readStateColors
import com.karmakameleon.android.ui.theme.voteColors
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.VoteState
import kotlinx.coroutines.launch

private const val EXPAND_ANIMATION_MS = 200
private val COMPACT_THUMBNAIL_SIZE = 72.dp
private const val THUMBNAIL_SOURCE_MIN_WIDTH = 216

/**
 * Compact feed variant of [PostCard]. Tapping the card toggles a control bar instead of opening
 * the post; tapping the thumbnail opens the post. The highlight stays on the last tapped post
 * after its controls are collapsed.
 */
@Composable
fun CompactPostCard(
    post: Post,
    isExpanded: Boolean,
    isHighlighted: Boolean,
    onToggleExpanded: () -> Unit,
    actions: PostActions,
    isLoggedIn: Boolean,
    modifier: Modifier = Modifier,
    isRead: Boolean = false,
    nsfwPreviewMode: NsfwPreviewMode = NsfwPreviewMode.DO_NOT_PREFETCH,
    spoilerPreviewsEnabled: Boolean = false,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = highlightedCardColor(isHighlighted)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(start = 2.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            CompactVoteColumn(post = post, isLoggedIn = isLoggedIn, onUpvote = actions.onUpvote, onDownvote = actions.onDownvote)
            CompactPostInfo(
                post = post,
                isExpanded = isExpanded,
                isRead = isRead,
                onSubredditClick = actions.onSubredditClick,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 2.dp, end = 8.dp, top = 2.dp),
            )
            CompactThumbnail(
                post = post,
                nsfwPreviewMode = nsfwPreviewMode,
                spoilerPreviewsEnabled = spoilerPreviewsEnabled,
                onClick = actions.onClick,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(tween(EXPAND_ANIMATION_MS)) + fadeIn(tween(EXPAND_ANIMATION_MS)),
            exit = shrinkVertically(tween(EXPAND_ANIMATION_MS)) + fadeOut(tween(EXPAND_ANIMATION_MS)),
        ) {
            CompactControlBar(post = post, isLoggedIn = isLoggedIn, actions = actions)
        }
    }
}

// The card's color, tinted while it's the highlighted post.
@Composable
private fun highlightedCardColor(isHighlighted: Boolean): Color {
    val baseColor = CardDefaults.cardColors().containerColor
    val color by animateColorAsState(
        targetValue =
            if (isHighlighted) {
                MaterialTheme.colorScheme.primary
                    .copy(alpha = 0.14f)
                    .compositeOver(baseColor)
            } else {
                baseColor
            },
        animationSpec = tween(EXPAND_ANIMATION_MS),
        label = "compactPostHighlight",
    )
    return color
}

private fun showLoginRequired(context: Context) = showShortToast(context, "You need to be logged in to do that")

// Votes need an account and an open post; tapping explains which is missing.
@Composable
private fun CompactVoteColumn(
    post: Post,
    isLoggedIn: Boolean,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
) {
    val context = LocalContext.current
    val voteOrExplain = { vote: () -> Unit ->
        when {
            !isLoggedIn -> showLoginRequired(context)
            post.isVotingDisabled -> showShortToast(context, post.votingDisabledReason)
            else -> vote()
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(44.dp),
    ) {
        VoteArrowButton(post = post, isUpvote = true, onClick = { voteOrExplain(onUpvote) }, modifier = Modifier.size(28.dp))
        PostScore(post = post, maxLines = 1)
        VoteArrowButton(post = post, isUpvote = false, onClick = { voteOrExplain(onDownvote) }, modifier = Modifier.size(28.dp))
    }
}

// Flairs, title with the link's domain, subreddit and stats, and the author once expanded.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactPostInfo(
    post: Post,
    isExpanded: Boolean,
    isRead: Boolean,
    onSubredditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (post.hasFlairs) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                PostFlairChips(post)
            }
        }

        CompactTitle(post = post, isRead = isRead)
        Spacer(modifier = Modifier.height(4.dp))
        CompactSubredditLine(post = post, onSubredditClick = onSubredditClick)

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(tween(EXPAND_ANIMATION_MS)) + fadeIn(tween(EXPAND_ANIMATION_MS)),
            exit = shrinkVertically(tween(EXPAND_ANIMATION_MS)) + fadeOut(tween(EXPAND_ANIMATION_MS)),
        ) {
            Text(
                text = "by u/${post.author}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// The title, followed by the domain for a link post.
@Composable
private fun CompactTitle(
    post: Post,
    isRead: Boolean,
) {
    val readStateColors = readStateColors()
    val domainColor = MaterialTheme.colorScheme.onSurfaceVariant
    val domainStyle = MaterialTheme.typography.labelMedium
    Text(
        text =
            buildAnnotatedString {
                append(post.title)
                if (post.isLinkPost) {
                    withStyle(
                        SpanStyle(
                            color = domainColor,
                            fontSize = domainStyle.fontSize,
                            fontWeight = FontWeight.Normal,
                        ),
                    ) {
                        append(" (${post.domain})")
                    }
                }
            },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (isRead) FontWeight.Normal else FontWeight.SemiBold,
        color = if (isRead) readStateColors.readTitleColor else MaterialTheme.colorScheme.onSurface,
    )
}

// The subreddit, comment count and age.
@Composable
private fun CompactSubredditLine(
    post: Post,
    onSubredditClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "r/${post.subreddit}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .clickable(onClick = onSubredditClick),
        )
        Text(
            text = " • ${formatNumber(post.numComments)} comments • ${formatTimeAgo(post.createdUtc)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactControlBar(
    post: Post,
    isLoggedIn: Boolean,
    actions: PostActions,
) {
    val context = LocalContext.current
    Column {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
        ) {
            val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
            val accountIconTint = if (isLoggedIn) iconTint else iconTint.copy(alpha = 0.38f)

            CompactShareMenu(post = post, iconTint = iconTint)

            // Save and hide need an account; dimmed when logged out and explain why when tapped
            IconButton(onClick = if (isLoggedIn) actions.onSave else ({ showLoginRequired(context) })) {
                Icon(
                    imageVector = if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (post.isSaved) "Unsave" else "Save",
                    tint = if (post.isSaved) MaterialTheme.colorScheme.primary else accountIconTint,
                )
            }

            IconButton(onClick = if (isLoggedIn) actions.onHide else ({ showLoginRequired(context) })) {
                Icon(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = if (post.isHidden) "Unhide" else "Hide",
                    tint = accountIconTint,
                )
            }

            if (post.isLinkPost) {
                IconButton(onClick = { actions.onLinkClick(post.url) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = "Open link",
                        tint = iconTint,
                    )
                }
            }

            if (post.isCrosspost) {
                IconButton(onClick = actions.onCrosspostClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CallMerge,
                        contentDescription = "View original post",
                        tint = iconTint,
                    )
                }
            }

            CompactMoreMenu(
                post = post,
                iconTint = iconTint,
                onUserClick = actions.onUserClick,
                onBlockSubreddit = actions.onBlockSubreddit,
            )
        }
    }
}

@Composable
private fun CompactShareMenu(
    post: Post,
    iconTint: Color,
) {
    var showShareMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showShareMenu = true }) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share",
                tint = iconTint,
            )
        }

        DropdownMenu(
            expanded = showShareMenu,
            onDismissRequest = { showShareMenu = false },
        ) {
            CopyLinkMenuItems(post = post, onDone = { showShareMenu = false })
        }
    }
}

@Composable
private fun CompactMoreMenu(
    post: Post,
    iconTint: Color,
    onUserClick: () -> Unit,
    onBlockSubreddit: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = iconTint,
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("u/${post.author}'s profile") },
                onClick = {
                    onUserClick()
                    showMenu = false
                },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
            )
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

/**
 * Small square preview that opens the post when tapped. Posts without an image get a placeholder
 * icon so every post still has a way to be opened.
 */
@Composable
private fun CompactThumbnail(
    post: Post,
    nsfwPreviewMode: NsfwPreviewMode,
    spoilerPreviewsEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val postFlairColors = postFlairColors()
    val thumbnailUrl = compactThumbnailUrl(post)

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(COMPACT_THUMBNAIL_SIZE)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
    ) {
        when {
            post.isSpoiler && !spoilerPreviewsEnabled -> {
                HiddenThumbnail(
                    label = "Spoiler",
                    color = postFlairColors.spoilerColor,
                )
            }

            thumbnailUrl == null -> {
                Icon(
                    imageVector = if (post.isLinkPost) Icons.Default.Link else Icons.AutoMirrored.Outlined.Article,
                    contentDescription = "Open post",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            }

            post.isNsfw && nsfwPreviewMode == NsfwPreviewMode.DO_NOT_PREFETCH -> {
                HiddenThumbnail(
                    label = "NSFW",
                    color = postFlairColors.nsfwColor,
                )
            }

            post.isNsfw && nsfwPreviewMode == NsfwPreviewMode.PREFETCH_AND_BLUR -> {
                BlurredThumbnail(thumbnailUrl)
            }

            else -> {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = "Open post",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                MediaBadge(post = post, modifier = Modifier.align(Alignment.BottomEnd))
            }
        }
    }
}

// Reddit's preview resolutions run small to large; 216px is enough for a 72dp square
private fun compactThumbnailUrl(post: Post): String? {
    val previewImage = post.preview?.images?.firstOrNull()
    return previewImage?.resolutions?.firstOrNull { it.width >= THUMBNAIL_SOURCE_MIN_WIDTH }?.url
        ?: previewImage?.resolutions?.lastOrNull()?.url
        ?: previewImage?.source?.url
        ?: post.galleryData
            ?.items
            ?.firstOrNull()
            ?.url
        ?: post.thumbnail?.takeIf { it.startsWith("http") }
        ?: post.url.takeIf { post.isImagePost }
}

@Composable
private fun BlurredThumbnail(url: String) {
    val mediaColors = mediaColors()
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = url,
            contentDescription = "Open post",
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .blur(10.dp),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(mediaColors.blurScrimColor),
        ) {
            Icon(
                imageVector = Icons.Default.VisibilityOff,
                contentDescription = null,
                tint = mediaColors.onOverlayColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun HiddenThumbnail(
    label: String,
    color: Color,
) {
    val mediaColors = mediaColors()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .fillMaxSize()
                .background(mediaColors.hiddenPreviewColor),
    ) {
        Icon(
            imageVector = Icons.Default.VisibilityOff,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

/** Small corner badge marking videos and multi-image galleries. */
@Composable
private fun MediaBadge(
    post: Post,
    modifier: Modifier = Modifier,
) {
    val galleryCount = post.galleryData?.items?.size ?: 0
    if (!post.isVideoPost && galleryCount <= 1) return

    val mediaColors = mediaColors()
    Surface(
        color = mediaColors.overlayBadgeColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.padding(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Icon(
                imageVector = if (post.isVideoPost) Icons.Default.PlayArrow else Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = mediaColors.onOverlayColor,
                modifier = Modifier.size(12.dp),
            )
            if (!post.isVideoPost) {
                Text(
                    text = "$galleryCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = mediaColors.onOverlayColor,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
    }
}
