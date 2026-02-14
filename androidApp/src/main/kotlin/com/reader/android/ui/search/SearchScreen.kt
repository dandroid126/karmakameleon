package com.reader.android.ui.search

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.reader.android.data.ReadPostsRepository
import com.reader.android.ui.components.PostCard
import com.reader.android.ui.components.formatNumber
import com.reader.shared.domain.model.SearchSort
import com.reader.shared.domain.model.SearchType
import com.reader.shared.domain.model.Subreddit
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onPostClick: (subreddit: String, postId: String) -> Unit,
    onSubredditClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onLinkClick: (String) -> Unit = {},
    viewModel: SearchViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val readPostsRepository: ReadPostsRepository = koinInject()
    val readPostIds by readPostsRepository.readPostIds.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= uiState.posts.size - 5
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !uiState.isLoading && uiState.hasMore && uiState.searchType == SearchType.POST) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Search") })
                
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search Reddit") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true
                )

                TabRow(selectedTabIndex = uiState.searchType.ordinal) {
                    Tab(
                        selected = uiState.searchType == SearchType.POST,
                        onClick = { viewModel.setSearchType(SearchType.POST) },
                        text = { Text("Posts") }
                    )
                    Tab(
                        selected = uiState.searchType == SearchType.SUBREDDIT,
                        onClick = { viewModel.setSearchType(SearchType.SUBREDDIT) },
                        text = { Text("Communities") }
                    )
                }

                if (uiState.searchType == SearchType.POST && uiState.query.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = { showSortMenu = true },
                            label = { Text(uiState.searchSort.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp)) }
                        )
                        FilterChip(
                            selected = false,
                            onClick = {},
                            label = { Text(uiState.timeFilter.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.query.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Search for posts or communities")
                }
            } else if (uiState.isLoading && uiState.posts.isEmpty() && uiState.subreddits.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                when (uiState.searchType) {
                    SearchType.POST -> {
                        if (uiState.posts.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No results found")
                            }
                        } else {
                            LazyColumn(state = listState) {
                                items(uiState.posts, key = { it.id }) { post ->
                                    PostCard(
                                        post = post,
                                        onClick = {
                                            readPostsRepository.markAsRead(post.id)
                                            onPostClick(post.subreddit, post.id)
                                        },
                                        onSubredditClick = { onSubredditClick(post.subreddit) },
                                        onUserClick = { onUserClick(post.author) },
                                        onUpvote = {},
                                        onDownvote = {},
                                        onSave = {},
                                        onHide = {},
                                        isLoggedIn = false,
                                        onLinkClick = onLinkClick,
                                        isRead = readPostIds.contains(post.id)
                                    )
                                }
                                
                                if (uiState.isLoading) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SearchType.SUBREDDIT -> {
                        if (uiState.subreddits.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No communities found")
                            }
                        } else {
                            LazyColumn {
                                items(uiState.subreddits, key = { it.id }) { subreddit ->
                                    SubredditSearchItem(
                                        subreddit = subreddit,
                                        onClick = { onSubredditClick(subreddit.displayName) }
                                    )
                                }
                            }
                        }
                    }
                    SearchType.USER -> {}
                }
            }
        }
    }

    if (showSortMenu) {
        ModalBottomSheet(onDismissRequest = { showSortMenu = false }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    "Sort by",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                SearchSort.entries.forEach { sort ->
                    if (uiState.searchSort == sort) {
                        ListItem(
                            headlineContent = { Text(sort.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            leadingContent = { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                viewModel.setSearchSort(sort)
                                showSortMenu = false
                            }
                        )
                    } else {
                        ListItem(
                            headlineContent = { Text(sort.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.clickable {
                                viewModel.setSearchSort(sort)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubredditSearchItem(
    subreddit: Subreddit,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(subreddit.displayNamePrefixed, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Column {
                Text("${formatNumber(subreddit.subscribers.toInt())} members")
                subreddit.publicDescription?.let { desc ->
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
            }
        },
        leadingContent = {
            if (subreddit.iconUrl != null) {
                AsyncImage(
                    model = subreddit.iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            subreddit.displayName.first().uppercase(),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
