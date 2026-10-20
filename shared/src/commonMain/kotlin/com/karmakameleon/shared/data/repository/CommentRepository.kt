package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.util.runSuspendCatching

class CommentRepository(
    private val redditApi: RedditApi,
) {
    suspend fun vote(
        comment: Comment,
        direction: Int,
    ): Result<Comment> =
        runSuspendCatching {
            check(redditApi.vote(comment.name, direction)) { "Vote failed" }
            comment.copy(
                likes = voteToLikes(direction),
                score = comment.score + direction - likesToVote(comment.likes),
            )
        }

    suspend fun save(comment: Comment): Result<Comment> =
        runSuspendCatching {
            val success =
                if (comment.isSaved) {
                    redditApi.unsave(comment.name)
                } else {
                    redditApi.save(comment.name)
                }
            check(success) { "Save failed" }
            comment.copy(isSaved = !comment.isSaved)
        }

    suspend fun submitComment(
        parentId: String,
        text: String,
    ): Result<Comment> =
        runSuspendCatching {
            redditApi.submitComment(parentId, text) ?: error("Failed to submit comment")
        }

    suspend fun editComment(
        comment: Comment,
        text: String,
    ): Result<Comment> =
        runSuspendCatching {
            check(redditApi.editComment(comment.name, text)) { "Edit failed" }
            comment.copy(body = text)
        }

    suspend fun deleteComment(comment: Comment): Result<Unit> =
        runSuspendCatching {
            check(redditApi.deleteComment(comment.name)) { "Delete failed" }
        }
}
