package com.reader.android.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reader.android.navigation.NavigationHandler
import com.reader.android.ui.components.MessageItem
import com.reader.android.ui.components.ReplyBar
import com.reader.shared.domain.model.InboxFilter
import com.reader.shared.domain.model.Message
import com.reader.shared.ui.inbox.InboxViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: InboxViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterMenu by remember { mutableStateOf(false) }
    var blockPendingMessage by remember { mutableStateOf<Message?>(null) }
    val navigationHandler = koinInject<NavigationHandler>()

    fun navigateToFullComments(contextUrl: String) {
        val path = contextUrl.split("?")[0]
        val parts = path.split("/").filter { it.isNotEmpty() }
        val subreddit = parts.getOrNull(1) ?: return
        val postId = parts.getOrNull(3) ?: return
        navigationHandler.onPostClick(subreddit, postId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inbox") },
                actions = {
                    if (uiState.isLoggedIn) {
                        IconButton(onClick = viewModel::markAllAsRead) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Mark all read")
                        }
                        Box {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter")
                            }
                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false }
                            ) {
                                InboxFilter.entries.forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                        onClick = {
                                            viewModel.setFilter(filter)
                                            showFilterMenu = false
                                        },
                                        leadingIcon = if (uiState.currentFilter == filter) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else null
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.loadMessages(forceRefresh = true) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.isLoggedIn && uiState.replyingToMessage != null) {
                ReplyBar(
                    replyText = uiState.replyText,
                    onReplyTextChange = viewModel::setReplyText,
                    onSubmit = viewModel::submitReply,
                    onCancel = viewModel::cancelReply
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!uiState.isLoggedIn) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Sign in to view your inbox")
                }
            } else if (uiState.isLoading && uiState.messages.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.messages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No messages")
                }
            } else {
                LazyColumn {
                    items(uiState.messages, key = { it.id }) { message ->
                        MessageItem(
                            message = message,
                            isSelected = uiState.selectedMessageId == message.id,
                            isLoggedIn = uiState.isLoggedIn,
                            onSelect = {
                                viewModel.markAsRead(message)
                                viewModel.selectMessage(message.id)
                            },
                            onVote = { direction -> viewModel.voteOnMessage(message, direction) },
                            onContext = { message.context?.let { navigationHandler.handleLink(it) } },
                            onFullComments = { message.context?.let { navigateToFullComments(it) } },
                            onReport = {},
                            onBlockUser = { blockPendingMessage = message },
                            onMarkUnread = { viewModel.markAsUnread(message) },
                            onReply = { viewModel.startReply(message) }
                        )
                    }
                }
            }
        }
    }

    blockPendingMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { blockPendingMessage = null },
            title = { Text("Block user") },
            text = { Text("Are you sure you want to block u/${msg.author}? They will no longer be able to message you.") },
            confirmButton = {
                TextButton(onClick = {
                    msg.author?.let { viewModel.blockUser(it) }
                    blockPendingMessage = null
                }) {
                    Text("Block", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { blockPendingMessage = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
