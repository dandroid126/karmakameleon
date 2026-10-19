package com.karmakameleon.android.ui.components

/** What a message in the inbox does when it or one of its controls is tapped. */
data class MessageActions(
    val onSelect: () -> Unit,
    val onVote: (direction: Int) -> Unit,
    val onContext: () -> Unit,
    val onFullComments: () -> Unit,
    val onReport: () -> Unit,
    val onBlockUser: () -> Unit,
    val onMarkUnread: () -> Unit,
    val onReply: () -> Unit,
)
