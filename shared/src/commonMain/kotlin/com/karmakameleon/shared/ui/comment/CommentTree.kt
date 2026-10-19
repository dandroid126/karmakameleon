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

/**
 * Turns comments loaded as a flat list, each pointing at its parent, into a tree. Comments whose parent isn't in the
 * list become top-level items, as do "more" entries whose parent isn't.
 */
fun buildCommentTree(flatItems: List<CommentOrMore>): List<CommentOrMore> {
    val comments = flatItems.filterIsInstance<CommentOrMore.CommentItem>().map { it.comment }
    val moreEntries = flatItems.filterIsInstance<CommentOrMore.More>()
    val commentByName = comments.associateByTo(mutableMapOf()) { it.name }

    val childNamesOf = comments.filter { it.parentId in commentByName }.groupBy({ it.parentId }, { it.name })
    val moreOf = moreEntries.filter { it.more.parentId in commentByName }.associate { it.more.parentId to it.more }

    // Deepest first, so each comment's replies are complete before it's attached to its parent.
    for (c in comments.sortedByDescending { it.depth }) {
        val childNames = childNamesOf[c.name]
        val more = moreOf[c.name]
        if (childNames != null || more != null) {
            val children = childNames?.map { commentByName.getValue(it) }.orEmpty()
            commentByName[c.name] = commentByName.getValue(c.name).copy(replies = children, moreReplies = more)
        }
    }

    return comments.filter { it.parentId !in commentByName }.map { CommentOrMore.CommentItem(commentByName.getValue(it.name)) } +
        moreEntries.filter { it.more.parentId !in commentByName }
}

/** Replaces the "more" entry with the comments it loaded, which [buildCommentTree] has already made a tree. */
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
