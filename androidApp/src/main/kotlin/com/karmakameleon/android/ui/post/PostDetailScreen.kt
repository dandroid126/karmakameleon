package com.karmakameleon.android.ui.post

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.karmakameleon.android.data.PendingQuote
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.android.ui.components.CommentActions
import com.karmakameleon.android.ui.components.CommentItem
import com.karmakameleon.android.ui.components.FlairChip
import com.karmakameleon.android.ui.components.FullScreenImageViewer
import com.karmakameleon.android.ui.components.LongPressMenuBox
import com.karmakameleon.android.ui.components.MarkdownText
import com.karmakameleon.android.ui.components.ProgressiveAsyncImage
import com.karmakameleon.android.ui.components.ReplyBar
import com.karmakameleon.android.ui.components.SubredditFlairChip
import com.karmakameleon.android.ui.components.UniversalTopAppBar
import com.karmakameleon.android.ui.components.VideoPlayer
import com.karmakameleon.android.ui.components.YouTubePlayer
import com.karmakameleon.android.ui.components.formatNumber
import com.karmakameleon.android.ui.components.formatTimeAgo
import com.karmakameleon.android.ui.components.gifMenuItems
import com.karmakameleon.android.ui.components.imageMenuItems
import com.karmakameleon.android.ui.components.showShortToast
import com.karmakameleon.android.ui.components.videoMenuItems
import com.karmakameleon.android.ui.theme.postFlairColors
import com.karmakameleon.android.ui.theme.voteColors
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.domain.model.CommentSort
import com.karmakameleon.shared.domain.model.MoreComments
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.VoteState
import com.karmakameleon.shared.ui.comment.CommentUiState
import com.karmakameleon.shared.ui.comment.FlatCommentItem
import com.karmakameleon.shared.ui.post.PostDetailTarget
import com.karmakameleon.shared.ui.post.PostDetailUiState
import com.karmakameleon.shared.ui.post.PostDetailViewModel
import com.karmakameleon.shared.util.extractYouTubeVideoId
import kotlinx.coroutines.CoroutineScope
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
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    commentId: String? = null,
    commentContext: Int? = null,
    onGoToCommentNav: (commentId: String) -> Unit = {},
    viewModel: PostDetailViewModel = koinViewModel { parametersOf(PostDetailTarget(subreddit, postId, commentId, commentContext)) },
    settingsRepository: SettingsRepository = koinInject(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val commentState by viewModel.commentViewModel.uiState.collectAsState()
    val inlineImagesEnabled by settingsRepository.inlineImagesEnabled.collectAsState()
    val nsfwEnabled by settingsRepository.nsfwEnabled.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val screen = rememberPostDetailScreenState()
    var isBodyExpanded by rememberSaveable { mutableStateOf(true) }

    ShowActionErrors(uiState.actionError, onConsumeError = viewModel::clearActionError)
    ConsumePendingQuotes { text -> viewModel.handleQuote(text, commentState) }
    val replyActions = viewModel.replyActions(commentState, screen.draftDialogs, context)
    val isReplying = commentState.editingCommentId != null || commentState.replyingTo != null
    BackHandler(enabled = uiState.isLoggedIn && isReplying, onBack = replyActions.requestClose)

    Scaffold(
        modifier = modifier,
        topBar = { PostDetailTopBar(onBackClick = onBackClick) },
        bottomBar = {
            if (uiState.isLoggedIn) PostReplyBar(commentState = commentState, actions = replyActions)
        },
    ) { padding ->
        PostDetailBody(
            content =
                ThreadContent(
                    uiState = uiState,
                    commentState = commentState,
                    flattenedComments =
                        remember(
                            commentState.comments,
                            commentState.hiddenCommentIds,
                        ) { viewModel.commentViewModel.getFlattenedComments() },
                    renderInlineImages = inlineImagesEnabled,
                ),
            padding = padding,
            screen = screen,
            isBodyExpanded = isBodyExpanded,
            headerActions = viewModel.headerActions(uiState.post, screen, onToggleBodyExpanded = { isBodyExpanded = !isBodyExpanded }),
            threadComments = viewModel.threadComments(screen, isSingleThread = uiState.focusedCommentId != null, clipboard, context),
            listActions = viewModel.listActions(onGoToCommentNav),
            nsfwEnabled = nsfwEnabled,
        )
    }

    PostDetailOverlays(
        screen = screen,
        uiState = uiState,
        onUseSuggestedSortChange = viewModel::setUseSuggestedSort,
        onSelectSort = viewModel::setCommentSort,
        onDeleteComment = viewModel.commentViewModel::deleteComment,
    )
    ReplyDraftDialogs(
        state = screen.draftDialogs,
        onSaveDraft = replyActions.saveDraft,
        onApplyDraft = viewModel.commentViewModel::applyDraft,
    )
}

// The screen's own UI state, apart from whether the post body is expanded, which survives configuration changes.
@Stable
private class PostDetailScreenState(
    val listState: LazyListState,
    val coroutineScope: CoroutineScope,
) {
    val draftDialogs = ReplyDraftDialogState()
    val selection = TextSelectionTracker()
    var showCommentSortSheet by mutableStateOf(false)
    var deleteConfirmCommentId by mutableStateOf<String?>(null)
    var imageViewer by mutableStateOf<ImageViewerRequest?>(null)

    fun openImages(
        urls: List<String>,
        initialPage: Int = 0,
    ) {
        imageViewer = ImageViewerRequest(urls, initialPage)
    }
}

@Composable
private fun rememberPostDetailScreenState(): PostDetailScreenState {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    return remember { PostDetailScreenState(listState, coroutineScope) }
}

@Composable
private fun PostDetailTopBar(onBackClick: () -> Unit) {
    UniversalTopAppBar(
        title = { Text("Comments") },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
}

private fun PostDetailViewModel.replyActions(
    commentState: CommentUiState,
    dialogs: ReplyDraftDialogState,
    context: Context,
) = ReplyActions(
    onReplyTextChange = commentViewModel::setReplyText,
    onSubmitEdit = commentViewModel::submitEdit,
    onSubmitReply = ::submitReply,
    onQuote = ::insertQuote,
    requestClose = {
        dialogs.requestClose(commentState.hasUnsavedReply) {
            if (commentState.editingCommentId != null) commentViewModel.cancelEdit() else commentViewModel.setReplyingTo(null)
        }
    },
    saveDraft = {
        commentViewModel.saveDraft()
        showShortToast(context, "Draft saved")
    },
    loadDraft = { draft -> dialogs.requestLoadDraft(draft, commentState.replyText, commentViewModel::applyDraft) },
)

// Voting again in the same direction removes the vote.
private fun PostDetailViewModel.headerActions(
    post: Post?,
    screen: PostDetailScreenState,
    onToggleBodyExpanded: () -> Unit,
) = PostHeaderActions(
    onToggleBodyExpanded = onToggleBodyExpanded,
    onUpvote = { votePost(if (post?.likes == true) 0 else 1) },
    onDownvote = { votePost(if (post?.likes == false) 0 else -1) },
    onSave = { savePost() },
    onSortClick = { screen.showCommentSortSheet = true },
    onReply = { post?.let { commentViewModel.setReplyingTo(it.name) } },
    onImageClick = { urls, page -> screen.openImages(urls, page) },
    onInlineImageClick = { url -> screen.openImages(listOf(url)) },
    onTouchStart = {
        screen.selection.onSelectableTouched()
        commentViewModel.setLastTouchedComment(null)
    },
)

private fun PostDetailViewModel.threadComments(
    screen: PostDetailScreenState,
    isSingleThread: Boolean,
    clipboard: Clipboard,
    context: Context,
) = ThreadComments(
    viewModel = this,
    isSingleThread = isSingleThread,
    scrollTo = { targetId ->
        screen.coroutineScope.launch {
            screen.listState.scrollToSelectedComment(
                targetCommentId = targetId,
                previousSelectedId = commentViewModel.uiState.value.selectedCommentId,
                select = commentViewModel::selectComment,
                flattenedComments = commentViewModel::getFlattenedComments,
            )
        }
    },
    onCopyLink = { link -> screen.coroutineScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", link))) } },
    onConfirmDelete = { screen.deleteConfirmCommentId = it },
    onSaved = { comment -> showShortToast(context, if (comment.isSaved) "Unsaved comment" else "Saved comment") },
)

// Puts quoted text into the reply in progress, or starts a reply with it to the last touched comment (or the post).
private fun PostDetailViewModel.handleQuote(
    text: String,
    commentState: CommentUiState,
) {
    val post = uiState.value.post ?: return
    if (commentState.replyingTo != null || commentState.editingCommentId != null) {
        commentViewModel.insertQuote(text)
    } else {
        commentViewModel.startReplyWithQuote(commentState.lastTouchedCommentName ?: post.name, text)
    }
}

// Text quoted from another app with the Quote text action arrives through PendingQuote.
@Composable
private fun ConsumePendingQuotes(onQuote: (String) -> Unit) {
    val currentOnQuote by rememberUpdatedState(onQuote)
    LaunchedEffect(Unit) {
        PendingQuote.text.collect { text ->
            if (text != null) {
                PendingQuote.consume()
                currentOnQuote(text)
            }
        }
    }
}

@Composable
private fun ShowActionErrors(
    actionError: String?,
    onConsumeError: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnConsumeError by rememberUpdatedState(onConsumeError)
    SideEffect(actionError) {
        actionError?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            currentOnConsumeError()
        }
    }
}

// Images to show full screen, starting at one of them.
private class ImageViewerRequest(
    val urls: List<String>,
    val initialPage: Int,
)

// The image viewer, comment sort sheet and delete confirmation, when they're open.
@Composable
private fun PostDetailOverlays(
    screen: PostDetailScreenState,
    uiState: PostDetailUiState,
    onUseSuggestedSortChange: (Boolean) -> Unit,
    onSelectSort: (CommentSort) -> Unit,
    onDeleteComment: (String) -> Unit,
) {
    screen.imageViewer?.let {
        FullScreenImageViewer(imageUrls = it.urls, initialPage = it.initialPage, onDismiss = { screen.imageViewer = null })
    }
    if (screen.showCommentSortSheet) {
        CommentSortBottomSheet(
            currentSort = uiState.commentSort,
            suggestedSort = uiState.suggestedSort,
            useSuggestedSort = uiState.useSuggestedSort,
            onUseSuggestedSortChange = onUseSuggestedSortChange,
            onSelectSort = {
                onSelectSort(it)
                screen.showCommentSortSheet = false
            },
            onDismiss = { screen.showCommentSortSheet = false },
        )
    }
    screen.deleteConfirmCommentId?.let { commentId ->
        AlertDialog(
            onDismissRequest = { screen.deleteConfirmCommentId = null },
            title = { Text("Delete Comment") },
            text = { Text("Are you sure you want to delete this comment? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteComment(commentId)
                    screen.deleteConfirmCommentId = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { screen.deleteConfirmCommentId = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// Clears the text selection when a tap lands outside selectable text. Every touch first passes through here at the
// Initial pass and resets touchedSelectable; selectable text then sets it again. At the Main pass, a tap that didn't
// touch selectable text after one that did bumps version, which SelectableArea uses to drop its selection.
@Stable
private class TextSelectionTracker {
    var version by mutableIntStateOf(0)
        private set
    private var touchedSelectable = false
    private var selectionMayBeActive = false

    fun onSelectableTouched() {
        touchedSelectable = true
    }

    fun Modifier.clearSelectionOnOutsideTap(): Modifier =
        pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { it.pressed && !it.previousPressed }) touchedSelectable = false
                }
            }
        }.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.changes.any { it.pressed && !it.previousPressed }) onTapReachedMainPass()
                }
            }
        }

    private fun onTapReachedMainPass() {
        if (touchedSelectable) {
            selectionMayBeActive = true
        } else if (selectionMayBeActive) {
            version++
            selectionMayBeActive = false
        }
    }
}

// What the comments in the thread do. It wraps the view models so the composables below don't take them.
private class ThreadComments(
    private val viewModel: PostDetailViewModel,
    private val isSingleThread: Boolean,
    private val scrollTo: (commentId: String) -> Unit,
    private val onCopyLink: (String) -> Unit,
    private val onConfirmDelete: (commentId: String) -> Unit,
    private val onSaved: (Comment) -> Unit,
) {
    private val comments get() = viewModel.commentViewModel

    fun select(comment: Comment) = comments.selectComment(comment.id)

    fun deselect() = comments.selectComment(null)

    fun hide(comment: Comment) = comments.hideComment(comment.id)

    fun update(comment: Comment) = comments.updateComment(comment)

    fun reply(comment: Comment) = comments.setReplyingTo(comment.name)

    fun edit(comment: Comment) = comments.startEditComment(comment)

    fun delete(comment: Comment) = onConfirmDelete(comment.id)

    fun share(comment: Comment) = onCopyLink("https://www.reddit.com${comment.permalink}")

    fun save(comment: Comment) {
        comments.saveComment(comment)
        onSaved(comment)
    }

    fun touched(comment: Comment?) = comments.setLastTouchedComment(comment?.name)

    fun previous(comment: Comment) {
        comments.findPrevRootCommentId(comment.id)?.let(scrollTo)
    }

    fun next(comment: Comment) {
        comments.findNextRootCommentId(comment.id)?.let(scrollTo)
    }

    // In a single thread, a root or parent that isn't loaded is fetched instead.
    fun root(comment: Comment) {
        val rootId = comments.findRootCommentId(comment.id)
        if (rootId != null && rootId != comment.id) {
            scrollTo(rootId)
        } else if (isSingleThread && comment.parentId.startsWith("t1_")) {
            viewModel.navigateToParentRoot(comment.id)
        }
    }

    fun parent(comment: Comment) {
        val parentId = comments.findParentCommentId(comment.id)
        if (parentId != null) {
            scrollTo(parentId)
        } else if (isSingleThread && comment.parentId.startsWith("t1_")) {
            viewModel.navigateToComment(comment.parentId.removePrefix("t1_"))
        }
    }
}

// Selects a comment and scrolls so it lands where the previously selected comment was. Selecting changes item
// heights (control bars appear and disappear), so this waits for the next layout before measuring.
private suspend fun LazyListState.scrollToSelectedComment(
    targetCommentId: String,
    previousSelectedId: String?,
    select: (String) -> Unit,
    flattenedComments: () -> List<FlatCommentItem>,
) {
    val currentOffset = previousSelectedId?.let { id -> layoutInfo.visibleItemsInfo.find { it.key == id }?.offset } ?: 0
    select(targetCommentId)
    snapshotFlow { layoutInfo }.drop(1).first()

    val newTargetInfo = layoutInfo.visibleItemsInfo.find { it.key == targetCommentId }
    if (newTargetInfo != null) {
        val scrollAmount = newTargetInfo.offset - currentOffset
        if (scrollAmount != 0) scroll { scrollBy(scrollAmount.toFloat()) }
        return
    }
    val targetIndex = flattenedComments().indexOfFirst { it is FlatCommentItem.CommentEntry && it.comment.id == targetCommentId }
    if (targetIndex >= 0) {
        scrollToItem(targetIndex + COMMENTS_FIRST_INDEX, 0)
        if (currentOffset > 0) scroll { scrollBy(-currentOffset.toFloat()) }
    }
}

// The post header and the divider come before the comments in the list.
private const val COMMENTS_FIRST_INDEX = 2

// What the post and its comments show.
private class ThreadContent(
    val uiState: PostDetailUiState,
    val commentState: CommentUiState,
    val flattenedComments: List<FlatCommentItem>,
    val renderInlineImages: Boolean,
)

private fun PostDetailViewModel.listActions(onGoToCommentNav: (commentId: String) -> Unit) =
    PostDetailListActions(
        onRefresh = { loadPostWithComments(forceRefresh = true) },
        onRetry = { loadPostWithComments() },
        onViewAllComments = ::viewAllComments,
        onLoadMoreComments = ::loadMoreComments,
        onGoToCommentNav = onGoToCommentNav,
    )

// What the list asks the view model to do.
private class PostDetailListActions(
    val onRefresh: () -> Unit,
    val onRetry: () -> Unit,
    val onViewAllComments: () -> Unit,
    val onLoadMoreComments: (MoreComments) -> Unit,
    val onGoToCommentNav: (commentId: String) -> Unit,
)

// A spinner or an error until the post loads, then the post and its comments, under pull to refresh.
@Composable
private fun PostDetailBody(
    content: ThreadContent,
    padding: PaddingValues,
    screen: PostDetailScreenState,
    isBodyExpanded: Boolean,
    headerActions: PostHeaderActions,
    threadComments: ThreadComments,
    listActions: PostDetailListActions,
    nsfwEnabled: Boolean,
) {
    val uiState = content.uiState
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = listActions.onRefresh,
        state = rememberPullToRefreshState(),
        modifier =
            Modifier
                .fillMaxSize()
                .padding(padding),
    ) {
        val post = uiState.post
        Box(modifier = with(screen.selection) { Modifier.fillMaxSize().clearSelectionOnOutsideTap() }) {
            when {
                uiState.isLoading && post == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.error != null && post == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(uiState.error ?: "Unknown error")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = listActions.onRetry) {
                            Text("Retry")
                        }
                    }
                }

                post != null -> {
                    LazyColumn(state = screen.listState, modifier = Modifier.fillMaxSize()) {
                        item {
                            PostHeader(
                                post = post,
                                isBodyExpanded = isBodyExpanded,
                                isLoggedIn = uiState.isLoggedIn,
                                actions = headerActions,
                                selectionVersion = screen.selection.version,
                                renderInlineImages = content.renderInlineImages,
                                nsfwEnabled = nsfwEnabled,
                            )
                        }
                        commentItems(content = content, screen = screen, threadComments = threadComments, listActions = listActions)
                    }
                }
            }
        }
    }
}

// The comments under the post header: a way back to all comments in a single thread, a spinner while they load,
// the comments and "more" entries, and room at the end for the reply bar.
private fun LazyListScope.commentItems(
    content: ThreadContent,
    screen: PostDetailScreenState,
    threadComments: ThreadComments,
    listActions: PostDetailListActions,
) {
    val uiState = content.uiState
    val commentState = content.commentState
    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
    if (uiState.focusedCommentId != null) {
        item {
            TextButton(
                onClick = listActions.onViewAllComments,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
            ) {
                Text("View all comments")
            }
        }
    }
    if (uiState.isLoading && commentState.comments.isEmpty()) {
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
    items(
        items = content.flattenedComments,
        key = { item ->
            when (item) {
                is FlatCommentItem.CommentEntry -> item.comment.id
                is FlatCommentItem.MoreEntry -> "more_${item.more.id}"
            }
        },
    ) { item ->
        when (item) {
            is FlatCommentItem.CommentEntry -> {
                ThreadComment(
                    comment = item.comment,
                    isSelected = commentState.selectedCommentId == item.comment.id,
                    isHidden = item.comment.id in commentState.hiddenCommentIds,
                    isSingleThread = uiState.focusedCommentId != null,
                    isLoggedIn = uiState.isLoggedIn,
                    loggedInUsername = uiState.loggedInUsername,
                    threadComments = threadComments,
                    screen = screen,
                    renderInlineImages = content.renderInlineImages,
                    onGoToCommentNav = listActions.onGoToCommentNav,
                )
            }

            is FlatCommentItem.MoreEntry -> {
                MoreCommentsButton(
                    more = item.more,
                    onClick = { listActions.onLoadMoreComments(item.more) },
                    isLoading = commentState.loadingMoreId == item.more.id,
                )
            }
        }
    }
    item { Spacer(modifier = Modifier.height(80.dp)) }
}

@Composable
private fun ThreadComment(
    comment: Comment,
    isSelected: Boolean,
    isHidden: Boolean,
    isSingleThread: Boolean,
    isLoggedIn: Boolean,
    loggedInUsername: String?,
    threadComments: ThreadComments,
    screen: PostDetailScreenState,
    renderInlineImages: Boolean,
    onGoToCommentNav: (commentId: String) -> Unit,
) {
    CommentItem(
        comment = comment,
        isSelected = isSelected,
        isHidden = isHidden,
        actions =
            CommentActions(
                onSelect = { threadComments.select(comment) },
                onDone = threadComments::deselect,
                onShare = { threadComments.share(comment) },
                onReply = { threadComments.reply(comment) },
                onEdit = { threadComments.edit(comment) },
                onDelete = { threadComments.delete(comment) },
                onSave = { threadComments.save(comment) },
                onCommentUpdated = threadComments::update,
                onGoToCommentNav = onGoToCommentNav,
                onHide = { threadComments.hide(comment) },
                onPrev = { threadComments.previous(comment) },
                onNext = { threadComments.next(comment) },
                onRoot = { threadComments.root(comment) },
                onParent = { threadComments.parent(comment) },
                onTouchStart = {
                    screen.selection.onSelectableTouched()
                    threadComments.touched(comment)
                },
                onInlineImageClick = { url -> screen.openImages(listOf(url)) },
            ),
        isLoggedIn = isLoggedIn,
        loggedInUsername = loggedInUsername,
        selectionVersion = screen.selection.version,
        renderInlineImages = renderInlineImages,
        isSingleThreadMode = isSingleThread,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentSortBottomSheet(
    currentSort: CommentSort,
    suggestedSort: CommentSort?,
    useSuggestedSort: Boolean,
    onUseSuggestedSortChange: (Boolean) -> Unit,
    onSelectSort: (CommentSort) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
        ) {
            SuggestedSortToggle(useSuggestedSort = useSuggestedSort, onToggle = onUseSuggestedSortChange)

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Sort comments by",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            CommentSort.entries.forEach { sort ->
                val isActive = currentSort == sort
                val isSuggested = sort == suggestedSort
                ListItem(
                    headlineContent = {
                        Text(
                            buildString {
                                append(sort.displayName)
                                if (isSuggested) append(" (suggested)")
                            },
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    trailingContent =
                        if (isActive) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            null
                        },
                    modifier = Modifier.clickable { onSelectSort(sort) },
                )
            }
        }
    }
}

@Composable
private fun SuggestedSortToggle(
    useSuggestedSort: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(!useSuggestedSort) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Use suggested sort",
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(
            checked = useSuggestedSort,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun MoreCommentsButton(
    more: MoreComments,
    onClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = (more.depth * 12).dp)
                .clickable(enabled = !isLoading, onClick = onClick)
                .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = "Load ${more.count} more ${if (more.count == 1) "reply" else "replies"}",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
