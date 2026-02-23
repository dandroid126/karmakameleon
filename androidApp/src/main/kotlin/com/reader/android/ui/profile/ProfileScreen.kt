package com.reader.android.ui.profile

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.reader.android.navigation.NavigationHandler
import com.reader.android.ui.components.CommentItem
import com.reader.android.ui.components.PostCard
import com.reader.android.ui.components.ReplyBar
import com.reader.android.ui.components.SortBottomSheet
import com.reader.android.ui.components.formatNumber
import com.reader.android.ui.components.formatTimeAgo
import com.reader.shared.data.repository.ReadPostsRepository
import com.reader.shared.data.repository.UserRepository
import com.reader.shared.domain.model.Account
import com.reader.shared.domain.model.PostSort
import com.reader.shared.domain.model.TimeFilter
import com.reader.shared.domain.model.User
import com.reader.shared.ui.profile.ProfileTab
import com.reader.shared.ui.profile.ProfileViewModel
import com.reader.shared.ui.profile.SavedContentType
import com.reader.android.ui.components.UniversalTopAppBar
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    username: String? = null,
    currentRoute: String? = null,
    onBackClick: (() -> Unit)? = null,
    onPostClick: (subreddit: String, postId: String) -> Unit,
    onCommentClick: (subreddit: String, postId: String, commentId: String) -> Unit = { s, p, c -> onPostClick(s, p) },
    onSubredditClick: (String) -> Unit,
    onLinkClick: (String) -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
    userRepository: UserRepository = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val commentState by viewModel.commentViewModel.uiState.collectAsState()
    val currentAccount by userRepository.currentAccount.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var selectedCommentId by remember { mutableStateOf<String?>(null) }
    var deleteConfirmCommentId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearAllData() }
    }

    LaunchedEffect(username, currentAccount) {
        viewModel.clearAllData()
        when {
            username == null -> {
                // Not logged in - show login prompt
            }
            username == currentAccount?.name -> {
                viewModel.loadOwnProfile()
            }
            else -> {
                viewModel.loadUserProfile(username)
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(uiState.authUrl) {
        uiState.authUrl?.let { url ->
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            context.startActivity(intent)
            viewModel.clearAuthUrl()
        }
    }

    Scaffold(
        topBar = {
            UniversalTopAppBar(
                currentRoute = currentRoute,
                excludeProfile = uiState.isOwnProfile,
                title = {
                    val titleText = if (uiState.isOwnProfile) {
                        uiState.account?.name ?: "Profile"
                    } else {
                        username.orEmpty()
                    }
                    Text(titleText)
                },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (uiState.isOwnProfile && uiState.isLoggedIn) {
                        IconButton(onClick = viewModel::logout) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.isLoggedIn && commentState.editingCommentId != null) {
                ReplyBar(
                    replyText = commentState.replyText,
                    onReplyTextChange = viewModel.commentViewModel::setReplyText,
                    onSubmit = viewModel::submitEdit,
                    onCancel = viewModel.commentViewModel::cancelEdit,
                    placeholder = "Edit comment..."
                )
            } else if (uiState.isLoggedIn && commentState.replyingTo != null) {
                ReplyBar(
                    replyText = commentState.replyText,
                    onReplyTextChange = viewModel.commentViewModel::setReplyText,
                    onSubmit = viewModel::submitReply,
                    onCancel = { viewModel.commentViewModel.setReplyingTo(null) }
                )
            }
        }
    ) { padding ->
        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (username == null) {
                LoginPrompt(
                    clientId = uiState.clientId,
                    onClientIdChange = viewModel::setClientId,
                    onLogin = viewModel::initiateLogin
                )
            } else if (uiState.isLoading && uiState.account == null && uiState.user == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                val displayName = if (uiState.isOwnProfile) uiState.account?.name else uiState.user?.name
                val iconUrl = if (uiState.isOwnProfile) uiState.account?.iconUrl else uiState.user?.iconUrl
                val karma = if (uiState.isOwnProfile) uiState.account?.totalKarma else uiState.user?.totalKarma
                val created = if (uiState.isOwnProfile) uiState.account?.createdUtc else uiState.user?.createdUtc

                if (displayName != null) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        UserHeader(
                            name = displayName,
                            iconUrl = iconUrl,
                            karma = karma ?: 0,
                            created = created ?: 0L
                        )

                        val tabs = if (uiState.isOwnProfile) {
                            listOf(
                                ProfileTab.POSTS,
                                ProfileTab.COMMENTS,
                                ProfileTab.SAVED,
                                ProfileTab.UPVOTED,
                                ProfileTab.DOWNVOTED,
                                ProfileTab.ABOUT
                            )
                        } else {
                            listOf(ProfileTab.POSTS, ProfileTab.COMMENTS, ProfileTab.ABOUT)
                        }

                        val selectedTabIndex = tabs.indexOf(uiState.selectedTab).coerceAtLeast(0)

                        if (tabs.size <= 3) {
                            PrimaryTabRow(
                                selectedTabIndex = selectedTabIndex,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                tabs.forEach { tab ->
                                    Tab(
                                        selected = uiState.selectedTab == tab,
                                        onClick = { viewModel.setSelectedTab(tab) },
                                        text = { Text(tab.displayName) }
                                    )
                                }
                            }
                        } else {
                            PrimaryScrollableTabRow(
                                selectedTabIndex = selectedTabIndex,
                                edgePadding = 8.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                tabs.forEach { tab ->
                                    Tab(
                                        selected = uiState.selectedTab == tab,
                                        onClick = { viewModel.setSelectedTab(tab) },
                                        text = { Text(tab.displayName) }
                                    )
                                }
                            }
                        }

                        var showSortSheet by remember { mutableStateOf(false) }
                        val showSortOption = uiState.selectedTab == ProfileTab.POSTS || 
                                           uiState.selectedTab == ProfileTab.COMMENTS

                        if (showSortOption) {
                            val currentSort = when (uiState.selectedTab) {
                                ProfileTab.POSTS -> uiState.postsSort
                                ProfileTab.COMMENTS -> uiState.commentsSort
                                else -> PostSort.NEW
                            }
                            val currentTimeFilter = when (uiState.selectedTab) {
                                ProfileTab.POSTS -> uiState.postsTimeFilter
                                ProfileTab.COMMENTS -> uiState.commentsTimeFilter
                                else -> TimeFilter.ALL
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(onClick = { showSortSheet = true }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = "Sort"
                                    )
                                }
                            }
                            
                            if (showSortSheet) {
                                SortBottomSheet(
                                    currentSort = currentSort,
                                    currentTimeFilter = currentTimeFilter,
                                    onSortSelected = { sort ->
                                        when (uiState.selectedTab) {
                                            ProfileTab.POSTS -> viewModel.setPostsSort(sort)
                                            ProfileTab.COMMENTS -> viewModel.setCommentsSort(sort)
                                            else -> {}
                                        }
                                    },
                                    onTimeFilterSelected = { timeFilter ->
                                        when (uiState.selectedTab) {
                                            ProfileTab.POSTS -> viewModel.setPostsTimeFilter(timeFilter)
                                            ProfileTab.COMMENTS -> viewModel.setCommentsTimeFilter(timeFilter)
                                            else -> {}
                                        }
                                    },
                                    onDismiss = { showSortSheet = false }
                                )
                            }
                        }

                        when (uiState.selectedTab) {
                            ProfileTab.ABOUT -> {
                                if (uiState.isOwnProfile) {
                                    AboutTab(account = uiState.account)
                                } else {
                                    AboutTabForUser(user = uiState.user)
                                }
                            }
                            ProfileTab.COMMENTS -> {
                                val comments = uiState.comments
                                if (uiState.isLoadingContent && comments.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                } else if (comments.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No comments")
                                    }
                                } else {
                                    LazyColumn {
                                        items(comments, key = { it.id }) { comment ->
                                            CommentItem(
                                                comment = comment,
                                                isSelected = selectedCommentId == comment.id,
                                                isHidden = false,
                                                onSelect = { 
                                                    selectedCommentId = if (selectedCommentId == comment.id) null else comment.id
                                                },
                                                onDone = { selectedCommentId = null },
                                                onHide = {},
                                                onPrev = {},
                                                onNext = {},
                                                onRoot = {},
                                                onParent = {},
                                                onCommentUpdated = { updatedComment ->
                                                    viewModel.updateComment(updatedComment)
                                                },
                                                onShare = {
                                                    coroutineScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", "https://reddit.com${comment.permalink}"))) }
                                                },
                                                onReply = { viewModel.commentViewModel.setReplyingTo(comment.name) },
                                                onEdit = { viewModel.commentViewModel.startEditComment(comment) },
                                                onDelete = { deleteConfirmCommentId = comment.id },
                                                onSave = {
                                                    viewModel.saveComment(comment)
                                                    Toast.makeText(
                                                        context,
                                                        if (comment.isSaved) "Unsaved comment" else "Saved comment",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                isLoggedIn = uiState.isLoggedIn,
                                                loggedInUsername = uiState.account?.name,
                                                showTopControls = false,
                                                showSubreddit = true,
                                                onGoToCommentNav = { commentId ->
                                                    val postId = comment.linkId.removePrefix("t3_")
                                                    onCommentClick(comment.subreddit, postId, commentId)
                                                },
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            ProfileTab.SAVED -> {
                                var showSavedTypeMenu by remember { mutableStateOf(false) }
                                val savedPosts = uiState.savedPosts
                                val savedComments = uiState.savedComments
                                val currentItems = when (uiState.savedContentType) {
                                    SavedContentType.POSTS -> savedPosts
                                    SavedContentType.COMMENTS -> savedComments
                                }

                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Dropdown selector header
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { showSavedTypeMenu = true }
                                        ) {
                                            Text(
                                                uiState.savedContentType.displayName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = "Change saved type"
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showSavedTypeMenu,
                                            onDismissRequest = { showSavedTypeMenu = false }
                                        ) {
                                            SavedContentType.entries.forEach { type ->
                                                DropdownMenuItem(
                                                    text = { Text(type.displayName) },
                                                    onClick = {
                                                        viewModel.setSavedContentType(type)
                                                        showSavedTypeMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    when (uiState.savedContentType) {
                                        SavedContentType.POSTS -> {
                                            if (uiState.isLoadingContent && savedPosts.isEmpty()) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator()
                                                }
                                            } else if (savedPosts.isEmpty()) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("No saved posts")
                                                }
                                            } else {
                                                val readPostsRepository: ReadPostsRepository = koinInject()
                                                val settingsRepository: com.reader.shared.data.repository.SettingsRepository = koinInject()
                                                val readPostIds by readPostsRepository.readPostIds.collectAsState()
                                                val nsfwEnabled by settingsRepository.nsfwEnabled.collectAsState()
                                                val nsfwHistoryMode by settingsRepository.nsfwHistoryMode.collectAsState()
                                                val nsfwPreviewMode by settingsRepository.nsfwPreviewMode.collectAsState()
                                                val effectiveNsfwPreviewMode = if (nsfwEnabled) nsfwPreviewMode else com.reader.shared.domain.model.NsfwPreviewMode.DO_NOT_PREFETCH
                                                val effectiveNsfwHistoryMode = if (nsfwEnabled) nsfwHistoryMode else com.reader.shared.domain.model.NsfwHistoryMode.DONT_SAVE_ANY_NSFW
                                                LazyColumn {
                                                    items(savedPosts, key = { it.id }) { post ->
                                                        val navigationHandler: NavigationHandler = koinInject()
                                                        PostCard(
                                                            post = post,
                                                            onClick = {
                                                                readPostsRepository.markAsRead(post, effectiveNsfwHistoryMode)
                                                                onPostClick(post.subreddit, post.id)
                                                            },
                                                            onSubredditClick = { onSubredditClick(post.subreddit) },
                                                            onUserClick = {},
                                                            onUpvote = { viewModel.vote(post, if (post.likes == true) 0 else 1) },
                                                            onDownvote = { viewModel.vote(post, if (post.likes == false) 0 else -1) },
                                                            onSave = { viewModel.save(post) },
                                                            onHide = {},
                                                            isLoggedIn = uiState.isLoggedIn,
                                                            onLinkClick = onLinkClick,
                                                            onCrosspostClick = {
                                                                post.crosspostParentPermalink?.let { navigationHandler.handleLink(it) }
                                                            },
                                                            isRead = readPostsRepository.isRead(post, effectiveNsfwHistoryMode),
                                                            nsfwPreviewMode = effectiveNsfwPreviewMode
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        SavedContentType.COMMENTS -> {
                                            if (uiState.isLoadingContent && savedComments.isEmpty()) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator()
                                                }
                                            } else if (savedComments.isEmpty()) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("No saved comments")
                                                }
                                            } else {
                                                LazyColumn {
                                                    items(savedComments, key = { it.id }) { comment ->
                                                        CommentItem(
                                                            comment = comment,
                                                            isSelected = selectedCommentId == comment.id,
                                                            isHidden = false,
                                                            onSelect = { 
                                                                selectedCommentId = if (selectedCommentId == comment.id) null else comment.id
                                                            },
                                                            onDone = { selectedCommentId = null },
                                                            onHide = {},
                                                            onPrev = {},
                                                            onNext = {},
                                                            onRoot = {},
                                                            onParent = {},
                                                            onCommentUpdated = { updatedComment ->
                                                                viewModel.updateComment(updatedComment)
                                                            },
                                                            onShare = {
                                                                coroutineScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", "https://reddit.com${comment.permalink}"))) }
                                                            },
                                                            onReply = { viewModel.commentViewModel.setReplyingTo(comment.name) },
                                                            onEdit = { viewModel.commentViewModel.startEditComment(comment) },
                                                            onDelete = { deleteConfirmCommentId = comment.id },
                                                            onSave = {
                                                                viewModel.saveComment(comment)
                                                                Toast.makeText(
                                                                    context,
                                                                    if (comment.isSaved) "Unsaved comment" else "Saved comment",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            },
                                                            isLoggedIn = uiState.isLoggedIn,
                                                            loggedInUsername = uiState.account?.name,
                                                            showTopControls = false,
                                                            showSubreddit = true,
                                                            onGoToCommentNav = { commentId ->
                                                                val postId = comment.linkId.removePrefix("t3_")
                                                                onCommentClick(comment.subreddit, postId, commentId)
                                                            },
                                                            modifier = Modifier.padding(vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                val posts = when (uiState.selectedTab) {
                                    ProfileTab.UPVOTED -> uiState.upvotedPosts
                                    ProfileTab.DOWNVOTED -> uiState.downvotedPosts
                                    else -> uiState.posts
                                }

                                if (uiState.isLoadingContent && posts.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                } else if (posts.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No posts")
                                    }
                                } else {
                                    val readPostsRepository: ReadPostsRepository = koinInject()
                                    val settingsRepository: com.reader.shared.data.repository.SettingsRepository = koinInject()
                                    val readPostIds by readPostsRepository.readPostIds.collectAsState()
                                    val nsfwEnabled by settingsRepository.nsfwEnabled.collectAsState()
                                    val nsfwHistoryMode by settingsRepository.nsfwHistoryMode.collectAsState()
                                    val nsfwPreviewMode by settingsRepository.nsfwPreviewMode.collectAsState()
                                    val effectiveNsfwPreviewMode = if (nsfwEnabled) nsfwPreviewMode else com.reader.shared.domain.model.NsfwPreviewMode.DO_NOT_PREFETCH
                                    val effectiveNsfwHistoryMode = if (nsfwEnabled) nsfwHistoryMode else com.reader.shared.domain.model.NsfwHistoryMode.DONT_SAVE_ANY_NSFW
                                    val navigationHandler: NavigationHandler = koinInject()
                                    LazyColumn {
                                        items(posts, key = { it.id }) { post ->
                                            PostCard(
                                                post = post,
                                                onClick = {
                                                    readPostsRepository.markAsRead(post, effectiveNsfwHistoryMode)
                                                    onPostClick(post.subreddit, post.id)
                                                },
                                                onSubredditClick = { onSubredditClick(post.subreddit) },
                                                onUserClick = {},
                                                onUpvote = { viewModel.vote(post, if (post.likes == true) 0 else 1) },
                                                onDownvote = { viewModel.vote(post, if (post.likes == false) 0 else -1) },
                                                onSave = { viewModel.save(post) },
                                                onHide = {},
                                                isLoggedIn = uiState.isLoggedIn,
                                                onLinkClick = onLinkClick,
                                                onCrosspostClick = {
                                                    post.crosspostParentPermalink?.let { navigationHandler.handleLink(it) }
                                                },
                                                isRead = readPostsRepository.isRead(post, effectiveNsfwHistoryMode),
                                                nsfwPreviewMode = effectiveNsfwPreviewMode
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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
}


@Composable
private fun LoginPrompt(
    clientId: String,
    onClientIdChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Sign in to Reddit", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Access your profile, saved posts, and more",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = clientId,
            onValueChange = onClientIdChange,
            label = { Text("Reddit Client ID") },
            placeholder = { Text("Enter your Reddit app client ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.9f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth(0.7f),
            enabled = clientId.isNotBlank()
        ) {
            Icon(Icons.AutoMirrored.Default.Login, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign In")
        }
    }
}

@Composable
private fun UserHeader(
    name: String,
    iconUrl: String?,
    karma: Int,
    created: Long
) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (iconUrl != null) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(modifier = Modifier.size(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(name.first().uppercase(), style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("u/$name", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${formatNumber(karma)} karma", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Joined ${formatTimeAgo(created)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AboutTab(account: Account?) {
    if (account == null) return
    
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Karma Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    KarmaRow("Post Karma", account.linkKarma)
                    KarmaRow("Comment Karma", account.commentKarma)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    KarmaRow("Total Karma", account.totalKarma, bold = true)
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Account Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (account.isGold) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reddit Premium", fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (account.isMod) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Moderator", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KarmaRow(label: String, value: Int, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(formatNumber(value), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun AboutTabForUser(user: User?) {
    if (user == null) return
    
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Karma Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    KarmaRow("Post Karma", user.linkKarma)
                    KarmaRow("Comment Karma", user.commentKarma)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    KarmaRow("Total Karma", user.totalKarma, bold = true)
                }
            }
        }
    }
}
