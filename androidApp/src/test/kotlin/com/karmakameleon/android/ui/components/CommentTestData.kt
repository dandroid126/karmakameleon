package com.karmakameleon.android.ui.components

import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.shared.domain.model.Comment
import org.junit.rules.ExternalResource
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

// A top-level comment. Tests change what they need with copy().
fun testComment(): Comment {
    val created = threeHoursAgo
    return Comment(
        id = "c1",
        name = "t1_c1",
        parentId = "t3_abc123",
        linkId = "t3_abc123",
        author = "commenter",
        body = "Sealed classes are great, but **value classes** are underrated.",
        bodyHtml = "",
        score = 42,
        created = created,
        createdUtc = created,
        isSubmitter = false,
        distinguished = null,
        isStickied = false,
        isLocked = false,
        isArchived = false,
        isSaved = false,
        isCollapsed = false,
        likes = null,
        depth = 0,
        replies = emptyList(),
        moreReplies = null,
        authorFlairText = null,
        authorFlairBackgroundColor = null,
        scoreHidden = false,
        edited = null,
        subreddit = "Kotlin",
        permalink = "/r/Kotlin/comments/abc123/_/c1/",
    )
}

/** Records which of a comment's actions ran, in order, and the comments it was updated to. */
class RecordedCommentActions {
    val calls = mutableListOf<String>()
    val updatedComments = mutableListOf<Comment>()

    val actions =
        CommentActions(
            onSelect = { calls += "select" },
            onDone = { calls += "done" },
            onShare = { calls += "share" },
            onReply = { calls += "reply" },
            onEdit = { calls += "edit" },
            onDelete = { calls += "delete" },
            onSave = { calls += "save" },
            onCommentUpdated = { updatedComments += it },
            onGoToCommentNav = { calls += "go to $it" },
            onHide = { calls += "hide" },
            onPrev = { calls += "prev" },
            onNext = { calls += "next" },
            onRoot = { calls += "root" },
            onParent = { calls += "parent" },
        )
}

/** Gives the components the [NavigationHandler] they get from Koin, recording where it was asked to go. */
class NavigationRule : ExternalResource() {
    val calls = mutableListOf<String>()

    private val handler =
        NavigationHandler().apply {
            onSubredditClick = { calls += "subreddit $it" }
            onUserClick = { calls += "user $it" }
        }

    override fun before() {
        startKoin { modules(module { single { handler } }) }
    }

    override fun after() {
        stopKoin()
    }
}
