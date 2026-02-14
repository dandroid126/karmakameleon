package com.reader.android.ui.post

import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.FlairChip
import com.reader.android.ui.components.FullScreenImageViewer
import com.reader.android.ui.components.MarkdownText
import com.reader.android.ui.components.ProgressiveAsyncImage
import com.reader.android.ui.components.RedditLink
import com.reader.android.ui.components.VideoPlayer
import com.reader.android.ui.components.formatNumber
import com.reader.android.ui.components.formatTimeAgo
import com.reader.android.ui.components.parseRedditLink
import coil3.compose.AsyncImage
import com.reader.shared.domain.model.Comment
import com.reader.shared.domain.model.CommentSort
import com.reader.shared.domain.model.FlairRichtext
import com.reader.shared.domain.model.MoreComments
import com.reader.shared.domain.model.Post
import com.reader.shared.domain.model.VoteState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    subreddit: String,
    postId: String,
    onBackClick: () -> Unit,
    onSubredditClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onLinkClick: (String) -> Unit = {},
    viewModel: PostDetailViewModel = koinViewModel { parametersOf(subreddit, postId) }
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var showCommentSortSheet by remember { mutableStateOf(false) }
    var deleteConfirmCommentId by remember { mutableStateOf<String?>(null) }
    var showLoadDraftDialog by remember { mutableStateOf(false) }
    var pendingDraftText by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var pendingDismissAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDiscardDraftOption by remember { mutableStateOf(false) }
    var imageViewerUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var imageViewerInitialPage by remember { mutableStateOf(0) }

    val isReplyBarOpen = uiState.isLoggedIn && (uiState.editingCommentId != null || uiState.replyingTo != null)
    BackHandler(enabled = isReplyBarOpen) {
        if (uiState.editingCommentId != null) {
            val draft = uiState.savedDraftText
            if (uiState.replyText != uiState.editingOriginalText && uiState.replyText != (draft ?: "")) {
                pendingDismissAction = { viewModel.cancelEdit() }
                showDiscardDraftOption = true
                showDiscardDialog = true
            } else {
                viewModel.cancelEdit()
            }
        } else if (uiState.replyingTo != null) {
            val draft = uiState.savedDraftText
            if (uiState.replyText.isNotBlank() && uiState.replyText != (draft ?: "")) {
                pendingDismissAction = { viewModel.setReplyingTo(null) }
                showDiscardDraftOption = true
                showDiscardDialog = true
            } else {
                viewModel.setReplyingTo(null)
            }
        }
    }

    val flattenedComments = remember(uiState.comments, uiState.hiddenCommentIds) {
        viewModel.getFlattenedComments()
    }

    fun scrollToComment(targetCommentId: String) {
        coroutineScope.launch {
            val currentSelectedId = uiState.selectedCommentId
            var currentOffset = 0
            if (currentSelectedId != null) {
                val currentItemInfo = listState.layoutInfo.visibleItemsInfo.find {
                    it.key == currentSelectedId
                }
                if (currentItemInfo != null) {
                    currentOffset = currentItemInfo.offset
                }
            }

            viewModel.selectComment(targetCommentId)

            // Wait for recomposition and layout to reflect the new selection
            // (control bars appear/disappear, changing item heights)
            snapshotFlow { listState.layoutInfo }.drop(1).first()

            val newTargetInfo = listState.layoutInfo.visibleItemsInfo.find {
                it.key == targetCommentId
            }

            if (newTargetInfo != null) {
                val scrollAmount = newTargetInfo.offset - currentOffset
                if (scrollAmount != 0) {
                    listState.scroll { scrollBy(scrollAmount.toFloat()) }
                }
            } else {
                val updatedFlat = viewModel.getFlattenedComments()
                val targetIndex = updatedFlat.indexOfFirst {
                    it is FlatCommentItem.CommentEntry && it.comment.id == targetCommentId
                }
                if (targetIndex >= 0) {
                    val lazyIndex = targetIndex + 2 // +2 for PostHeader and Divider
                    listState.scrollToItem(lazyIndex, 0)
                    if (currentOffset > 0) {
                        listState.scroll { scrollBy(-currentOffset.toFloat()) }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comments") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadPostWithComments() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.isLoggedIn && uiState.editingCommentId != null) {
                val draft = uiState.savedDraftText
                val hasDraft = draft != null
                ReplyBar(
                    replyText = uiState.replyText,
                    onReplyTextChange = viewModel::setReplyText,
                    onSubmit = viewModel::submitEdit,
                    onCancel = {
                        if (uiState.replyText != uiState.editingOriginalText && uiState.replyText != (draft ?: "")) {
                            pendingDismissAction = { viewModel.cancelEdit() }
                            showDiscardDraftOption = true
                            showDiscardDialog = true
                        } else {
                            viewModel.cancelEdit()
                        }
                    },
                    onSaveDraft = {
                        viewModel.saveDraft()
                        Toast.makeText(context, "Draft saved", Toast.LENGTH_SHORT).show()
                    },
                    onLoadDraft = {
                        if (draft != null) {
                            if (uiState.replyText.isNotBlank() && uiState.replyText != draft) {
                                pendingDraftText = draft
                                showLoadDraftDialog = true
                            } else {
                                viewModel.applyDraft(draft)
                            }
                        }
                    },
                    hasDraft = hasDraft,
                    showDraftControls = true,
                    placeholder = "Edit comment..."
                )
            } else if (uiState.isLoggedIn && uiState.replyingTo != null) {
                val draft = uiState.savedDraftText
                val hasDraft = draft != null
                ReplyBar(
                    replyText = uiState.replyText,
                    onReplyTextChange = viewModel::setReplyText,
                    onSubmit = viewModel::submitReply,
                    onCancel = {
                        if (uiState.replyText.isNotBlank() && uiState.replyText != (draft ?: "")) {
                            pendingDismissAction = { viewModel.setReplyingTo(null) }
                            showDiscardDraftOption = true
                            showDiscardDialog = true
                        } else {
                            viewModel.setReplyingTo(null)
                        }
                    },
                    onSaveDraft = {
                        viewModel.saveDraft()
                        Toast.makeText(context, "Draft saved", Toast.LENGTH_SHORT).show()
                    },
                    onLoadDraft = {
                        if (draft != null) {
                            if (uiState.replyText.isNotBlank() && uiState.replyText != draft) {
                                pendingDraftText = draft
                                showLoadDraftDialog = true
                            } else {
                                viewModel.applyDraft(draft)
                            }
                        }
                    },
                    hasDraft = hasDraft,
                    showDraftControls = true
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading && uiState.post == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.error != null && uiState.post == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(uiState.error ?: "Unknown error")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadPostWithComments() }) {
                        Text("Retry")
                    }
                }
            } else {
                uiState.post?.let { post ->
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            PostHeader(
                                post = post,
                                onSubredditClick = { onSubredditClick(post.subreddit) },
                                onUserClick = { onUserClick(post.author) },
                                onUpvote = { viewModel.votePost(if (post.likes == true) 0 else 1) },
                                onDownvote = { viewModel.votePost(if (post.likes == false) 0 else -1) },
                                onSave = { viewModel.savePost() },
                                onSortClick = { showCommentSortSheet = true },
                                onReply = { viewModel.setReplyingTo(post.name) },
                                isLoggedIn = uiState.isLoggedIn,
                                onLinkClick = onLinkClick,
                                onImageClick = { urls, page ->
                                    imageViewerUrls = urls
                                    imageViewerInitialPage = page
                                }
                            )
                        }

                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        if (uiState.isLoading && uiState.comments.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }

                        items(
                            items = flattenedComments,
                            key = { item ->
                                when (item) {
                                    is FlatCommentItem.CommentEntry -> item.comment.id
                                    is FlatCommentItem.MoreEntry -> "more_${item.more.id}"
                                }
                            }
                        ) { item ->
                            when (item) {
                                is FlatCommentItem.CommentEntry -> {
                                    val comment = item.comment
                                    val isSelected = uiState.selectedCommentId == comment.id
                                    val isHidden = uiState.hiddenCommentIds.contains(comment.id)
                                    CommentItem(
                                        comment = comment,
                                        isSelected = isSelected,
                                        isHidden = isHidden,
                                        onSelect = { viewModel.selectComment(comment.id) },
                                        onDone = { viewModel.selectComment(null) },
                                        onHide = { viewModel.hideComment(comment.id) },
                                        onPrev = {
                                            viewModel.findPrevRootCommentId(comment.id)?.let { scrollToComment(it) }
                                        },
                                        onNext = {
                                            viewModel.findNextRootCommentId(comment.id)?.let { scrollToComment(it) }
                                        },
                                        onRoot = {
                                            viewModel.findRootCommentId(comment.id)?.let { rootId ->
                                                if (rootId != comment.id) scrollToComment(rootId)
                                            }
                                        },
                                        onParent = {
                                            viewModel.findParentCommentId(comment.id)?.let { scrollToComment(it) }
                                        },
                                        onUserClick = onUserClick,
                                        onUpvote = { viewModel.voteComment(comment, if (comment.likes == true) 0 else 1) },
                                        onDownvote = { viewModel.voteComment(comment, if (comment.likes == false) 0 else -1) },
                                        onShare = {
                                            val link = "https://www.reddit.com${comment.permalink}"
                                            clipboardManager.setText(AnnotatedString(link))
                                            Toast.makeText(context, "Copied link: $link", Toast.LENGTH_SHORT).show()
                                        },
                                        onReply = { viewModel.setReplyingTo(comment.name) },
                                        onEdit = { viewModel.startEditComment(comment) },
                                        onDelete = { deleteConfirmCommentId = comment.id },
                                        isLoggedIn = uiState.isLoggedIn,
                                        loggedInUsername = uiState.loggedInUsername,
                                        onLinkClick = { url ->
                                            when (val link = parseRedditLink(url)) {
                                                is RedditLink.Subreddit -> onSubredditClick(link.name)
                                                is RedditLink.User -> onUserClick(link.name)
                                                is RedditLink.Post -> onLinkClick(url)
                                                is RedditLink.External -> onLinkClick(url)
                                            }
                                        }
                                    )
                                }
                                is FlatCommentItem.MoreEntry -> {
                                    MoreCommentsButton(
                                        more = item.more,
                                        onClick = { viewModel.loadMoreComments(item.more) },
                                        isLoading = uiState.loadingMoreId == item.more.id
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }

    if (imageViewerUrls.isNotEmpty()) {
        FullScreenImageViewer(
            imageUrls = imageViewerUrls,
            initialPage = imageViewerInitialPage,
            onDismiss = { imageViewerUrls = emptyList() }
        )
    }

    if (showCommentSortSheet) {
        CommentSortBottomSheet(
            currentSort = uiState.commentSort,
            onSortSelected = { sort ->
                viewModel.setCommentSort(sort)
                showCommentSortSheet = false
            },
            onDismiss = { showCommentSortSheet = false }
        )
    }

    if (deleteConfirmCommentId != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmCommentId = null },
            title = { Text("Delete Comment") },
            text = { Text("Are you sure you want to delete this comment? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfirmCommentId?.let { viewModel.deleteComment(it) }
                    deleteConfirmCommentId = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmCommentId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLoadDraftDialog) {
        AlertDialog(
            onDismissRequest = { showLoadDraftDialog = false },
            title = { Text("Load Draft") },
            text = { Text("Loading the draft will overwrite your current text. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDraftText?.let { viewModel.applyDraft(it) }
                    pendingDraftText = null
                    showLoadDraftDialog = false
                }) {
                    Text("Load Draft")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDraftText = null
                    showLoadDraftDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = {
                pendingDismissAction = null
                showDiscardDraftOption = false
                showDiscardDialog = false
            },
            title = { Text("Discard Changes") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
            confirmButton = {
                Row {
                    if (showDiscardDraftOption) {
                        TextButton(onClick = {
                            viewModel.saveDraft()
                            pendingDismissAction?.invoke()
                            pendingDismissAction = null
                            showDiscardDraftOption = false
                            showDiscardDialog = false
                            Toast.makeText(context, "Draft saved", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Save Draft")
                        }
                    }
                    TextButton(onClick = {
                        pendingDismissAction?.invoke()
                        pendingDismissAction = null
                        showDiscardDraftOption = false
                        showDiscardDialog = false
                    }) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDismissAction = null
                    showDiscardDraftOption = false
                    showDiscardDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentSortBottomSheet(
    currentSort: CommentSort,
    onSortSelected: (CommentSort) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Sort comments by",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            CommentSort.entries.forEach { sort ->
                val isActive = currentSort == sort
                ListItem(
                    headlineContent = {
                        Text(
                            sort.displayName,
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    trailingContent = if (isActive) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null,
                    modifier = Modifier.clickable { onSortSelected(sort) }
                )
            }
        }
    }
}

private val POST_DETAIL_IMAGE_MAX_HEIGHT = 400.dp

@Composable
private fun PostHeader(
    post: Post,
    onSubredditClick: () -> Unit,
    onUserClick: () -> Unit,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onSave: () -> Unit,
    onSortClick: () -> Unit,
    onReply: () -> Unit,
    isLoggedIn: Boolean,
    onLinkClick: (String) -> Unit = {},
    onImageClick: (urls: List<String>, initialPage: Int) -> Unit = { _, _ -> }
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "r/${post.subreddit}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onSubredditClick)
            )
            Text(" • ", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (post.isNsfw) FlairChip("NSFW", Color(0xFFFF4444))
            if (post.isSpoiler) FlairChip("Spoiler", Color(0xFF888888))
            post.linkFlairText?.let { FlairChip(it, Color.Gray) }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = post.title,
            style = MaterialTheme.typography.titleLarge
        )

        val galleryItems = post.galleryData?.items?.filter { it.url != null } ?: emptyList()
        if (galleryItems.size > 1 && !post.isNsfw) {
            Spacer(modifier = Modifier.height(12.dp))
            val pagerState = rememberPagerState(pageCount = { galleryItems.size })
            val galleryUrls = remember(galleryItems) { galleryItems.mapNotNull { it.url } }
            Column {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(POST_DETAIL_IMAGE_MAX_HEIGHT)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onImageClick(galleryUrls, pagerState.currentPage) }
                ) { page ->
                    AsyncImage(
                        model = galleryItems[page].url,
                        contentDescription = galleryItems[page].caption,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(galleryItems.size) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (index == pagerState.currentPage)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                        )
                    }
                }
            }
        } else {
            val redditVideo = post.media?.redditVideo
                ?: post.preview?.redditVideoPreview
            if (redditVideo != null && !post.isNsfw) {
                Spacer(modifier = Modifier.height(12.dp))
                VideoPlayer(
                    videoUrl = redditVideo.fallbackUrl,
                    isGif = redditVideo.isGif,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                val mp4Url = post.preview?.images?.firstOrNull()?.mp4Url
                if (mp4Url != null && !post.isNsfw) {
                    Spacer(modifier = Modifier.height(12.dp))
                    VideoPlayer(
                        videoUrl = mp4Url,
                        isGif = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val previewImage = post.preview?.images?.firstOrNull()
                    val galleryUrl = galleryItems.firstOrNull()?.url
                    val highResUrl = previewImage?.source?.url
                        ?: galleryUrl
                        ?: post.thumbnail?.takeIf { it.startsWith("http") }
                        ?: post.url.takeIf { post.isImagePost }
                    val lowResUrl = previewImage?.resolutions?.firstOrNull()?.url
                        ?: post.thumbnail?.takeIf { it.startsWith("http") }
                    if (highResUrl != null && !post.isNsfw) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ProgressiveAsyncImage(
                            lowResUrl = lowResUrl,
                            highResUrl = highResUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(POST_DETAIL_IMAGE_MAX_HEIGHT)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onImageClick(listOf(highResUrl), 0) },
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }

        post.selfText?.let { text ->
            if (text.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                MarkdownText(
                    markdown = text,
                    style = MaterialTheme.typography.bodyMedium,
                    onLinkClick = onLinkClick
                )
            }
        }

        if (post.isLinkPost) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = post.domain,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                    .padding(horizontal = 4.dp)
            ) {
                IconButton(onClick = onUpvote, enabled = isLoggedIn, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (post.voteState == VoteState.UPVOTED) Icons.Filled.KeyboardArrowUp else Icons.Outlined.KeyboardArrowUp,
                        contentDescription = "Upvote",
                        tint = if (post.voteState == VoteState.UPVOTED) Color(0xFFFF4500) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatNumber(post.score),
                    fontWeight = FontWeight.Bold,
                    color = when (post.voteState) {
                        VoteState.UPVOTED -> Color(0xFFFF4500)
                        VoteState.DOWNVOTED -> Color(0xFF7193FF)
                        VoteState.NONE -> MaterialTheme.colorScheme.onSurface
                    }
                )
                IconButton(onClick = onDownvote, enabled = isLoggedIn, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (post.voteState == VoteState.DOWNVOTED) Icons.Filled.KeyboardArrowDown else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Downvote",
                        tint = if (post.voteState == VoteState.DOWNVOTED) Color(0xFF7193FF) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "${formatNumber(post.numComments)} comments",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onSortClick) {
                Icon(Icons.Default.Sort, contentDescription = "Sort comments", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isLoggedIn) {
                IconButton(onClick = onSave) {
                    Icon(
                        if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Save",
                        tint = if (post.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onReply) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Reply")
                }
            }
        }
    }
}

@Composable
private fun CommentItem(
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
    onUserClick: (String) -> Unit,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onShare: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isLoggedIn: Boolean,
    loggedInUsername: String? = null,
    onLinkClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val depthColors = listOf(
        Color(0xFFFF4500),
        Color(0xFF0079D3),
        Color(0xFF46A508),
        Color(0xFFFFD635),
        Color(0xFF7193FF),
        Color(0xFFFF66AC)
    )
    val depthColor = depthColors[comment.depth % depthColors.size]
    val isRootComment = comment.depth == 0

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
                if (isSelected) {
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
                            if (isRootComment) {
                                TextButton(onClick = onPrev, modifier = Modifier.height(32.dp)) {
                                    Text("Prev", style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(onClick = onNext, modifier = Modifier.height(32.dp)) {
                                    Text("Next", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                TextButton(onClick = onRoot, modifier = Modifier.height(32.dp)) {
                                    Text("Root", style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(onClick = onParent, modifier = Modifier.height(32.dp)) {
                                    Text("Parent", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
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
                        modifier = Modifier.clickable { onUserClick(comment.author) }
                    )
                    if (comment.isSubmitter) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("OP", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0079D3))
                    }
                    val flairText = comment.authorFlairText
                    val flairColor = comment.authorFlairBackgroundColor?.let {
                        try { Color(AndroidColor.parseColor(it)) } catch (_: Exception) { MaterialTheme.colorScheme.secondary }
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

                    MarkdownText(
                        markdown = comment.body,
                        style = MaterialTheme.typography.bodyMedium,
                        onLinkClick = onLinkClick,
                        onTextClick = onSelect
                    )
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
                        IconButton(onClick = onUpvote, enabled = isLoggedIn, modifier = Modifier.size(36.dp)) {
                            Icon(
                                if (comment.voteState == VoteState.UPVOTED) Icons.Filled.KeyboardArrowUp else Icons.Outlined.KeyboardArrowUp,
                                contentDescription = "Upvote",
                                modifier = Modifier.size(24.dp),
                                tint = if (comment.voteState == VoteState.UPVOTED) Color(0xFFFF4500) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDownvote, enabled = isLoggedIn, modifier = Modifier.size(36.dp)) {
                            Icon(
                                if (comment.voteState == VoteState.DOWNVOTED) Icons.Filled.KeyboardArrowDown else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Downvote",
                                modifier = Modifier.size(24.dp),
                                tint = if (comment.voteState == VoteState.DOWNVOTED) Color(0xFF7193FF) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "Share",
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

@Composable
private fun RichFlairChip(
    richtext: List<FlairRichtext>,
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
                            AsyncImage(
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

@Composable
private fun MoreCommentsButton(
    more: MoreComments,
    onClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier.padding(start = (more.depth * 12).dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text("Load ${more.count} more ${if (more.count == 1) "reply" else "replies"}")
    }
}

@Composable
private fun ReplyBar(
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    placeholder: String = "Write a reply...",
    onSaveDraft: () -> Unit = {},
    onLoadDraft: () -> Unit = {},
    hasDraft: Boolean = false,
    showDraftControls: Boolean = false
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
                OutlinedTextField(
                    value = replyText,
                    onValueChange = onReplyTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(placeholder) },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                IconButton(
                    onClick = onSubmit,
                    enabled = replyText.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
            if (showDraftControls) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasDraft) {
                        TextButton(onClick = onLoadDraft) {
                            Text("Load Draft", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    TextButton(
                        onClick = onSaveDraft,
                        enabled = replyText.isNotBlank()
                    ) {
                        Text("Save Draft", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
