package com.karmakameleon.android.ui.components

/** What a comment does when it or one of its controls is tapped. Thread navigation can be left out off its thread. */
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
    val onHide: () -> Unit = {},
    val onPrev: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onRoot: () -> Unit = {},
    val onParent: () -> Unit = {},
    val onTouchStart: () -> Unit = {},
    val onInlineImageClick: (String) -> Unit = {},
)
