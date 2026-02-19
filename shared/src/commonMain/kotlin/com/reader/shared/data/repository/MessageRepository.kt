package com.reader.shared.data.repository

import com.reader.shared.data.api.RedditApi
import com.reader.shared.domain.model.Comment
import com.reader.shared.domain.model.InboxFilter
import com.reader.shared.domain.model.Listing
import com.reader.shared.domain.model.Message

class MessageRepository(
    private val redditApi: RedditApi,
) {
    suspend fun getInbox(
        filter: InboxFilter = InboxFilter.ALL,
        after: String? = null
    ): Result<Listing<Message>> {
        return try {
            val listing = redditApi.getInbox(filter, after)
            Result.success(listing)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(to: String, subject: String, body: String): Result<Unit> {
        return try {
            val success = redditApi.sendMessage(to, subject, body)
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to send message"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAsRead(message: Message): Result<Unit> {
        return try {
            val success = redditApi.markMessageRead(message.name)
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mark as read"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAllAsRead(): Result<Unit> {
        return try {
            val success = redditApi.markAllMessagesRead()
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mark all as read"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAsUnread(message: Message): Result<Unit> {
        return try {
            val success = redditApi.markMessageUnread(message.name)
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mark as unread"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun blockUser(username: String): Result<Unit> {
        return try {
            val success = redditApi.blockUser(username)
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to block user"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun voteOnMessage(message: Message, direction: Int): Result<Message> {
        return try {
            val success = redditApi.vote(message.name, direction)
            if (success) {
                val newLikes = when (direction) {
                    1 -> true
                    -1 -> false
                    else -> null
                }
                Result.success(message.copy(likes = newLikes))
            } else {
                Result.failure(Exception("Vote failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun replyToMessage(message: Message, text: String): Result<Comment> {
        return try {
            val comment = redditApi.submitComment(message.name, text)
            if (comment != null) {
                Result.success(comment)
            } else {
                Result.failure(Exception("Failed to submit reply"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
