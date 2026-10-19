package com.karmakameleon.android.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.android.ui.components.MessageActions
import com.karmakameleon.android.ui.components.MessageItem
import com.karmakameleon.android.ui.components.ReplyBar
import com.karmakameleon.android.ui.components.UniversalTopAppBar
import com.karmakameleon.shared.domain.model.InboxFilter
import com.karmakameleon.shared.domain.model.Message
import com.karmakameleon.shared.ui.inbox.InboxUiState
import com.karmakameleon.shared.ui.inbox.InboxViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun InboxScreen(
    modifier: Modifier = Modifier,
    initialFilter: InboxFilter? = null,
    currentRoute: String? = null,
    onBackClick: () -> Unit = {},
    viewModel: InboxViewModel = koinViewModel(),
    navigationHandler: NavigationHandler = koinInject(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var blockPendingMessage by remember { mutableStateOf<Message?>(null) }

    SideEffect(initialFilter) {
        if (initialFilter != null) {
            viewModel.setFilter(initialFilter)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            InboxTopBar(
                currentRoute = currentRoute,
                isLoggedIn = uiState.isLoggedIn,
                currentFilter = uiState.currentFilter,
                onBackClick = onBackClick,
                onMarkAllRead = viewModel::markAllAsRead,
                onSelectFilter = viewModel::setFilter,
            )
        },
        bottomBar = {
            if (uiState.isLoggedIn && uiState.replyingToMessage != null) {
                ReplyBar(
                    replyText = uiState.replyText,
                    onReplyTextChange = viewModel::setReplyText,
                    onSubmit = viewModel::submitReply,
                    onCancel = viewModel::cancelReply,
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.loadMessages(forceRefresh = true) },
            state = rememberPullToRefreshState(),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            InboxContent(
                uiState = uiState,
                messageActions = { message ->
                    viewModel.messageActions(message, navigationHandler, onBlockUser = { blockPendingMessage = it })
                },
            )
        }
    }

    blockPendingMessage?.let { message ->
        BlockUserDialog(
            username = message.author,
            onConfirm = {
                message.author?.let { viewModel.blockUser(it) }
                blockPendingMessage = null
            },
            onDismiss = { blockPendingMessage = null },
        )
    }
}

private fun InboxViewModel.messageActions(
    message: Message,
    navigationHandler: NavigationHandler,
    onBlockUser: (Message) -> Unit,
) = MessageActions(
    onSelect = {
        markAsRead(message)
        selectMessage(message.id)
    },
    onVote = { direction -> voteOnMessage(message, direction) },
    onContext = { message.context?.let { navigationHandler.handleLink(it) } },
    onFullComments = {
        message.context?.let(::postOfContextUrl)?.let { (subreddit, postId) -> navigationHandler.onPostClick(subreddit, postId) }
    },
    onReport = {},
    onBlockUser = { onBlockUser(message) },
    onMarkUnread = { markAsUnread(message) },
    onReply = { startReply(message) },
)

// A comment's context URL is /r/<subreddit>/comments/<post>/...; returns the subreddit and post.
private fun postOfContextUrl(contextUrl: String): Pair<String, String>? {
    val parts = contextUrl.substringBefore("?").split("/").filter { it.isNotEmpty() }
    val subreddit = parts.getOrNull(1) ?: return null
    val postId = parts.getOrNull(3) ?: return null
    return subreddit to postId
}

@Composable
private fun InboxTopBar(
    currentRoute: String?,
    isLoggedIn: Boolean,
    currentFilter: InboxFilter,
    onBackClick: () -> Unit,
    onMarkAllRead: () -> Unit,
    onSelectFilter: (InboxFilter) -> Unit,
) {
    UniversalTopAppBar(
        currentRoute = currentRoute,
        excludeInbox = true,
        title = { Text("Inbox") },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            if (isLoggedIn) {
                IconButton(onClick = onMarkAllRead) {
                    Icon(Icons.Default.DoneAll, contentDescription = "Mark all read")
                }
                InboxFilterMenu(currentFilter = currentFilter, onSelectFilter = onSelectFilter)
            }
        },
    )
}

@Composable
private fun InboxFilterMenu(
    currentFilter: InboxFilter,
    onSelectFilter: (InboxFilter) -> Unit,
) {
    var showFilterMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showFilterMenu = true }) {
            Icon(Icons.Default.FilterList, contentDescription = "Filter")
        }
        DropdownMenu(
            expanded = showFilterMenu,
            onDismissRequest = { showFilterMenu = false },
        ) {
            InboxFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onSelectFilter(filter)
                        showFilterMenu = false
                    },
                    leadingIcon =
                        if (currentFilter == filter) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

// A sign-in prompt when logged out, a spinner while loading, a message when empty, otherwise the messages.
@Composable
private fun BoxScope.InboxContent(
    uiState: InboxUiState,
    messageActions: (Message) -> MessageActions,
) {
    when {
        !uiState.isLoggedIn -> {
            InboxEmptyState(icon = Icons.Default.Email, text = "Sign in to view your inbox")
        }

        uiState.isLoading && uiState.messages.isEmpty() -> {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        uiState.messages.isEmpty() -> {
            InboxEmptyState(icon = Icons.Default.Inbox, text = "No messages")
        }

        else -> {
            LazyColumn {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageItem(
                        message = message,
                        isSelected = uiState.selectedMessageId == message.id,
                        isLoggedIn = uiState.isLoggedIn,
                        actions = messageActions(message),
                    )
                }
            }
        }
    }
}

@Composable
private fun InboxEmptyState(
    icon: ImageVector,
    text: String,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text)
    }
}

@Composable
private fun BlockUserDialog(
    username: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block user") },
        text = { Text("Are you sure you want to block u/$username? They will no longer be able to message you.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Block", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
