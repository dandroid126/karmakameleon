package com.karmakameleon.android.ui.components

import com.karmakameleon.android.testing.testMessage
import com.karmakameleon.shared.domain.model.Message
import com.karmakameleon.shared.domain.model.MessageType

// An inbox message from u/replier, three hours old.
fun inboxMessage(
    type: MessageType,
    subject: String,
    body: String,
): Message = testMessage("m1", type = type, author = "replier", body = body).copy(subject = subject, createdUtc = threeHoursAgo)

fun testCommentReply(): Message = inboxMessage(MessageType.COMMENT_REPLY, "comment reply", "Thanks, that fixed my build!")

fun testPrivateMessage(): Message =
    inboxMessage(MessageType.PRIVATE_MESSAGE, "Question about your app", "Does it support multiple accounts?")

/** Records which of a message's actions ran, in order. */
class RecordedMessageActions {
    val calls = mutableListOf<String>()

    val actions =
        MessageActions(
            onSelect = { calls += "select" },
            onVote = { calls += "vote $it" },
            onContext = { calls += "context" },
            onFullComments = { calls += "full comments" },
            onReport = { calls += "report" },
            onBlockUser = { calls += "block" },
            onMarkUnread = { calls += "mark unread" },
            onReply = { calls += "reply" },
        )
}
