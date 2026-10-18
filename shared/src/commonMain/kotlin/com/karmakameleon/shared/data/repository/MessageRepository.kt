package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.domain.model.InboxFilter
import com.karmakameleon.shared.domain.model.Listing
import com.karmakameleon.shared.domain.model.Message
import com.karmakameleon.shared.util.runSuspendCatching

class MessageRepository(
    private val redditApi: RedditApi,
) {
    suspend fun getInbox(
        filter: InboxFilter = InboxFilter.ALL,
        after: String? = null,
    ): Result<Listing<Message>> = runSuspendCatching { redditApi.getInbox(filter, after) }

    suspend fun sendMessage(
        to: String,
        subject: String,
        body: String,
    ): Result<Unit> =
        runSuspendCatching {
            check(redditApi.sendMessage(to, subject, body)) { "Failed to send message" }
        }

    suspend fun markAsRead(message: Message): Result<Unit> =
        runSuspendCatching {
            check(redditApi.markMessageRead(message.name)) { "Failed to mark as read" }
        }

    suspend fun markAllAsRead(): Result<Unit> =
        runSuspendCatching {
            check(redditApi.markAllMessagesRead()) { "Failed to mark all as read" }
        }

    suspend fun markAsUnread(message: Message): Result<Unit> =
        runSuspendCatching {
            check(redditApi.markMessageUnread(message.name)) { "Failed to mark as unread" }
        }

    suspend fun blockUser(username: String): Result<Unit> =
        runSuspendCatching {
            check(redditApi.blockUser(username)) { "Failed to block user" }
        }

    suspend fun voteOnMessage(
        message: Message,
        direction: Int,
    ): Result<Message> =
        runSuspendCatching {
            check(redditApi.vote(message.name, direction)) { "Vote failed" }
            val newLikes =
                when (direction) {
                    1 -> true
                    -1 -> false
                    else -> null
                }
            message.copy(likes = newLikes)
        }

    suspend fun replyToMessage(
        message: Message,
        text: String,
    ): Result<Comment> =
        runSuspendCatching {
            redditApi.submitComment(message.name, text) ?: error("Failed to submit reply")
        }
}
