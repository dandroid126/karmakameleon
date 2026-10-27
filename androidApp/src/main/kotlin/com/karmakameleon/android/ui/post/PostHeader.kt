package com.karmakameleon.android.ui.post

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.android.ui.components.FlairChip
import com.karmakameleon.android.ui.components.LongPressMenuBox
import com.karmakameleon.android.ui.components.MarkdownText
import com.karmakameleon.android.ui.components.PostScore
import com.karmakameleon.android.ui.components.PostVotePill
import com.karmakameleon.android.ui.components.ProgressiveAsyncImage
import com.karmakameleon.android.ui.components.SelectableArea
import com.karmakameleon.android.ui.components.SubredditFlairChip
import com.karmakameleon.android.ui.components.VideoPlayer
import com.karmakameleon.android.ui.components.YouTubePlayer
import com.karmakameleon.android.ui.components.formatNumber
import com.karmakameleon.android.ui.components.formatTimeAgo
import com.karmakameleon.android.ui.components.gifMenuItems
import com.karmakameleon.android.ui.components.imageMenuItems
import com.karmakameleon.android.ui.components.showShortToast
import com.karmakameleon.android.ui.components.videoMenuItems
import com.karmakameleon.android.ui.theme.postFlairColors
import com.karmakameleon.shared.domain.model.GalleryItem
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.util.extractYouTubeVideoId
import org.koin.compose.koinInject
import kotlin.math.roundToInt

private val POST_DETAIL_IMAGE_MAX_HEIGHT = 400.dp

/** What the post at the top of the comments does when its controls are tapped. */
internal data class PostHeaderActions(
    val onToggleBodyExpanded: () -> Unit,
    val onUpvote: () -> Unit,
    val onDownvote: () -> Unit,
    val onSave: () -> Unit,
    val onSortClick: () -> Unit,
    val onReply: () -> Unit,
    val onImageClick: (urls: List<String>, initialPage: Int) -> Unit,
    val onInlineImageClick: (String) -> Unit,
    val onTouchStart: () -> Unit,
)

/** The post above its comments: who posted it, its title, and its media and text, which can be collapsed. */
@Composable
internal fun PostHeader(
    post: Post,
    isBodyExpanded: Boolean,
    isLoggedIn: Boolean,
    actions: PostHeaderActions,
    selectionVersion: Int,
    renderInlineImages: Boolean,
    nsfwEnabled: Boolean,
    navigationHandler: NavigationHandler = koinInject(),
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        PostHeaderByline(post = post, navigationHandler = navigationHandler)
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (post.isNsfw) FlairChip("NSFW", postFlairColors().nsfwColor)
            if (post.isSpoiler) FlairChip("Spoiler", postFlairColors().spoilerColor)
            post.linkFlairText?.let { SubredditFlairChip(it, post.linkFlairBackgroundColor) }
        }
        Spacer(modifier = Modifier.height(8.dp))

        SelectableArea(selectionVersion = selectionVersion, onTouchStart = actions.onTouchStart) {
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = actions.onToggleBodyExpanded) {
                Icon(
                    imageVector = if (isBodyExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isBodyExpanded) "Collapse post body" else "Expand post body",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PostBody(
            post = post,
            isExpanded = isBodyExpanded,
            actions = actions,
            selectionVersion = selectionVersion,
            renderInlineImages = renderInlineImages,
            nsfwEnabled = nsfwEnabled,
            navigationHandler = navigationHandler,
        )

        Spacer(modifier = Modifier.height(16.dp))
        PostHeaderActionRow(post = post, isLoggedIn = isLoggedIn, actions = actions)
    }
}

@Composable
private fun PostHeaderByline(
    post: Post,
    navigationHandler: NavigationHandler,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "r/${post.subreddit}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { navigationHandler.onSubredditClick(post.subreddit) },
        )
        Text(" • ", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "u/${post.author}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { navigationHandler.onUserClick(post.author) },
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = formatTimeAgo(post.createdUtc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// The media, the self text, and links to the linked page and the original of a crosspost, which can be collapsed.
// The text stays composed while it's collapsed, so expanding it doesn't lay out all of a long post again. The media
// leaves composition, so a video doesn't keep playing, and the links go with it.
@Composable
private fun PostBody(
    post: Post,
    isExpanded: Boolean,
    actions: PostHeaderActions,
    selectionVersion: Int,
    renderInlineImages: Boolean,
    nsfwEnabled: Boolean,
    navigationHandler: NavigationHandler,
) {
    // NSFW media is only shown when NSFW content is enabled.
    if (!post.isNsfw || nsfwEnabled) {
        AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            PostMedia(post = post, onImageClick = actions.onImageClick, navigationHandler = navigationHandler)
        }
    }

    val selfText = post.selfText
    if (!selfText.isNullOrBlank()) {
        KeptWhenCollapsed(isExpanded = isExpanded) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                SelectableArea(selectionVersion = selectionVersion, onTouchStart = actions.onTouchStart) {
                    MarkdownText(
                        markdown = selfText,
                        style = MaterialTheme.typography.bodyMedium,
                        renderInlineImages = renderInlineImages,
                        onImageClick = actions.onInlineImageClick,
                    )
                }
            }
        }
    }

    AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
        Column {
            if (post.isLinkPost) {
                PostLink(text = post.domain) { navigationHandler.handleLink(post.url) }
            }
            val crosspostParentSubreddit = post.crosspostParentSubreddit
            if (post.isCrosspost && crosspostParentSubreddit != null) {
                PostLink(text = "View original post at r/$crosspostParentSubreddit") {
                    post.crosspostParentPermalink?.let { navigationHandler.handleLink(it) }
                }
            }
        }
    }
}

// Expands and collapses its content like AnimatedVisibility's expandVertically and shrinkVertically, but keeps it
// composed and measured while it's collapsed, so expanding it again only places it. The content is measured as if it
// were expanded, and the animation only changes how much of it shows. While collapsed it isn't placed, so it isn't
// drawn, tapped or read by screen readers.
@Composable
private fun KeptWhenCollapsed(
    isExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    // The same spring AnimatedVisibility uses, so the text moves with the media and links around it.
    val shown by animateFloatAsState(if (isExpanded) 1f else 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    Box(
        modifier =
            Modifier
                .then(if (isExpanded) Modifier else Modifier.clearAndSetSemantics {})
                .clipToBounds()
                // Reads the animation while laying out, so each frame of it lays out again without composing again.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val height = (placeable.height * shown).roundToInt()
                    layout(placeable.width, height) { if (height > 0) placeable.place(0, 0) }
                },
    ) {
        content()
    }
}

@Composable
private fun PostLink(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .padding(top = 8.dp)
                .clickable(onClick = onClick),
    )
}

// The post's gallery, video, YouTube video, animated preview or image, in that order of preference.
@Composable
private fun PostMedia(
    post: Post,
    onImageClick: (urls: List<String>, initialPage: Int) -> Unit,
    navigationHandler: NavigationHandler,
) {
    val galleryItems =
        post.galleryData
            ?.items
            ?.filter { it.url != null }
            .orEmpty()
    val redditVideo = post.media?.redditVideo ?: post.preview?.redditVideoPreview
    val youTubeVideoId = extractYouTubeVideoId(post.url)
    val previewImage = post.preview?.images?.firstOrNull()
    val mp4Url = previewImage?.mp4Url
    val highResUrl =
        previewImage?.source?.url
            ?: galleryItems.firstOrNull()?.url
            ?: post.thumbnail?.takeIf { it.startsWith("http") }
            ?: post.url.takeIf { post.isImagePost }

    Column {
        when {
            galleryItems.size > 1 -> {
                PostMediaSpacer()
                PostGallery(galleryItems = galleryItems, onImageClick = onImageClick)
            }

            redditVideo != null -> {
                PostMediaSpacer()
                VideoPlayer(
                    videoUrl = redditVideo.fallbackUrl,
                    isGif = redditVideo.isGif,
                    modifier = Modifier.fillMaxWidth(),
                    menuItems =
                        if (redditVideo.isGif) {
                            gifMenuItems(redditVideo.fallbackUrl, gifImageUrl = post.url)
                        } else {
                            videoMenuItems(redditVideo.fallbackUrl)
                        },
                )
            }

            youTubeVideoId != null -> {
                PostMediaSpacer()
                YouTubePlayer(videoId = youTubeVideoId, modifier = Modifier.fillMaxWidth())
            }

            mp4Url != null -> {
                PostMediaSpacer()
                VideoPlayer(
                    videoUrl = mp4Url,
                    isGif = true,
                    modifier = Modifier.fillMaxWidth(),
                    menuItems = gifMenuItems(mp4Url, gifImageUrl = post.url),
                )
            }

            highResUrl != null -> {
                PostMediaSpacer()
                val lowResUrl =
                    previewImage?.resolutions?.firstOrNull()?.url
                        ?: post.thumbnail?.takeIf { it.startsWith("http") }
                // Tapping a link post's image opens the link; otherwise it opens the image viewer.
                PostImage(lowResUrl = lowResUrl, highResUrl = highResUrl) {
                    if (post.isLinkPost) navigationHandler.handleLink(post.url) else onImageClick(listOf(highResUrl), 0)
                }
            }
        }
    }
}

@Composable
private fun PostMediaSpacer() {
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun PostImage(
    lowResUrl: String?,
    highResUrl: String,
    onClick: () -> Unit,
) {
    LongPressMenuBox(
        menuItems = imageMenuItems(highResUrl),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(POST_DETAIL_IMAGE_MAX_HEIGHT)
                .clip(RoundedCornerShape(8.dp)),
        onClick = onClick,
    ) {
        ProgressiveAsyncImage(
            lowResUrl = lowResUrl,
            highResUrl = highResUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

// A swipeable gallery with a dot per item. Animated items play as videos; tapping an image opens the image viewer
// at it, with the gallery's other images.
@Composable
private fun PostGallery(
    galleryItems: List<GalleryItem>,
    onImageClick: (urls: List<String>, initialPage: Int) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { galleryItems.size })
    val imageItems = remember(galleryItems) { galleryItems.filter { !it.isVideo } }
    Column {
        HorizontalPager(
            state = pagerState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(POST_DETAIL_IMAGE_MAX_HEIGHT)
                    .clip(RoundedCornerShape(8.dp)),
        ) { page ->
            val item = galleryItems[page]
            val itemUrl = item.url
            if (item.isVideo && itemUrl != null) {
                VideoPlayer(
                    videoUrl = itemUrl,
                    isGif = true,
                    modifier = Modifier.fillMaxSize(),
                    menuItems = gifMenuItems(itemUrl, gifImageUrl = item.gifUrl),
                )
            } else {
                LongPressMenuBox(
                    menuItems = imageMenuItems(item.url.orEmpty()),
                    modifier = Modifier.fillMaxSize(),
                    onClick = { onImageClick(imageItems.mapNotNull { it.url }, imageItems.indexOf(item).coerceAtLeast(0)) },
                ) {
                    AsyncImage(
                        model = item.url,
                        contentDescription = item.caption,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        PagerDots(count = galleryItems.size, currentPage = pagerState.currentPage)
    }
}

@Composable
private fun PagerDots(
    count: Int,
    currentPage: Int,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        repeat(count) { index ->
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (index == currentPage) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (index == currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                        ),
            )
        }
    }
}

// Votes need an account, so the arrows are disabled when logged out; closed posts explain why votes and replies
// don't work.
@Composable
private fun PostHeaderActionRow(
    post: Post,
    isLoggedIn: Boolean,
    actions: PostHeaderActions,
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PostVotePill(
            post = post,
            isLoggedIn = isLoggedIn,
            onUpvote = actions.onUpvote,
            onDownvote = actions.onDownvote,
            buttonSize = 36.dp,
            scoreStyle = MaterialTheme.typography.labelLarge,
        )

        Text(
            text = "${formatNumber(post.numComments)} comments",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = actions.onSortClick) {
            Icon(
                Icons.AutoMirrored.Default.Sort,
                contentDescription = "Sort comments",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isLoggedIn) {
            IconButton(onClick = actions.onSave) {
                Icon(
                    if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Save",
                    tint = if (post.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val commentingDisabled = post.isCommentingDisabled
            IconButton(
                onClick = { if (commentingDisabled) showShortToast(context, post.commentingDisabledReason) else actions.onReply() },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Reply",
                    tint =
                        if (commentingDisabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}
