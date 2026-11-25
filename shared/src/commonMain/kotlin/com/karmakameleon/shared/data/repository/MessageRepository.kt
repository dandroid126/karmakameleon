package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.data.api.RedditApi
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
}
