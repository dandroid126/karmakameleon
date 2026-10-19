package com.karmakameleon.android.ui.components

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.android.ui.theme.messageColors
import com.karmakameleon.android.ui.theme.voteColors
import com.karmakameleon.shared.domain.model.Message
import com.karmakameleon.shared.domain.model.MessageType
import org.koin.compose.koinInject

@Composable
fun MessageItem(
    message: Message,
    isSelected: Boolean,
    isLoggedIn: Boolean,
    actions: MessageActions,
    navigationHandler: NavigationHandler = koinInject(),
) {
    // Replies and mentions are comments, so they can be voted on and opened in their thread.
    val isCommentType = message.type in listOf(MessageType.COMMENT_REPLY, MessageType.POST_REPLY, MessageType.USERNAME_MENTION)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (isSelected) messageColors().selectedBackground else Color.Transparent)
                .clickable { actions.onSelect() },
    ) {
        MessageListItem(message = message, onUserClick = { navigationHandler.onUserClick(it) })
        if (isSelected && isCommentType) {
            MessageActionRow(message = message, isLoggedIn = isLoggedIn, actions = actions)
        }
    }
}

@Composable
private fun MessageListItem(
    message: Message,
    onUserClick: (String) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = message.subject,
                fontWeight = if (message.isNew) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (message.isNew) messageColors().newMessageColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        supportingContent = {
            Column {
                message.author?.let { author ->
                    Text(
                        text = "u/$author",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onUserClick(author) },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = message.body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = message.type.icon,
                contentDescription = null,
                tint = if (message.isNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Text(
                text = formatTimeAgo(message.createdUtc),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private val MessageType.icon: ImageVector
    get() =
        when (this) {
            MessageType.COMMENT_REPLY -> Icons.Default.ChatBubble
            MessageType.POST_REPLY -> Icons.AutoMirrored.Default.Article
            MessageType.USERNAME_MENTION -> Icons.Default.AlternateEmail
            MessageType.PRIVATE_MESSAGE -> Icons.Default.Mail
            MessageType.MOD_MESSAGE -> Icons.Default.Notifications
        }

// Context, votes, more options and reply. Voting again in the same direction removes the vote.
@Composable
private fun MessageActionRow(
    message: Message,
    isLoggedIn: Boolean,
    actions: MessageActions,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
    ) {
        IconButton(onClick = actions.onContext, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Context",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MessageVoteButton(message, isUpvote = true, enabled = isLoggedIn) { actions.onVote(if (message.likes == true) 0 else 1) }
        MessageVoteButton(message, isUpvote = false, enabled = isLoggedIn) { actions.onVote(if (message.likes == false) 0 else -1) }
        MessageMoreMenu(actions)
        if (isLoggedIn) {
            IconButton(onClick = actions.onReply, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Reply",
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageVoteButton(
    message: Message,
    isUpvote: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val isCurrentVote = message.likes == isUpvote
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(
            when {
                isUpvote && isCurrentVote -> Icons.Filled.KeyboardArrowUp
                isUpvote -> Icons.Outlined.KeyboardArrowUp
                isCurrentVote -> Icons.Filled.KeyboardArrowDown
                else -> Icons.Outlined.KeyboardArrowDown
            },
            contentDescription = if (isUpvote) "Upvote" else "Downvote",
            modifier = Modifier.size(24.dp),
            tint =
                when {
                    !isCurrentVote -> MaterialTheme.colorScheme.onSurfaceVariant
                    isUpvote -> voteColors().upvoteColor
                    else -> voteColors().downvoteColor
                },
        )
    }
}

@Composable
private fun MessageMoreMenu(actions: MessageActions) {
    var showMenu by remember { mutableStateOf(false) }
    val entries =
        listOf(
            Triple("Full comments", Icons.AutoMirrored.Filled.Article, actions.onFullComments),
            Triple("Report", Icons.Default.Flag, actions.onReport),
            Triple("Block user", Icons.Default.Block, actions.onBlockUser),
            Triple("Mark as unread", Icons.Default.Email, actions.onMarkUnread),
        )
    Box {
        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            entries.forEach { (label, icon, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        showMenu = false
                        action()
                    },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                )
            }
        }
    }
}
