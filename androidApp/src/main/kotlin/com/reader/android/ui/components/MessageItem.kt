package com.reader.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.navigation.NavigationHandler
import com.reader.shared.domain.model.Message
import com.reader.shared.domain.model.MessageType
import org.koin.compose.koinInject

@Composable
fun MessageItem(
    message: Message,
    isSelected: Boolean,
    isLoggedIn: Boolean,
    onSelect: () -> Unit,
    onVote: (Int) -> Unit,
    onContext: () -> Unit,
    onFullComments: () -> Unit,
    onReport: () -> Unit,
    onBlockUser: () -> Unit,
    onMarkUnread: () -> Unit,
    onReply: () -> Unit,
    navigationHandler: NavigationHandler = koinInject()
) {
    val isCommentType = message.type in listOf(
        MessageType.COMMENT_REPLY, MessageType.POST_REPLY, MessageType.USERNAME_MENTION
    )
    val icon = when (message.type) {
        MessageType.COMMENT_REPLY -> Icons.Default.ChatBubble
        MessageType.POST_REPLY -> Icons.AutoMirrored.Default.Article
        MessageType.USERNAME_MENTION -> Icons.Default.AlternateEmail
        MessageType.PRIVATE_MESSAGE -> Icons.Default.Mail
        MessageType.MOD_MESSAGE -> Icons.Default.Notifications
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.background(Color(0xFF0079D3).copy(alpha = 0.08f))
                else Modifier
            )
            .clickable { onSelect() }
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = message.subject,
                    fontWeight = if (message.isNew) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (message.isNew) Color(0xFFFF0000) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            supportingContent = {
                Column {
                    message.author?.let { author ->
                        Text(
                            text = "u/$author",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { navigationHandler.onUserClick(author) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = message.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (message.isNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Text(
                    text = formatTimeAgo(message.createdUtc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        if (isSelected && isCommentType) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
            ) {
                IconButton(onClick = onContext, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Context",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onVote(if (message.likes == true) 0 else 1) },
                    enabled = isLoggedIn,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (message.likes == true) Icons.Filled.KeyboardArrowUp else Icons.Outlined.KeyboardArrowUp,
                        contentDescription = "Upvote",
                        modifier = Modifier.size(24.dp),
                        tint = if (message.likes == true) Color(0xFFFF4500) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onVote(if (message.likes == false) 0 else -1) },
                    enabled = isLoggedIn,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (message.likes == false) Icons.Filled.KeyboardArrowDown else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Downvote",
                        modifier = Modifier.size(24.dp),
                        tint = if (message.likes == false) Color(0xFF7193FF) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Full comments") },
                            onClick = { showMenu = false; onFullComments() },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Report") },
                            onClick = { showMenu = false; onReport() },
                            leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Block user") },
                            onClick = { showMenu = false; onBlockUser() },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Mark as unread") },
                            onClick = { showMenu = false; onMarkUnread() },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) }
                        )
                    }
                }
                if (isLoggedIn) {
                    IconButton(onClick = onReply, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Reply",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider()
    }
}
