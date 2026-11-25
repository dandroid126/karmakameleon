package com.karmakameleon.android.ui.components

import android.content.Context
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.ui.comment.CommentViewModel

/** What a comment does when it or one of its controls is tapped. Build it with [commentActions]. */
data class CommentActions(
    val onSelect: () -> Unit,
    val onDone: () -> Unit,
    val onShare: () -> Unit,
    val onReply: () -> Unit,
    val onEdit: () -> Unit,
    val onDelete: () -> Unit,
    val onSave: () -> Unit,
    val onVote: (direction: Int) -> Unit,
    val onGoToCommentNav: (commentId: String) -> Unit,
    val onHide: () -> Unit,
    val onPrev: () -> Unit,
    val onNext: () -> Unit,
    val onRoot: () -> Unit,
    val onParent: () -> Unit,
    val onTouchStart: () -> Unit,
    val onInlineImageClick: (String) -> Unit,
)

/**
 * What only the screen showing a comment knows: how it's selected, how a delete is confirmed, and where links, the
 * comment's thread and its images open. Everything else comes from [commentActions], the same on every screen.
 */
class CommentScreenActions(
    val select: (Comment) -> Unit,
    val deselect: () -> Unit,
    val confirmDelete: (Comment) -> Unit,
    val links: CommentLinks,
    val onTouchStart: (Comment) -> Unit,
    // Collapsing comments and moving between them only make sense in a thread, so a flat list of comments passes null.
    val thread: ThreadNavigation?,
)

/** Copying a comment's link, and opening a comment's thread at one of its comments, or an image in it. */
class CommentLinks(
    val copyLink: (String) -> Unit,
    val openThread: (comment: Comment, commentId: String) -> Unit,
    val openImage: (url: String) -> Unit,
)

/** Collapsing a comment in a thread, and moving to the previous or next top-level comment, its root or its parent. */
class ThreadNavigation(
    val hide: (Comment) -> Unit,
    val previous: (Comment) -> Unit,
    val next: (Comment) -> Unit,
    val root: (Comment) -> Unit,
    val parent: (Comment) -> Unit,
)

/**
 * The actions of [comment], built the same way on every screen. Votes, saves, replies and edits go through [comments],
 * whose actions go through the shared ActionRepository.
 */
fun commentActions(
    comment: Comment,
    comments: CommentViewModel,
    context: Context,
    screen: CommentScreenActions,
): CommentActions {
    val thread = screen.thread
    return CommentActions(
        onSelect = { screen.select(comment) },
        onDone = screen.deselect,
        onShare = { screen.links.copyLink("https://www.reddit.com${comment.permalink}") },
        onReply = { comments.setReplyingTo(comment.name) },
        onEdit = { comments.startEditComment(comment) },
        onDelete = { screen.confirmDelete(comment) },
        onSave = {
            comments.saveComment(comment)
            showShortToast(context, if (comment.isSaved) "Unsaved comment" else "Saved comment")
        },
        onVote = { comments.voteComment(comment, it) },
        onGoToCommentNav = { screen.links.openThread(comment, it) },
        onHide = { thread?.hide(comment) },
        onPrev = { thread?.previous(comment) },
        onNext = { thread?.next(comment) },
        onRoot = { thread?.root(comment) },
        onParent = { thread?.parent(comment) },
        onTouchStart = { screen.onTouchStart(comment) },
        onInlineImageClick = screen.links.openImage,
    )
}
