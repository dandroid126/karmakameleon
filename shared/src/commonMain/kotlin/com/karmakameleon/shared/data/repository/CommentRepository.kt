package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.domain.model.Comment

class CommentRepository(
    private val redditApi: RedditApi,
) {
    suspend fun vote(comment: Comment, direction: Int): Result<Comment> {
        return try {
            val success = redditApi.vote(comment.name, direction)
            if (success) {
                val newLikes = when (direction) {
                    1 -> true
                    -1 -> false
                    else -> null
                }
                val scoreDiff = when {
                    comment.likes == true && direction != 1 -> -1
                    comment.likes == false && direction != -1 -> 1
                    comment.likes == null && direction == 1 -> 1
                    comment.likes == null && direction == -1 -> -1
                    else -> 0
                }
                val updated = comment.copy(
                    likes = newLikes,
                    score = comment.score + scoreDiff
                )
                Result.success(updated)
            } else {
                Result.failure(Exception("Vote failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun save(comment: Comment): Result<Comment> {
        return try {
            val success = if (comment.isSaved) {
                redditApi.unsave(comment.name)
            } else {
                redditApi.save(comment.name)
            }
            if (success) {
                Result.success(comment.copy(isSaved = !comment.isSaved))
            } else {
                Result.failure(Exception("Save failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitComment(parentId: String, text: String): Result<Comment> {
        return try {
            val comment = redditApi.submitComment(parentId, text)
            if (comment != null) {
                Result.success(comment)
            } else {
                Result.failure(Exception("Failed to submit comment"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun editComment(comment: Comment, text: String): Result<Comment> {
        return try {
            val success = redditApi.editComment(comment.name, text)
            if (success) {
                Result.success(comment.copy(body = text))
            } else {
                Result.failure(Exception("Edit failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteComment(comment: Comment): Result<Unit> {
        return try {
            val success = redditApi.deleteComment(comment.name)
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Delete failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
