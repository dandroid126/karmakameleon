package com.reader.shared.data.repository

import com.reader.shared.data.api.RedditApi
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
}
