package com.reader.android.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.reader.android.ui.components.PostCard
import com.reader.android.ui.components.formatNumber
import com.reader.android.ui.components.formatTimeAgo
import com.reader.shared.domain.model.Account
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    username: String? = null,
    onBackClick: (() -> Unit)? = null,
    onPostClick: (subreddit: String, postId: String) -> Unit,
    onSubredditClick: (String) -> Unit,
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
                    if (isOwnProfile && uiState.isLoggedIn) {
                        IconButton(onClick = viewModel::logout) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
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

                        if (isOwnProfile) {
                            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                                Tab(
                                    selected = uiState.selectedTab == ProfileTab.POSTS,
                                    onClick = { viewModel.setSelectedTab(ProfileTab.POSTS) },
                                    text = { Text("Posts") }
                                )
                                Tab(
                                    selected = uiState.selectedTab == ProfileTab.SAVED,
                                    onClick = { viewModel.setSelectedTab(ProfileTab.SAVED) },
                                    text = { Text("Saved") }
                                )
                                Tab(
                                    selected = uiState.selectedTab == ProfileTab.ABOUT,
                                    onClick = { viewModel.setSelectedTab(ProfileTab.ABOUT) },
                                    text = { Text("About") }
                                )
                            }
                        }

                        when {
                            uiState.selectedTab == ProfileTab.ABOUT && isOwnProfile -> {
                                AboutTab(account = uiState.account)
                            }
                            else -> {
                                val posts = when {
                                    !isOwnProfile -> uiState.posts
                                    uiState.selectedTab == ProfileTab.SAVED -> uiState.savedPosts
                                    else -> uiState.posts
                                }

                                if (uiState.isLoadingPosts && posts.isEmpty()) {
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
                                    LazyColumn {
                                        items(posts, key = { it.id }) { post ->
                                            PostCard(
                                                post = post,
                                                onClick = { onPostClick(post.subreddit, post.id) },
                                                onSubredditClick = { onSubredditClick(post.subreddit) },
                                                onUserClick = {},
                                                onUpvote = { viewModel.vote(post, if (post.likes == true) 0 else 1) },
                                                onDownvote = { viewModel.vote(post, if (post.likes == false) 0 else -1) },
                                                onSave = { viewModel.save(post) },
                                                onHide = {},
                                                isLoggedIn = uiState.isLoggedIn
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
