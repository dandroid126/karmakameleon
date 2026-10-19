package com.karmakameleon.android.ui.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.android.ui.components.FeedPostItem
import com.karmakameleon.android.ui.components.LoadMoreNearEnd
import com.karmakameleon.android.ui.components.PostFeedSettings
import com.karmakameleon.android.ui.components.PostListCallbacks
import com.karmakameleon.android.ui.components.ReplyBar
import com.karmakameleon.android.ui.components.SortBottomSheet
import com.karmakameleon.android.ui.components.UniversalTopAppBar
import com.karmakameleon.android.ui.components.formatNumber
import com.karmakameleon.android.ui.components.formatTimeAgo
import com.karmakameleon.android.ui.components.rememberPostFeedSettings
import com.karmakameleon.shared.data.api.AuthManager
import com.karmakameleon.shared.data.repository.ReadPostsRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.domain.model.NsfwHistoryMode
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.PostSort
import com.karmakameleon.shared.domain.model.TimeFilter
import com.karmakameleon.shared.ui.PagedList
import com.karmakameleon.shared.ui.profile.ProfileList
import com.karmakameleon.shared.ui.profile.ProfileTab
import com.karmakameleon.shared.ui.profile.ProfileUiState
import com.karmakameleon.shared.ui.profile.ProfileViewModel
import com.karmakameleon.shared.ui.profile.SavedContentType
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onPostClick: (subreddit: String, postId: String) -> Unit,
    onSubredditClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    username: String? = null,
    onBackClick: (() -> Unit)? = null,
    onCommentClick: (subreddit: String, postId: String, commentId: String) -> Unit = { s, p, c -> onPostClick(s, p) },
    onLinkClick: (String) -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
    userRepository: UserRepository = koinInject(),
    navigationHandler: NavigationHandler = koinInject(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val commentState by viewModel.commentViewModel.uiState.collectAsState()
    val currentAccount by userRepository.currentAccount.collectAsState()

    ProfileLoading(
        username = username,
        currentAccountName = currentAccount?.name,
        errorMessage = uiState.errorMessage,
        onClear = viewModel::clearAllData,
        onLoadOwnProfile = { viewModel.loadOwnProfile() },
        onLoadUserProfile = { viewModel.loadUserProfile(it) },
        onConsumeError = viewModel::clearErrorMessage,
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            ProfileTopBar(
                uiState = uiState,
                username = username,
                onBackClick = onBackClick,
                onCancelLogin = viewModel::cancelLogin,
                onLogout = {
                    clearRedditLoginSession()
                    viewModel.logout()
                },
            )
        },
        bottomBar = {
            if (uiState.isLoggedIn) {
                ProfileReplyBar(
                    isEditing = commentState.editingCommentId != null,
                    isReplying = commentState.replyingTo != null,
                    replyText = commentState.replyText,
                    onReplyTextChange = viewModel.commentViewModel::setReplyText,
                    onSubmitEdit = viewModel::submitEdit,
                    onCancelEdit = viewModel.commentViewModel::cancelEdit,
                    onSubmitReply = viewModel::submitReply,
                    onCancelReply = { viewModel.commentViewModel.setReplyingTo(null) },
                )
            }
        },
    ) { padding ->
        ProfileScaffoldBody(
            uiState = uiState,
            isLoggedOut = username == null,
            padding = padding,
            loginActions = viewModel.loginActions(),
            onRefresh = viewModel::refresh,
            actions = viewModel.tabActions(),
            postCallbacks = viewModel.postCallbacks(onPostClick, onSubredditClick, onLinkClick, navigationHandler),
            commentCallbacks = viewModel.commentCallbacks(onOpen = onCommentClick),
        )
    }
}

// What signing in asks the view model to do.
private class LoginActions(
    val onRedirect: (String) -> Boolean,
    val onCancel: () -> Unit,
    val onClientIdChange: (String) -> Unit,
    val onRedirectUriChange: (String) -> Unit,
    val onLogin: () -> Unit,
)

// Reddit's sign-in page while signing in. Otherwise, under pull to refresh: the login prompt when logged out,
// a spinner while the profile loads, or the profile.
@Composable
private fun ProfileScaffoldBody(
    uiState: ProfileUiState,
    isLoggedOut: Boolean,
    padding: PaddingValues,
    loginActions: LoginActions,
    onRefresh: () -> Unit,
    actions: ProfileTabActions,
    postCallbacks: PostListCallbacks,
    commentCallbacks: ProfileCommentCallbacks,
) {
    val authUrl = uiState.authUrl
    if (isLoggedOut && authUrl != null) {
        RedditLoginWebView(
            authUrl = authUrl,
            onRedirect = loginActions.onRedirect,
            onCancel = loginActions.onCancel,
            modifier = Modifier.padding(padding),
        )
        return
    }
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        state = rememberPullToRefreshState(),
        modifier =
            Modifier
                .fillMaxSize()
                .padding(padding),
    ) {
        when {
            isLoggedOut -> {
                LoginPrompt(
                    clientId = uiState.clientId,
                    onClientIdChange = loginActions.onClientIdChange,
                    redirectUri = uiState.redirectUri,
                    onRedirectUriChange = loginActions.onRedirectUriChange,
                    isSigningIn = uiState.isSigningIn,
                    onLogin = loginActions.onLogin,
                )
            }

            uiState.isLoading && uiState.account == null && uiState.user == null -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            else -> {
                ProfileBody(uiState = uiState, actions = actions, postCallbacks = postCallbacks, commentCallbacks = commentCallbacks)
            }
        }
    }
}

private fun ProfileViewModel.loginActions() =
    LoginActions(
        onRedirect = ::handleLoginRedirect,
        onCancel = ::cancelLogin,
        onClientIdChange = ::setClientId,
        onRedirectUriChange = ::setRedirectUri,
        onLogin = ::initiateLogin,
    )

private fun ProfileViewModel.tabActions() =
    ProfileTabActions(
        onSelectTab = ::setSelectedTab,
        onLoadMore = ::loadMore,
        onSetPostsSort = ::setPostsSort,
        onSetPostsTimeFilter = ::setPostsTimeFilter,
        onSetCommentsSort = ::setCommentsSort,
        onSetCommentsTimeFilter = ::setCommentsTimeFilter,
        onSetSavedContentType = ::setSavedContentType,
    )

private fun ProfileViewModel.postCallbacks(
    onPostClick: (subreddit: String, postId: String) -> Unit,
    onSubredditClick: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    navigationHandler: NavigationHandler,
) = PostListCallbacks(
    onPostClick = onPostClick,
    onSubredditClick = onSubredditClick,
    onLinkClick = onLinkClick,
    onCrosspostClick = navigationHandler::handleLink,
    onVote = ::vote,
    onSave = ::save,
)

private fun ProfileViewModel.commentCallbacks(onOpen: (subreddit: String, postId: String, commentId: String) -> Unit) =
    ProfileCommentCallbacks(
        onUpdate = ::updateComment,
        onReply = { commentViewModel.setReplyingTo(it) },
        onEdit = commentViewModel::startEditComment,
        onDelete = ::deleteComment,
        onSave = ::saveComment,
        onOpen = onOpen,
    )

// The bar for writing a reply or editing a comment, while one is in progress.
@Composable
private fun ProfileReplyBar(
    isEditing: Boolean,
    isReplying: Boolean,
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    onSubmitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSubmitReply: () -> Unit,
    onCancelReply: () -> Unit,
) {
    if (isEditing) {
        ReplyBar(
            replyText = replyText,
            onReplyTextChange = onReplyTextChange,
            onSubmit = onSubmitEdit,
            onCancel = onCancelEdit,
            placeholder = "Edit comment...",
        )
    } else if (isReplying) {
        ReplyBar(
            replyText = replyText,
            onReplyTextChange = onReplyTextChange,
            onSubmit = onSubmitReply,
            onCancel = onCancelReply,
        )
    }
}

// Loads the profile that's asked for, clears it when the screen goes away, and shows errors as toasts.
@Composable
private fun ProfileLoading(
    username: String?,
    currentAccountName: String?,
    errorMessage: String?,
    onClear: () -> Unit,
    onLoadOwnProfile: () -> Unit,
    onLoadUserProfile: (String) -> Unit,
    onConsumeError: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnClear by rememberUpdatedState(onClear)
    val currentOnLoadOwnProfile by rememberUpdatedState(onLoadOwnProfile)
    val currentOnLoadUserProfile by rememberUpdatedState(onLoadUserProfile)
    val currentOnConsumeError by rememberUpdatedState(onConsumeError)
    DisposableEffect(Unit) {
        onDispose { currentOnClear() }
    }

    SideEffect(username, currentAccountName) {
        currentOnClear()
        when (username) {
            // Not logged in: the login prompt is shown.
            null -> {}

            currentAccountName -> {
                currentOnLoadOwnProfile()
            }

            else -> {
                currentOnLoadUserProfile(username)
            }
        }
    }

    SideEffect(errorMessage) {
        errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            currentOnConsumeError()
        }
    }
}

@Composable
private fun ProfileTopBar(
    uiState: ProfileUiState,
    username: String?,
    onBackClick: (() -> Unit)?,
    onCancelLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    UniversalTopAppBar(
        excludeProfile = uiState.isOwnProfile,
        title = {
            val titleText =
                when {
                    uiState.authUrl != null -> "Sign in to Reddit"
                    uiState.isOwnProfile -> uiState.account?.name ?: "Profile"
                    else -> username.orEmpty()
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
            if (uiState.authUrl != null) {
                IconButton(onClick = onCancelLogin) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel sign in")
                }
            }
            if (uiState.isOwnProfile && uiState.isLoggedIn) {
                IconButton(onClick = onLogout) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                }
            }
        },
    )
}

// What the tabs and their lists ask the view model to do.
private data class ProfileTabActions(
    val onSelectTab: (ProfileTab) -> Unit,
    val onLoadMore: (ProfileList) -> Unit,
    val onSetPostsSort: (PostSort) -> Unit,
    val onSetPostsTimeFilter: (TimeFilter) -> Unit,
    val onSetCommentsSort: (PostSort) -> Unit,
    val onSetCommentsTimeFilter: (TimeFilter) -> Unit,
    val onSetSavedContentType: (SavedContentType) -> Unit,
)

// The user's header, the tabs, and the selected tab's content.
@Composable
private fun ProfileBody(
    uiState: ProfileUiState,
    actions: ProfileTabActions,
    postCallbacks: PostListCallbacks,
    commentCallbacks: ProfileCommentCallbacks,
) {
    val account = uiState.account
    val user = uiState.user
    val displayName = if (uiState.isOwnProfile) account?.name else user?.name
    if (displayName == null) return

    Column(modifier = Modifier.fillMaxSize()) {
        UserHeader(
            name = displayName,
            iconUrl = if (uiState.isOwnProfile) account?.iconUrl else user?.iconUrl,
            karma = (if (uiState.isOwnProfile) account?.totalKarma else user?.totalKarma) ?: 0,
            created = (if (uiState.isOwnProfile) account?.createdUtc else user?.createdUtc) ?: 0L,
        )

        val tabs =
            if (uiState.isOwnProfile) {
                listOf(ProfileTab.POSTS, ProfileTab.COMMENTS, ProfileTab.SAVED, ProfileTab.UPVOTED, ProfileTab.DOWNVOTED, ProfileTab.ABOUT)
            } else {
                listOf(ProfileTab.POSTS, ProfileTab.COMMENTS, ProfileTab.ABOUT)
            }
        ProfileTabRow(tabs = tabs, selectedTab = uiState.selectedTab, onSelectTab = actions.onSelectTab)

        when (uiState.selectedTab) {
            ProfileTab.POSTS -> {
                ProfileSortButton(
                    sort = uiState.postsSort,
                    timeFilter = uiState.postsTimeFilter,
                    onSelectSort = actions.onSetPostsSort,
                    onSelectTimeFilter = actions.onSetPostsTimeFilter,
                )
            }

            ProfileTab.COMMENTS -> {
                ProfileSortButton(
                    sort = uiState.commentsSort,
                    timeFilter = uiState.commentsTimeFilter,
                    onSelectSort = actions.onSetCommentsSort,
                    onSelectTimeFilter = actions.onSetCommentsTimeFilter,
                )
            }

            else -> {}
        }

        ProfileTabContent(uiState = uiState, actions = actions, postCallbacks = postCallbacks, commentCallbacks = commentCallbacks)
    }
}

// Fixed tabs when they fit, scrollable ones for the longer list on your own profile.
@Composable
private fun ProfileTabRow(
    tabs: List<ProfileTab>,
    selectedTab: ProfileTab,
    onSelectTab: (ProfileTab) -> Unit,
) {
    val selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    val tabItems =
        @Composable {
            tabs.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onSelectTab(tab) },
                    text = { Text(tab.displayName) },
                )
            }
        }
    if (tabs.size <= 3) {
        PrimaryTabRow(selectedTabIndex = selectedTabIndex, modifier = Modifier.fillMaxWidth(), tabs = tabItems)
    } else {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 8.dp,
            modifier = Modifier.fillMaxWidth(),
            tabs = tabItems,
        )
    }
}

@Composable
private fun ProfileSortButton(
    sort: PostSort,
    timeFilter: TimeFilter,
    onSelectSort: (PostSort) -> Unit,
    onSelectTimeFilter: (TimeFilter) -> Unit,
) {
    var showSortSheet by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        IconButton(onClick = { showSortSheet = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort",
            )
        }
    }

    if (showSortSheet) {
        SortBottomSheet(
            currentSort = sort,
            currentTimeFilter = timeFilter,
            onSelectSort = onSelectSort,
            onSelectTimeFilter = onSelectTimeFilter,
            onDismiss = { showSortSheet = false },
        )
    }
}

@Composable
private fun ProfileTabContent(
    uiState: ProfileUiState,
    actions: ProfileTabActions,
    postCallbacks: PostListCallbacks,
    commentCallbacks: ProfileCommentCallbacks,
) {
    val feedSettings = rememberPostFeedSettings()
    var selectedCommentId by remember { mutableStateOf<String?>(null) }
    var deleteConfirmCommentId by remember { mutableStateOf<String?>(null) }
    // One list state per list, so each keeps its scroll position when switching tabs.
    val listStates = ProfileList.entries.associateWith { rememberLazyListState() }
    ProfileList.entries.forEach { list ->
        val pagedList = uiState.pagedList(list)
        LoadMoreNearEnd(listStates.getValue(list), pagedList.items.size, enabled = uiState.isShowing(list) && pagedList.canLoadMore) {
            actions.onLoadMore(list)
        }
    }

    ProfileSelectedTab(
        uiState = uiState,
        listStates = listStates,
        feedSettings = feedSettings,
        postCallbacks = postCallbacks,
        commentListState =
            ProfileCommentListState(
                isLoggedIn = uiState.isLoggedIn,
                loggedInUsername = uiState.account?.name,
                selectedCommentId = selectedCommentId,
                onSelectComment = { selectedCommentId = it },
            ),
        // Deleting asks first.
        commentCallbacks = commentCallbacks.copy(onDelete = { deleteConfirmCommentId = it }),
        onSetSavedContentType = actions.onSetSavedContentType,
    )

    deleteConfirmCommentId?.let { commentId ->
        DeleteCommentDialog(
            onConfirm = {
                commentCallbacks.onDelete(commentId)
                deleteConfirmCommentId = null
            },
            onDismiss = { deleteConfirmCommentId = null },
        )
    }
}

@Composable
private fun ProfileSelectedTab(
    uiState: ProfileUiState,
    listStates: Map<ProfileList, LazyListState>,
    feedSettings: PostFeedSettings,
    postCallbacks: PostListCallbacks,
    commentListState: ProfileCommentListState,
    commentCallbacks: ProfileCommentCallbacks,
    onSetSavedContentType: (SavedContentType) -> Unit,
) {
    val postsTab = @Composable { list: ProfileList, pagedList: PagedList<Post>, emptyText: String ->
        ProfilePostsTab(
            posts = pagedList.items,
            isLoadingContent = uiState.isLoadingContent,
            isLoadingMore = pagedList.isLoadingMore,
            isLoggedIn = uiState.isLoggedIn,
            listState = listStates.getValue(list),
            feedSettings = feedSettings,
            callbacks = postCallbacks,
            emptyText = emptyText,
        )
    }
    val commentsTab = @Composable { list: ProfileList, pagedList: PagedList<Comment>, emptyText: String ->
        ProfileCommentsTab(
            comments = pagedList.items,
            isLoadingContent = uiState.isLoadingContent,
            isLoadingMore = pagedList.isLoadingMore,
            listState = listStates.getValue(list),
            state = commentListState,
            callbacks = commentCallbacks,
            emptyText = emptyText,
        )
    }

    when (uiState.selectedTab) {
        ProfileTab.ABOUT -> {
            val account = uiState.account
            val user = uiState.user
            when {
                // Premium and moderator status are only shown on your own profile.
                uiState.isOwnProfile && account != null -> {
                    AboutTab(account.linkKarma, account.commentKarma, account.totalKarma, account.isGold, account.isMod)
                }

                !uiState.isOwnProfile && user != null -> {
                    AboutTab(user.linkKarma, user.commentKarma, user.totalKarma)
                }
            }
        }

        ProfileTab.POSTS -> {
            postsTab(ProfileList.POSTS, uiState.posts, "No posts")
        }

        ProfileTab.COMMENTS -> {
            commentsTab(ProfileList.COMMENTS, uiState.comments, "No comments")
        }

        ProfileTab.SAVED -> {
            ProfileSavedTab(
                savedContentType = uiState.savedContentType,
                onSetSavedContentType = onSetSavedContentType,
                postsContent = { postsTab(ProfileList.SAVED_POSTS, uiState.savedPosts, "No saved posts") },
                commentsContent = { commentsTab(ProfileList.SAVED_COMMENTS, uiState.savedComments, "No saved comments") },
            )
        }

        ProfileTab.UPVOTED -> {
            postsTab(ProfileList.UPVOTED, uiState.upvotedPosts, "No upvoted posts")
        }

        ProfileTab.DOWNVOTED -> {
            postsTab(ProfileList.DOWNVOTED, uiState.downvotedPosts, "No downvoted posts")
        }
    }
}

private fun ProfileUiState.isShowing(list: ProfileList): Boolean =
    when (list) {
        ProfileList.POSTS -> selectedTab == ProfileTab.POSTS
        ProfileList.COMMENTS -> selectedTab == ProfileTab.COMMENTS
        ProfileList.SAVED_POSTS -> selectedTab == ProfileTab.SAVED && savedContentType == SavedContentType.POSTS
        ProfileList.SAVED_COMMENTS -> selectedTab == ProfileTab.SAVED && savedContentType == SavedContentType.COMMENTS
        ProfileList.UPVOTED -> selectedTab == ProfileTab.UPVOTED
        ProfileList.DOWNVOTED -> selectedTab == ProfileTab.DOWNVOTED
    }

private fun ProfileUiState.pagedList(list: ProfileList): PagedList<*> =
    when (list) {
        ProfileList.POSTS -> posts
        ProfileList.COMMENTS -> comments
        ProfileList.SAVED_POSTS -> savedPosts
        ProfileList.SAVED_COMMENTS -> savedComments
        ProfileList.UPVOTED -> upvotedPosts
        ProfileList.DOWNVOTED -> downvotedPosts
    }

@Composable
private fun DeleteCommentDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Comment") },
        text = { Text("Are you sure you want to delete this comment? This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun LoginPrompt(
    clientId: String,
    onClientIdChange: (String) -> Unit,
    redirectUri: String,
    onRedirectUriChange: (String) -> Unit,
    isSigningIn: Boolean,
    onLogin: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .imePadding()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Sign in to Reddit", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Access your profile, saved posts, and more",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = clientId,
            onValueChange = onClientIdChange,
            label = { Text("Reddit Client ID") },
            placeholder = { Text("Enter your Reddit app client ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.9f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = redirectUri,
            onValueChange = onRedirectUriChange,
            label = { Text("Redirect URI") },
            placeholder = { Text(AuthManager.DEFAULT_REDIRECT_URI) },
            supportingText = { Text("Must match the redirect URI of your app on Reddit") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(0.9f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth(0.7f),
            enabled = clientId.isNotBlank() && redirectUri.isNotBlank() && !isSigningIn,
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Default.Login, contentDescription = null)
            }
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
    created: Long,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (iconUrl != null) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
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
                Text(
                    "${formatNumber(karma)} karma",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Joined ${formatTimeAgo(created)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// The karma breakdown, then the account's Premium and moderator status when it has either.
@Composable
private fun AboutTab(
    linkKarma: Int,
    commentKarma: Int,
    totalKarma: Int,
    isGold: Boolean = false,
    isMod: Boolean = false,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        aboutSection("Karma Breakdown") {
            KarmaRow("Post Karma", linkKarma)
            KarmaRow("Comment Karma", commentKarma)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            KarmaRow("Total Karma", totalKarma, bold = true)
        }
        if (isGold || isMod) {
            item { Spacer(modifier = Modifier.height(16.dp)) }
            aboutSection("Account Info") {
                if (isGold) AccountBadge(Icons.Default.Star, "Reddit Premium")
                if (isGold && isMod) Spacer(modifier = Modifier.height(8.dp))
                if (isMod) AccountBadge(Icons.Default.Verified, "Moderator")
            }
        }
    }
}

// A heading and a card holding [content].
private fun LazyListScope.aboutSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    item {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
    }
    item {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
private fun AccountBadge(
    icon: ImageVector,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun KarmaRow(
    label: String,
    value: Int,
    bold: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(formatNumber(value), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}
