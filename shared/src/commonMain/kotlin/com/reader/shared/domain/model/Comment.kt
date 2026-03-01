package com.reader.shared.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class Comment(
    val id: String,
    val name: String, // fullname e.g., t1_xxxxx
    val parentId: String,
    val linkId: String,
    val author: String,
    val body: String,
    val bodyHtml: String,
    val score: Int,
    val created: Long,
    val createdUtc: Long,
    val isSubmitter: Boolean, // is OP
    val distinguished: String?, // moderator, admin
    val isStickied: Boolean,
    val isLocked: Boolean,
    val isArchived: Boolean,
    val isSaved: Boolean,
    val isCollapsed: Boolean,
    val likes: Boolean?, // null = no vote
    val depth: Int,
    val replies: List<Comment>,
    val moreReplies: MoreComments?,
    val authorFlairText: String?,
    val authorFlairBackgroundColor: String?,
    val authorFlairRichtext: List<FlairRichtext> = emptyList(),
    val scoreHidden: Boolean,
    val edited: Long?, // timestamp if edited, null if not
    val subreddit: String,
    val permalink: String,
) {
    val voteState: VoteState get() = when (likes) {
        true -> VoteState.UPVOTED
        false -> VoteState.DOWNVOTED
        null -> VoteState.NONE
    }
    
    val createdInstant: Instant get() = Instant.fromEpochSeconds(createdUtc)
    val isEdited: Boolean get() = edited != null
}

@Serializable
data class MoreComments(
    val id: String,
    val name: String,
    val parentId: String,
    val count: Int,
    val depth: Int,
    val children: List<String> // List of comment IDs
)

@Serializable
data class FlairRichtext(
    val type: String, // "text" or "emoji"
    val text: String? = null,
    val url: String? = null
)

enum class CommentSort(val value: String, val displayName: String) {
    CONFIDENCE("confidence", "Best"),
    TOP("top", "Top"),
    NEW("new", "New"),
    CONTROVERSIAL("controversial", "Controversial"),
    OLD("old", "Old"),
    QA("qa", "Q&A")
}
