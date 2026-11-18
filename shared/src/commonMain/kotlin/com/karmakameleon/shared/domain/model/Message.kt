package com.karmakameleon.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: String,
    val name: String, // fullname e.g., t4_xxxxx
    val author: String?,
    val dest: String, // recipient
    val subject: String,
    val body: String,
    val bodyHtml: String,
    val created: Long,
    val createdUtc: Long,
    val isNew: Boolean,
    val wasComment: Boolean,
    val context: String?, // permalink if was_comment
    val subreddit: String?,
    val parentId: String?,
    val firstMessageName: String?, // for reply chains
    val replies: List<Message>,
    val type: MessageType,
    val linkTitle: String? = null,
    val likes: Boolean? = null,
)

enum class MessageType {
    PRIVATE_MESSAGE,
    COMMENT_REPLY,
    POST_REPLY,
    USERNAME_MENTION,
    MOD_MESSAGE,
}

enum class InboxFilter(
    val value: String,
    val displayName: String,
) {
    ALL("inbox", "All"),
    UNREAD("unread", "Unread"),
    MESSAGES("messages", "Messages"),
    COMMENT_REPLIES("comments", "Comment replies"),
    POST_REPLIES("selfreply", "Post replies"),
    MENTIONS("mentions", "Mentions"),
    SENT("sent", "Sent"),
}
