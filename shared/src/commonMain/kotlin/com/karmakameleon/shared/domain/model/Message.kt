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
    MOD_MESSAGE
}

@Serializable
data class Inbox(
    val messages: List<Message>,
    val after: String?,
    val before: String?,
)

enum class InboxFilter(val value: String) {
    ALL("inbox"),
    UNREAD("unread"),
    MESSAGES("messages"),
    COMMENT_REPLIES("comments"),
    POST_REPLIES("selfreply"),
    MENTIONS("mentions"),
    SENT("sent")
}
