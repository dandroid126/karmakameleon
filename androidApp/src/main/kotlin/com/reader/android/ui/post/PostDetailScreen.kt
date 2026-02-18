package com.reader.android.ui.post

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.reader.android.data.PendingQuote
import com.reader.android.ui.components.CommentItem
import com.reader.android.ui.components.FlairChip
import com.reader.android.ui.components.FullScreenImageViewer
import com.reader.android.ui.components.MarkdownText
import com.reader.android.ui.components.ProgressiveAsyncImage
import com.reader.android.ui.components.ReplyBar
import com.reader.android.ui.components.VideoPlayer
import com.reader.android.ui.components.formatNumber
import com.reader.android.ui.components.formatTimeAgo
import com.reader.shared.data.repository.SettingsRepository
import com.reader.shared.domain.model.CommentSort
import com.reader.shared.domain.model.MoreComments
import com.reader.shared.domain.model.Post
import com.reader.shared.domain.model.VoteState
import com.reader.shared.ui.comment.FlatCommentItem
import com.reader.shared.ui.post.PostDetailViewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    subreddit: String,
    postId: String,
    commentId: String? = null,
    onBackClick: () -> Unit,
    onSubredditClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onLinkClick: (String) -> Unit = {},
    onGoToCommentNav: (commentId: String) -> Unit = {},
    viewModel: PostDetailViewModel = koinViewModel { parametersOf(subreddit, postId, commentId) }
) {
    val uiState by viewModel.uiState.collectAsState()
    val commentState by viewModel.commentViewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val settingsRepository: SettingsRepository = koinInject()
    val inlineImagesEnabled by settingsRepository.inlineImagesEnabled.collectAsState()

    var showCommentSortSheet by remember { mutableStateOf(false) }
    var deleteConfirmCommentId by remember { mutableStateOf<String?>(null) }
    var showLoadDraftDialog by remember { mutableStateOf(false) }
    var pendingDraftText by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var pendingDismissAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDiscardDraftOption by remember { mutableStateOf(false) }
    var imageViewerUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var imageViewerInitialPage by remember { mutableStateOf(0) }
    var selectionVersion by remember { mutableIntStateOf(0) }
    val touchedSelectable = remember { mutableStateOf(false) }
    val selectionMayBeActive = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        PendingQuote.text.collect { text ->
            if (text != null) {
                PendingQuote.consume()
                val post = uiState.post ?: return@collect
                if (commentState.replyingTo != null || commentState.editingCommentId != null) {
                    viewModel.commentViewModel.insertQuotedText(text)
                } else {
                    val parentId = commentState.lastTouchedCommentName ?: post.name
                    viewModel.commentViewModel.startReplyWithQuote(parentId, text)
                }
            }
        }
    }

    val isReplyBarOpen = uiState.isLoggedIn && (commentState.editingCommentId != null || commentState.replyingTo != null)
    BackHandler(enabled = isReplyBarOpen) {
        if (commentState.editingCommentId != null) {
            val draft = commentState.savedDraftText
            if (commentState.replyText != commentState.editingOriginalText && commentState.replyText != (draft ?: "")) {
                pendingDismissAction = { viewModel.commentViewModel.cancelEdit() }
                showDiscardDraftOption = true
                showDiscardDialog = true
            } else {
                viewModel.commentViewModel.cancelEdit()
            }
        } else if (commentState.replyingTo != null) {
            val draft = commentState.savedDraftText
            if (commentState.replyText.isNotBlank() && commentState.replyText != (draft ?: "")) {
                pendingDismissAction = { viewModel.commentViewModel.setReplyingTo(null) }
                showDiscardDraftOption = true
                showDiscardDialog = true
            } else {
                viewModel.commentViewModel.setReplyingTo(null)
            }
        }
    }

    val flattenedComments = remember(commentState.comments, commentState.hiddenCommentIds) {
        viewModel.commentViewModel.getFlattenedComments()
    }

    fun scrollToComment(targetCommentId: String) {
        coroutineScope.launch {
            val currentSelectedId = commentState.selectedCommentId
            var currentOffset = 0
            if (currentSelectedId != null) {
                val currentItemInfo = listState.layoutInfo.visibleItemsInfo.find {
                    it.key == currentSelectedId
                }
                if (currentItemInfo != null) {
                    currentOffset = currentItemInfo.offset
                }
            }

            viewModel.commentViewModel.selectComment(targetCommentId)

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
                val updatedFlat = viewModel.commentViewModel.getFlattenedComments()
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
            if (uiState.isLoggedIn && commentState.editingCommentId != null) {
                val draft = commentState.savedDraftText
                val hasDraft = draft != null
                ReplyBar(
                    replyText = commentState.replyText,
                    onReplyTextChange = viewModel.commentViewModel::setReplyText,
                    onSubmit = viewModel.commentViewModel::submitEdit,
                    onCancel = {
                        if (commentState.replyText != commentState.editingOriginalText && commentState.replyText != (draft ?: "")) {
                            pendingDismissAction = { viewModel.commentViewModel.cancelEdit() }
                            showDiscardDraftOption = true
                            showDiscardDialog = true
                        } else {
                            viewModel.commentViewModel.cancelEdit()
                        }
                    },
                    onSaveDraft = {
                        viewModel.commentViewModel.saveDraft()
                        Toast.makeText(context, "Draft saved", Toast.LENGTH_SHORT).show()
                    },
                    onLoadDraft = {
                        if (draft != null) {
                            if (commentState.replyText.isNotBlank() && commentState.replyText != draft) {
                                pendingDraftText = draft
                                showLoadDraftDialog = true
                            } else {
                                viewModel.commentViewModel.applyDraft(draft)
                            }
                        }
                    },
                    onQuote = viewModel::insertQuote,
                    hasDraft = hasDraft,
                    showDraftControls = true,
                    placeholder = "Edit comment..."
                )
            } else if (uiState.isLoggedIn && commentState.replyingTo != null) {
                val draft = commentState.savedDraftText
                val hasDraft = draft != null
                ReplyBar(
                    replyText = commentState.replyText,
                    onReplyTextChange = viewModel.commentViewModel::setReplyText,
                    onSubmit = viewModel::submitReply,
                    onCancel = {
                        if (commentState.replyText.isNotBlank() && commentState.replyText != (draft ?: "")) {
                            pendingDismissAction = { viewModel.commentViewModel.setReplyingTo(null) }
                            showDiscardDraftOption = true
                            showDiscardDialog = true
                        } else {
                            viewModel.commentViewModel.setReplyingTo(null)
                        }
                    },
                    onSaveDraft = {
                        viewModel.commentViewModel.saveDraft()
                        Toast.makeText(context, "Draft saved", Toast.LENGTH_SHORT).show()
                    },
                    onLoadDraft = {
                        if (draft != null) {
                            if (commentState.replyText.isNotBlank() && commentState.replyText != draft) {
                                pendingDraftText = draft
                                showLoadDraftDialog = true
                            } else {
                                viewModel.commentViewModel.applyDraft(draft)
                            }
                        }
                    },
                    onQuote = viewModel::insertQuote,
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
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.pressed && !it.previousPressed }) {
                                touchedSelectable.value = false
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (event.changes.any { it.pressed && !it.previousPressed }) {
                                if (touchedSelectable.value) {
                                    selectionMayBeActive.value = true
                                } else if (selectionMayBeActive.value) {
                                    selectionVersion++
                                    selectionMayBeActive.value = false
                                }
                            }
                        }
                    }
                }
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
                                onReply = { viewModel.commentViewModel.setReplyingTo(post.name) },
                                isLoggedIn = uiState.isLoggedIn,
                                onLinkClick = onLinkClick,
                                selectionVersion = selectionVersion,
                                onTouchStart = {
                                    touchedSelectable.value = true
                                    viewModel.commentViewModel.setLastTouchedComment(null)
                                },
                                onSubredditNameClick = onSubredditClick,
                                onUserNameClick = onUserClick,
                                onImageClick = { urls, page ->
                                    imageViewerUrls = urls
                                    imageViewerInitialPage = page
                                },
                                renderInlineImages = inlineImagesEnabled,
                                onInlineImageClick = { url ->
                                    imageViewerUrls = listOf(url)
                                    imageViewerInitialPage = 0
                                }
                            )
                        }

                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        if (uiState.focusedCommentId != null) {
                            item {
                                TextButton(
                                    onClick = { viewModel.viewAllComments() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    Text("View all comments")
                                }
                            }
                        }

                        if (uiState.isLoading && commentState.comments.isEmpty()) {
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
                                    val isSelected = commentState.selectedCommentId == comment.id
                                    val isHidden = commentState.hiddenCommentIds.contains(comment.id)
                                    val isSingleThread = uiState.focusedCommentId != null
                                    CommentItem(
                                        comment = comment,
                                        isSelected = isSelected,
                                        isHidden = isHidden,
                                        onSelect = { viewModel.commentViewModel.selectComment(comment.id) },
                                        onDone = { viewModel.commentViewModel.selectComment(null) },
                                        onHide = { viewModel.commentViewModel.hideComment(comment.id) },
                                        onPrev = {
                                            viewModel.commentViewModel.findPrevRootCommentId(comment.id)?.let { scrollToComment(it) }
                                        },
                                        onNext = {
                                            viewModel.commentViewModel.findNextRootCommentId(comment.id)?.let { scrollToComment(it) }
                                        },
                                        onRoot = {
                                            if (isSingleThread) {
                                                val rootId = viewModel.commentViewModel.findRootCommentId(comment.id)
                                                if (rootId != null && rootId != comment.id) {
                                                    scrollToComment(rootId)
                                                } else if (comment.parentId.startsWith("t1_")) {
                                                    // Root is not in current view, navigate to it
                                                    viewModel.navigateToParentRoot(comment.id)
                                                }
                                            } else {
                                                viewModel.commentViewModel.findRootCommentId(comment.id)?.let { rootId ->
                                                    if (rootId != comment.id) scrollToComment(rootId)
                                                }
                                            }
                                        },
                                        onParent = {
                                            if (isSingleThread) {
                                                val parentId = viewModel.commentViewModel.findParentCommentId(comment.id)
                                                if (parentId != null) {
                                                    scrollToComment(parentId)
                                                } else if (comment.parentId.startsWith("t1_")) {
                                                    // Parent is not in current view, navigate to it
                                                    val parentCommentId = comment.parentId.removePrefix("t1_")
                                                    viewModel.navigateToComment(parentCommentId)
                                                }
                                            } else {
                                                viewModel.commentViewModel.findParentCommentId(comment.id)?.let { scrollToComment(it) }
                                            }
                                        },
                                        onUserClick = onUserClick,
                                        onSubredditClick = onSubredditClick,
                                        onCommentUpdated = { updatedComment ->
                                            viewModel.commentViewModel.updateComment(updatedComment)
                                        },
                                        onShare = {
                                            val link = "https://www.reddit.com${comment.permalink}"
                                            clipboardManager.setText(AnnotatedString(link))
                                        },
                                        onReply = { viewModel.commentViewModel.setReplyingTo(comment.name) },
                                        onEdit = { viewModel.commentViewModel.startEditComment(comment) },
                                        onDelete = { deleteConfirmCommentId = comment.id },
                                        onSave = {
                                            viewModel.commentViewModel.saveComment(comment)
                                            Toast.makeText(
                                                context,
                                                if (comment.isSaved) "Unsaved comment" else "Saved comment",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        isLoggedIn = uiState.isLoggedIn,
                                        loggedInUsername = uiState.loggedInUsername,
                                        onLinkClick = onLinkClick,
                                        selectionVersion = selectionVersion,
                                        onTouchStart = {
                                            touchedSelectable.value = true
                                            viewModel.commentViewModel.setLastTouchedComment(comment.name)
                                        },
                                        renderInlineImages = inlineImagesEnabled,
                                        onInlineImageClick = { url ->
                                            imageViewerUrls = listOf(url)
                                            imageViewerInitialPage = 0
                                        },
                                        isSingleThreadMode = isSingleThread,
                                        onGoToComment = {
                                            viewModel.navigateToComment(comment.id)
                                        },
                                        onGoToCommentNav = { targetCommentId ->
                                            onGoToCommentNav(targetCommentId)
                                        }
                                    )
                                }
                                is FlatCommentItem.MoreEntry -> {
                                    MoreCommentsButton(
                                        more = item.more,
                                        onClick = { viewModel.loadMoreComments(item.more) },
                                        isLoading = commentState.loadingMoreId == item.more.id
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
                    deleteConfirmCommentId?.let { viewModel.commentViewModel.deleteComment(it) }
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
                    pendingDraftText?.let { viewModel.commentViewModel.applyDraft(it) }
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
                            viewModel.commentViewModel.saveDraft()
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
    onImageClick: (urls: List<String>, initialPage: Int) -> Unit = { _, _ -> },
    selectionVersion: Int = 0,
    onTouchStart: () -> Unit = {},
    renderInlineImages: Boolean = true,
    onInlineImageClick: (String) -> Unit = {},
    onSubredditNameClick: ((String) -> Unit)? = null,
    onUserNameClick: ((String) -> Unit)? = null
) {
    var isBodyExpanded by rememberSaveable { mutableStateOf(true) }
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

        Box(modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    onTouchStart()
                }
            }
        }) {
            key(selectionVersion) {
                SelectionContainer {
                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { isBodyExpanded = !isBodyExpanded }) {
                Icon(
                    imageVector = if (isBodyExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isBodyExpanded) "Collapse post body" else "Expand post body",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = isBodyExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
        Column {
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
                ) { page ->
                    val item = galleryItems[page]
                    if (item.isVideo && item.url != null) {
                        VideoPlayer(
                            videoUrl = item.url!!,
                            isGif = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = item.url,
                            contentDescription = item.caption,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    val imageItems = galleryItems.filter { !it.isVideo }
                                    val imageUrls = imageItems.mapNotNull { it.url }
                                    val imageIndex = imageItems.indexOf(item).coerceAtLeast(0)
                                    onImageClick(imageUrls, imageIndex)
                                },
                            contentScale = ContentScale.Fit
                        )
                    }
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
                                .clickable {
                                    if (post.isLinkPost) onLinkClick(post.url)
                                    else onImageClick(listOf(highResUrl), 0)
                                },
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }

        post.selfText?.let { text ->
            if (text.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            onTouchStart()
                        }
                    }
                }) {
                    key(selectionVersion) {
                        SelectionContainer {
                            MarkdownText(
                                markdown = text,
                                style = MaterialTheme.typography.bodyMedium,
                                onLinkClick = onLinkClick,
                                renderInlineImages = renderInlineImages,
                                onImageClick = onInlineImageClick,
                                onSubredditClick = onSubredditNameClick,
                                onUserClick = onUserNameClick
                            )
                        }
                    }
                }
            }
        }

        if (post.isLinkPost) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = post.domain,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onLinkClick(post.url) }
            )
        }
        } // end Column inside AnimatedVisibility
        } // end AnimatedVisibility

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

