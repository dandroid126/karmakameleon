package com.karmakameleon.shared.ui.comment

import com.karmakameleon.shared.data.api.CommentOrMore
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.domain.model.MoreComments

// Pure functions on a comment tree: the top-level items of a post, each comment holding its replies.

/** The comments in display order, without the replies of hidden comments. */
internal fun List<CommentOrMore>.flatten(hiddenIds: Set<String>): List<FlatCommentItem> =
    buildList {
        for (item in this@flatten) {
            when (item) {
                is CommentOrMore.CommentItem -> addWithReplies(item.comment, hiddenIds)
                is CommentOrMore.More -> add(FlatCommentItem.MoreEntry(item.more))
            }
        }
    }

private fun MutableList<FlatCommentItem>.addWithReplies(
    comment: Comment,
    hiddenIds: Set<String>,
) {
    add(FlatCommentItem.CommentEntry(comment))
    if (comment.id in hiddenIds) return
    comment.replies.forEach { addWithReplies(it, hiddenIds) }
    comment.moreReplies?.takeIf { it.count > 0 && it.children.isNotEmpty() }?.let { add(FlatCommentItem.MoreEntry(it)) }
}

/** The first comment, in display order, that matches. */
internal fun List<CommentOrMore>.findComment(predicate: (Comment) -> Boolean): Comment? =
    filterIsInstance<CommentOrMore.CommentItem>().firstNotNullOfOrNull { it.comment.findInThread(predicate) }

private fun Comment.findInThread(predicate: (Comment) -> Boolean): Comment? =
    if (predicate(this)) this else replies.firstNotNullOfOrNull { it.findInThread(predicate) }

/** The IDs of all the comments here, with their replies. */
internal fun List<CommentOrMore>.commentIds(): Set<String> =
    buildSet {
        fun addThread(comment: Comment) {
            add(comment.id)
            comment.replies.forEach(::addThread)
        }
        this@commentIds.filterIsInstance<CommentOrMore.CommentItem>().forEach { addThread(it.comment) }
    }

/** The comments that [more] needn't load: those loaded, and those another "more" entry in their thread will load. */
internal fun List<CommentOrMore>.commentIdsCoveredOutside(more: MoreComments): Set<String> =
    buildSet {
        addAll(this@commentIdsCoveredOutside.commentIds())

        fun addMoreOf(comment: Comment) {
            comment.moreReplies?.takeIf { it.id != more.id }?.let { addAll(it.children) }
            comment.replies.forEach(::addMoreOf)
        }
        this@commentIdsCoveredOutside.filterIsInstance<CommentOrMore.CommentItem>().forEach { addMoreOf(it.comment) }
    }

/**
 * Newly loaded threads without the comments that are in [existing] or appear twice, since a list needs each comment only
 * once. A "more" entry can list a comment and its replies, and then the comment's thread includes those replies too.
 */
internal fun List<CommentOrMore>.withoutDuplicates(existing: List<CommentOrMore>): List<CommentOrMore> {
    val replyIds =
        filterIsInstance<CommentOrMore.CommentItem>()
            .flatMap {
                it.comment.replies
            }.map { CommentOrMore.CommentItem(it) }
            .commentIds()
    val seen = existing.commentIds().toMutableSet()

    fun Comment.newThread(): Comment? = if (seen.add(id)) copy(replies = replies.mapNotNull { it.newThread() }) else null

    return mapNotNull { item ->
        when (item) {
            is CommentOrMore.CommentItem -> {
                item.comment
                    .takeIf { it.id !in replyIds }
                    ?.newThread()
                    ?.let { CommentOrMore.CommentItem(it) }
            }

            is CommentOrMore.More -> {
                item
            }
        }
    }
}

/** Replaces the "more" entry with the threads it loaded. */
internal fun List<CommentOrMore>.withMoreComments(
    more: MoreComments,
    loaded: List<CommentOrMore>,
): List<CommentOrMore> {
    val addedReplies = loaded.filterIsInstance<CommentOrMore.CommentItem>().map { it.comment }
    val nextMore = loaded.filterIsInstance<CommentOrMore.More>().firstOrNull()?.more
    return flatMap { item ->
        if (item is CommentOrMore.More && item.more.id == more.id) {
            loaded
        } else {
            listOf(item).replaceComments { comment ->
                comment.takeIf { it.moreReplies?.id == more.id }?.copy(replies = comment.replies + addedReplies, moreReplies = nextMore)
            }
        }
    }
}

/** Replaces a comment's own fields, keeping the replies already in the tree. */
internal fun List<CommentOrMore>.withUpdatedComment(updated: Comment): List<CommentOrMore> =
    replaceComments { comment ->
        if (comment.id == updated.id) updated.copy(replies = comment.replies, moreReplies = comment.moreReplies) else null
    }

/** Adds a reply as the first reply of the comment named parentName. */
internal fun List<CommentOrMore>.withReply(
    parentName: String,
    reply: Comment,
): List<CommentOrMore> =
    replaceComments { comment ->
        if (comment.name == parentName) comment.copy(replies = listOf(reply.copy(depth = comment.depth + 1)) + comment.replies) else null
    }

internal fun List<CommentOrMore>.withoutComment(commentId: String): List<CommentOrMore> =
    mapNotNull { item ->
        when (item) {
            is CommentOrMore.CommentItem -> {
                item.comment.takeIf { it.id != commentId }?.let {
                    CommentOrMore.CommentItem(
                        it.withoutReply(commentId),
                    )
                }
            }

            is CommentOrMore.More -> {
                item
            }
        }
    }

private fun Comment.withoutReply(commentId: String): Comment =
    copy(replies = replies.filter { it.id != commentId }.map { it.withoutReply(commentId) })

// Rebuilds every comment in the tree. replace returns a comment's replacement, or null to keep the comment and look
// through its replies.
private fun List<CommentOrMore>.replaceComments(replace: (Comment) -> Comment?): List<CommentOrMore> =
    map { item ->
        when (item) {
            is CommentOrMore.CommentItem -> CommentOrMore.CommentItem(item.comment.replaceInThread(replace))
            is CommentOrMore.More -> item
        }
    }

private fun Comment.replaceInThread(replace: (Comment) -> Comment?): Comment =
    replace(this) ?: copy(replies = replies.map { it.replaceInThread(replace) })
