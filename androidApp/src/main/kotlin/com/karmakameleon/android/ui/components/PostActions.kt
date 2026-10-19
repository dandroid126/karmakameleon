package com.karmakameleon.android.ui.components

/** What a post card does when the post or one of its controls is tapped. */
data class PostActions(
    val onClick: () -> Unit,
    val onSubredditClick: () -> Unit,
    val onUserClick: () -> Unit,
    val onUpvote: () -> Unit,
    val onDownvote: () -> Unit,
    val onSave: () -> Unit,
    val onHide: () -> Unit,
    val onBlockSubreddit: () -> Unit = {},
    val onLinkClick: (String) -> Unit = {},
    val onCrosspostClick: () -> Unit = {},
)
