package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.data.api.CommentOrMore
import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.domain.model.Message
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.util.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the user changed on a post, comment or message in this app: a null field wasn't changed. [vote] is a direction
 * (1 up, -1 down, 0 none).
 */
data class ItemChanges(
    val vote: Int? = null,
    val isSaved: Boolean? = null,
    val isHidden: Boolean? = null,
)

/**
 * The only place that votes on, saves and hides posts, comments and messages, so an action works the same on every
 * screen. Each change shows straight away in [changes], keyed by the item's fullname (`t3_…`, `t1_…`, `t4_…`), and
 * every screen shows what it loaded with these changes on top, so a change made on one screen shows on all of them.
 * Then Reddit is told. If Reddit refuses, the change is undone and the error goes to [errors], which the app shows.
 *
 * A change stays in [changes] until the user logs in or out, so a list loaded before it, or Reddit's cached copy of
 * one, still shows it. Applying a change to an item that already has it changes nothing. Logging in or out drops them
 * all, since they were another account's, and stops sending any still waiting.
 *
 * Runs on the main thread, which keeps its bookkeeping free of locks: each request suspends instead of blocking it.
 */
class ActionRepository(
    redditApi: RedditApi,
    isLoggedIn: StateFlow<Boolean>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val _changes = MutableStateFlow<Map<String, ItemChanges>>(emptyMap())
    val changes: StateFlow<Map<String, ItemChanges>> = _changes.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Actions that failed, as messages to show the user. */
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val votes =
        Field(
            read = ItemChanges::vote,
            write = { changes, vote -> changes.copy(vote = vote) },
            failure = "Vote failed",
            send = { name, vote -> redditApi.vote(name, vote) },
        )
    private val saves =
        Field(
            read = ItemChanges::isSaved,
            write = { changes, saved -> changes.copy(isSaved = saved) },
            failure = "Save failed",
            send = { name, saved -> if (saved) redditApi.save(name) else redditApi.unsave(name) },
        )
    private val hides =
        Field(
            read = ItemChanges::isHidden,
            write = { changes, hidden -> changes.copy(isHidden = hidden) },
            failure = "Hide failed",
            send = { name, hidden -> if (hidden) redditApi.hide(name) else redditApi.unhide(name) },
        )

    init {
        isLoggedIn.drop(1).onEach { forgetChanges() }.launchIn(scope)
    }

    private fun forgetChanges() {
        listOf(votes, saves, hides).forEach { it.forget() }
        _changes.value = emptyMap()
    }

    /** Votes on [post] in [direction] (1 up, -1 down, 0 none). */
    fun vote(
        post: Post,
        direction: Int,
    ) = votes.change(post.name, shown = likesToVote(post.withChanges(changes.value).likes), wanted = direction)

    fun vote(
        comment: Comment,
        direction: Int,
    ) = votes.change(comment.name, shown = likesToVote(comment.withChanges(changes.value).likes), wanted = direction)

    fun vote(
        message: Message,
        direction: Int,
    ) = votes.change(message.name, shown = likesToVote(message.withChanges(changes.value).likes), wanted = direction)

    /** Saves [post], or unsaves it if it's saved. */
    fun toggleSave(post: Post) {
        val saved = post.withChanges(changes.value).isSaved
        saves.change(post.name, shown = saved, wanted = !saved)
    }

    fun toggleSave(comment: Comment) {
        val saved = comment.withChanges(changes.value).isSaved
        saves.change(comment.name, shown = saved, wanted = !saved)
    }

    /** Hides [post], or unhides it if it's hidden. */
    fun toggleHide(post: Post) {
        val hidden = post.withChanges(changes.value).isHidden
        hides.change(post.name, shown = hidden, wanted = !hidden)
    }

    /**
     * One kind of change, like votes. Only one request per item runs at a time: a change made while one runs waits for
     * it, then is sent if it still differs from what Reddit has, so the last change the user made is the one Reddit ends
     * up with. [send] returns false, or throws, when Reddit refuses.
     */
    private inner class Field<T : Any>(
        private val read: (ItemChanges) -> T?,
        private val write: (ItemChanges, T) -> ItemChanges,
        private val failure: String,
        private val send: suspend (name: String, value: T) -> Boolean,
    ) {
        // What Reddit has for each item with a request running.
        private val confirmed = mutableMapOf<String, T>()

        // Counts the times the account changed, so a request for the old one stops once it's answered.
        private var account = 0

        fun forget() {
            confirmed.clear()
            account++
        }

        fun change(
            name: String,
            shown: T,
            wanted: T,
        ) {
            if (wanted == shown) return
            val running = name in confirmed
            if (!running) confirmed[name] = shown
            set(name, wanted)
            if (!running) scope.launch { sync(name) }
        }

        private suspend fun sync(name: String) {
            val startAccount = account
            try {
                var wanted = unsent(name)
                while (wanted != null) {
                    val before = confirmed.getValue(name)
                    val result = runSuspendCatching { check(send(name, wanted)) { failure } }
                    if (account != startAccount) break
                    if (result.isSuccess) {
                        confirmed[name] = wanted
                    } else if (_changes.value[name]?.let(read) == wanted) {
                        // Nothing newer to send instead, so the item goes back to what Reddit has.
                        set(name, before)
                        _errors.emit(result.exceptionOrNull()?.message ?: failure)
                    }
                    wanted = unsent(name)
                }
            } finally {
                // After the account changed, the entry is a request for the new one, if there is one.
                if (account == startAccount) confirmed.remove(name)
            }
        }

        // The change to send next, if the item shows something other than what Reddit has.
        private fun unsent(name: String): T? = _changes.value[name]?.let(read)?.takeIf { it != confirmed.getValue(name) }

        private fun set(
            name: String,
            value: T,
        ) {
            _changes.update { it + (name to write(it[name] ?: ItemChanges(), value)) }
        }
    }
}

// A vote of 0 removes the vote, so it gives null likes rather than keeping the item's own.
private fun ItemChanges.likes(own: Boolean?): Boolean? = if (vote != null) voteToLikes(vote) else own

/** This post with the user's changes from [ActionRepository.changes] on top. */
fun Post.withChanges(changes: Map<String, ItemChanges>): Post {
    val change = changes[name] ?: return this
    val likes = change.likes(likes)
    return copy(
        likes = likes,
        score = score + likesToVote(likes) - likesToVote(this.likes),
        isSaved = change.isSaved ?: isSaved,
        isHidden = change.isHidden ?: isHidden,
    )
}

fun Comment.withChanges(changes: Map<String, ItemChanges>): Comment {
    val changedReplies = replies.map { it.withChanges(changes) }
    // Keeps the comment itself when nothing in its thread changed.
    val withReplies = if (changedReplies.indices.all { changedReplies[it] === replies[it] }) this else copy(replies = changedReplies)
    val change = changes[name] ?: return withReplies
    val likes = change.likes(likes)
    val changed =
        withReplies.copy(
            likes = likes,
            score = score + likesToVote(likes) - likesToVote(this.likes),
            isSaved = change.isSaved ?: isSaved,
        )
    return if (changed == withReplies) withReplies else changed
}

fun Message.withChanges(changes: Map<String, ItemChanges>): Message {
    val change = changes[name] ?: return this
    return copy(likes = change.likes(likes))
}

fun List<Post>.withPostChanges(changes: Map<String, ItemChanges>): List<Post> = map { it.withChanges(changes) }

fun List<Comment>.withCommentChanges(changes: Map<String, ItemChanges>): List<Comment> = map { it.withChanges(changes) }

/** A comment tree, with the user's changes on each comment and reply. */
fun List<CommentOrMore>.withTreeChanges(changes: Map<String, ItemChanges>): List<CommentOrMore> =
    map { item ->
        if (item !is CommentOrMore.CommentItem) return@map item
        val changed = item.comment.withChanges(changes)
        if (changed === item.comment) item else CommentOrMore.CommentItem(changed)
    }
