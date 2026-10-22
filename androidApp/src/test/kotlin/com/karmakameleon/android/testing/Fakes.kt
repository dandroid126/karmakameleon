package com.karmakameleon.android.testing

import com.karmakameleon.shared.data.api.AuthManager
import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.domain.model.InboxFilter
import com.karmakameleon.shared.domain.model.Listing
import com.karmakameleon.shared.domain.model.Message
import com.karmakameleon.shared.domain.model.MessageType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import java.io.IOException

// No request reaches Reddit: the fakes below answer before anything is sent.
internal fun offlineClient() = HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) })

class FakeRedditApi(
    authManager: AuthManager,
) : RedditApi(offlineClient(), authManager) {
    // Null makes the request fail, like it would without a network.
    var unreadMessages: List<Message>? = emptyList()
    val requestedFilters = mutableListOf<InboxFilter>()

    // Each vote as "<thing ID> <direction>".
    val votes = mutableListOf<String>()

    override suspend fun getInbox(
        filter: InboxFilter,
        after: String?,
        limit: Int,
    ): Listing<Message> {
        requestedFilters += filter
        val messages = unreadMessages ?: throw IOException("offline")
        return Listing(items = messages, after = null, before = null, dist = messages.size)
    }

    override suspend fun vote(
        thingId: String,
        direction: Int,
    ): Boolean {
        votes += "$thingId $direction"
        return true
    }
}

fun testMessage(
    id: String,
    type: MessageType = MessageType.PRIVATE_MESSAGE,
    author: String? = "sender",
    body: String = "Message body",
) = Message(
    id = id,
    name = "t4_$id",
    author = author,
    dest = "testuser",
    subject = "Subject",
    body = body,
    bodyHtml = "<p>$body</p>",
    created = 1_700_000_000L,
    createdUtc = 1_700_000_000L,
    isNew = true,
    wasComment = type != MessageType.PRIVATE_MESSAGE,
    context = null,
    subreddit = null,
    parentId = null,
    firstMessageName = null,
    replies = emptyList(),
    type = type,
)
