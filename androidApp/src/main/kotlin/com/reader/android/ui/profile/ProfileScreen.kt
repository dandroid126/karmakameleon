package com.reader.android.ui.profile

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.reader.android.data.ReadPostsRepository
import com.reader.android.ui.components.PostCard
import com.reader.android.ui.components.ProfileCommentCard
import com.reader.android.ui.components.SortBottomSheet
import com.reader.android.ui.components.formatNumber
import com.reader.android.ui.components.formatTimeAgo
import com.reader.shared.domain.model.Account
import com.reader.shared.domain.model.PostSort
import com.reader.shared.domain.model.TimeFilter
import com.reader.shared.domain.model.User
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    username: String? = null,
    onBackClick: (() -> Unit)? = null,
    onPostClick: (subreddit: String, postId: String) -> Unit,
    onSubredditClick: (String) -> Unit,
    onLinkClick: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isOwnProfile = username == null

    LaunchedEffect(username) {
        if (username != null) {
            viewModel.loadUser(username)
        }
    }

    LaunchedEffect(uiState.authUrl) {
        uiState.authUrl?.let { url ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
            viewModel.clearAuthUrl()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(username ?: "Profile") },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isOwnProfile) {
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                        if (uiState.isLoggedIn) {
                            IconButton(onClick = viewModel::logout) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!uiState.isLoggedIn && isOwnProfile) {
                LoginPrompt(onLogin = viewModel::initiateLogin)
            } else if (uiState.isLoading && uiState.account == null && uiState.user == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                val displayName = if (isOwnProfile) uiState.account?.name else uiState.user?.name
                val iconUrl = if (isOwnProfile) uiState.account?.iconUrl else uiState.user?.iconUrl
                val karma = if (isOwnProfile) uiState.account?.totalKarma else uiState.user?.totalKarma
                val created = if (isOwnProfile) uiState.account?.createdUtc else uiState.user?.createdUtc

                if (displayName != null) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        UserHeader(
                            name = displayName,
                            iconUrl = iconUrl,
                            karma = karma ?: 0,
                            created = created ?: 0L
                        )

                        val tabs = if (isOwnProfile) {
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
                            androidx.compose.material3.TabRow(
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
                            ScrollableTabRow(
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
                                if (isOwnProfile) {
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
                                            ProfileCommentCard(
                                                comment = comment,
                                                onClick = {
                                                    val postId = comment.linkId.removePrefix("t3_")
                                                    onPostClick(comment.subreddit, postId)
                                                },
                                                onSubredditClick = onSubredditClick,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                val posts = when (uiState.selectedTab) {
                                    ProfileTab.SAVED -> uiState.savedPosts
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
                                    val readPostIds by readPostsRepository.readPostIds.collectAsState()
                                    LazyColumn {
                                        items(posts, key = { it.id }) { post ->
                                            PostCard(
                                                post = post,
                                                onClick = {
                                                    readPostsRepository.markAsRead(post.id)
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
                                                isRead = readPostIds.contains(post.id)
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
}


@Composable
private fun LoginPrompt(onLogin: () -> Unit) {
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
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth(0.7f)) {
            Icon(Icons.Default.Login, contentDescription = null)
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
