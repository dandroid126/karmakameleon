package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.createTestComment
import com.karmakameleon.shared.createTestMessage
import com.karmakameleon.shared.createTestPost
import com.karmakameleon.shared.data.api.CommentOrMore
import com.karmakameleon.shared.data.api.RedditApiException
import com.karmakameleon.shared.errorsOf
import com.karmakameleon.shared.util.markdown.MarkdownParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ActionRepositoryTest {
    private val api = FakeRedditApi()
    private val isLoggedIn = MutableStateFlow(true)

    // Runs in backgroundScope, since it follows the login state for as long as it runs.
    private fun TestScope.repository() =
        ActionRepository(
            api,
            CommentRepository(api, MarkdownParser(UnconfinedTestDispatcher(testScheduler))),
            isLoggedIn,
            backgroundScope,
        )

    // Runs what's waiting, requests included. advanceUntilIdle doesn't run backgroundScope's work.
    private fun TestScope.settle() {
        advanceTimeBy(60_000)
        runCurrent()
    }

    // ==================== Votes ====================

    @Test
    fun vote_showsStraightAwayAndTellsReddit() =
        runTest {
            val repository = repository()
            val post = createTestPost(id = "p1", score = 10, likes = null)

            repository.vote(post, 1)

            val shown = post.withChanges(repository.changes.value)
            assertEquals(true, shown.likes)
            assertEquals(11, shown.score)
            settle()
            assertEquals(listOf("vote t3_p1 1"), api.actions)
            assertEquals(true, post.withChanges(repository.changes.value).likes)
        }

    @Test
    fun vote_changesTheScoreByTheChangeInVote() =
        runTest {
            val repository = repository()
            val cases =
                listOf(
                    Triple(null, -1, 9),
                    Triple(true, 0, 9),
                    Triple(false, 0, 11),
                    Triple(true, -1, 8),
                    Triple(false, 1, 12),
                )
            cases.forEachIndexed { i, (likes, direction, score) ->
                val post = createTestPost(id = "p$i", score = 10, likes = likes)
                repository.vote(post, direction)
                assertEquals(score, post.withChanges(repository.changes.value).score, "likes $likes, vote $direction")
            }
        }

    @Test
    fun vote_isUndoneAndReportedWhenRedditRefuses() =
        runTest {
            val repository = repository()
            val errors = errorsOf(repository)
            api.voteResult = false
            val post = createTestPost(id = "p1", score = 10, likes = null)

            repository.vote(post, 1)
            settle()

            val shown = post.withChanges(repository.changes.value)
            assertNull(shown.likes)
            assertEquals(10, shown.score)
            assertEquals(listOf("Vote failed"), errors)
        }

    @Test
    fun vote_reportsRedditsReason() =
        runTest {
            val repository = repository()
            val errors = errorsOf(repository)
            api.shouldThrow = RedditApiException("THREAD_LOCKED", "you are not allowed to do that")

            repository.vote(createTestPost(id = "p1"), 1)
            settle()

            assertEquals(listOf("you are not allowed to do that"), errors)
        }

    @Test
    fun vote_onAPostAlreadyShownWithTheVoteDoesNothing() =
        runTest {
            val repository = repository()

            repository.vote(createTestPost(id = "p1", likes = true), 1)
            settle()

            assertTrue(api.actions.isEmpty())
            assertTrue(repository.changes.value.isEmpty())
        }

    @Test
    fun vote_madeWhileOneIsRunningIsSentAfterIt() =
        runTest {
            val repository = repository()
            api.actionDelayMillis = 1_000
            val post = createTestPost(id = "p1", score = 10, likes = null)

            repository.vote(post, 1)
            advanceTimeBy(500)
            repository.vote(post.withChanges(repository.changes.value), -1)
            settle()

            assertEquals(listOf("vote t3_p1 1", "vote t3_p1 -1"), api.actions)
            assertEquals(9, post.withChanges(repository.changes.value).score)
        }

    @Test
    fun vote_undoneWhileOneIsRunningIsNotSent() =
        runTest {
            val repository = repository()
            api.actionDelayMillis = 1_000
            val post = createTestPost(id = "p1", likes = null)

            repository.vote(post, 1)
            repository.vote(post.withChanges(repository.changes.value), -1)
            repository.vote(post.withChanges(repository.changes.value), 1)
            settle()

            assertEquals(listOf("vote t3_p1 1"), api.actions)
        }

    @Test
    fun vote_thatFailsKeepsANewerVote() =
        runTest {
            val repository = repository()
            val errors = errorsOf(repository)
            api.actionDelayMillis = 1_000
            api.voteResult = false
            val post = createTestPost(id = "p1", likes = null)

            repository.vote(post, 1)
            advanceTimeBy(500)
            repository.vote(post.withChanges(repository.changes.value), -1)
            advanceTimeBy(600)
            api.voteResult = true
            settle()

            assertEquals(listOf("vote t3_p1 1", "vote t3_p1 -1"), api.actions)
            assertEquals(false, post.withChanges(repository.changes.value).likes)
            assertTrue(errors.isEmpty())
        }

    @Test
    fun vote_onACommentAndAMessage() =
        runTest {
            val repository = repository()
            val comment = createTestComment(id = "c1", score = 5, likes = null)
            val message = createTestMessage(id = "m1", likes = null)

            repository.vote(comment, -1)
            repository.vote(message, 1)
            settle()

            assertEquals(4, comment.withChanges(repository.changes.value).score)
            assertEquals(true, message.withChanges(repository.changes.value).likes)
            assertEquals(listOf("vote t1_c1 -1", "vote t4_m1 1"), api.actions)
        }

    // ==================== Saves and hides ====================

    @Test
    fun toggleSave_savesAndUnsaves() =
        runTest {
            val repository = repository()
            val post = createTestPost(id = "p1", isSaved = false)
            val comment = createTestComment(id = "c1", isSaved = true)

            repository.toggleSave(post)
            repository.toggleSave(comment)
            settle()

            assertTrue(post.withChanges(repository.changes.value).isSaved)
            assertFalse(comment.withChanges(repository.changes.value).isSaved)
            assertEquals(listOf("save t3_p1", "unsave t1_c1"), api.actions)
        }

    @Test
    fun toggleSave_isUndoneWhenRedditRefuses() =
        runTest {
            val repository = repository()
            val errors = errorsOf(repository)
            api.saveResult = false
            val post = createTestPost(id = "p1", isSaved = false)

            repository.toggleSave(post)
            settle()

            assertFalse(post.withChanges(repository.changes.value).isSaved)
            assertEquals(listOf("Save failed"), errors)
        }

    @Test
    fun toggleHide_hidesAndUnhides() =
        runTest {
            val repository = repository()
            val post = createTestPost(id = "p1", isHidden = false)

            repository.toggleHide(post)
            settle()
            assertTrue(post.withChanges(repository.changes.value).isHidden)
            repository.toggleHide(post)
            settle()

            assertFalse(post.withChanges(repository.changes.value).isHidden)
            assertEquals(listOf("hide t3_p1", "unhide t3_p1"), api.actions)
        }

    @Test
    fun toggleHide_isUndoneWhenRedditRefuses() =
        runTest {
            val repository = repository()
            val errors = errorsOf(repository)
            api.hideResult = false
            val post = createTestPost(id = "p1", isHidden = false)

            repository.toggleHide(post)
            settle()

            assertFalse(post.withChanges(repository.changes.value).isHidden)
            assertEquals(listOf("Hide failed"), errors)
        }

    // ==================== Read state ====================

    @Test
    fun setIsNew_showsStraightAwayAndIsUndoneWhenRedditRefuses() =
        runTest {
            val repository = repository()
            val errors = errorsOf(repository)
            val message = createTestMessage(id = "m1", isNew = true)

            repository.setIsNew(message, isNew = false)
            assertFalse(message.withChanges(repository.changes.value).isNew)
            settle()
            assertEquals(listOf("read t4_m1"), api.actions)

            api.markMessageUnreadResult = false
            repository.setIsNew(message.withChanges(repository.changes.value), isNew = true)
            settle()

            assertFalse(message.withChanges(repository.changes.value).isNew)
            assertEquals(listOf("Failed to mark as unread"), errors)
        }

    @Test
    fun markAllRead_putsBackOnlyTheUnreadOnesWhenRedditRefuses() =
        runTest {
            val repository = repository()
            val errors = errorsOf(repository)
            api.markAllMessagesReadResult = false
            val unread = createTestMessage(id = "m1", isNew = true)
            val read = createTestMessage(id = "m2", isNew = false)

            repository.markAllRead(listOf(unread, read))
            assertFalse(unread.withChanges(repository.changes.value).isNew)
            settle()

            assertTrue(unread.withChanges(repository.changes.value).isNew)
            assertFalse(read.withChanges(repository.changes.value).isNew)
            assertEquals(listOf("Failed to mark all as read"), errors)
        }

    // ==================== Replies, edits, deletes and blocks ====================

    @Test
    fun reply_returnsTheNewCommentUpvoted() =
        runTest {
            val repository = repository()
            api.submitCommentResult = createTestComment(id = "new1", score = 0, likes = null)

            val reply = repository.reply("t1_c1", "Hello")

            assertEquals(listOf("t1_c1" to "Hello"), api.submittedComments)
            assertEquals(true, reply?.likes)
            assertEquals(1, reply?.score)
        }

    @Test
    fun reply_reportsAFailure() =
        runTest {
            val repository = repository()
            val errors = errorsOf(repository)
            api.shouldThrow = RedditApiException("RATELIMIT", "Take a break")

            assertNull(repository.reply("t1_c1", "Hello"))
            assertFalse(repository.reply(createTestMessage(id = "m1"), "Hello"))

            assertEquals(listOf("Take a break", "Take a break"), errors)
        }

    @Test
    fun replyToAMessage_postsIt() =
        runTest {
            val repository = repository()
            api.submitCommentResult = createTestComment(id = "new1")

            assertTrue(repository.reply(createTestMessage(id = "m1"), "Thanks"))
            assertEquals(listOf("t4_m1" to "Thanks"), api.submittedComments)
        }

    @Test
    fun edit_showsTheNewTextEverywhere() =
        runTest {
            val repository = repository()
            val comment = createTestComment(id = "c1", body = "Old")

            assertTrue(repository.edit(comment, "New"))

            val shown = comment.withChanges(repository.changes.value)
            assertEquals("New", shown.body)
            assertEquals("New", shown.sourceBody)
        }

    @Test
    fun edit_reportsAFailureAndKeepsTheText() =
        runTest {
            val repository = repository()
            val errors = errorsOf(repository)
            api.editCommentResult = false
            val comment = createTestComment(id = "c1", body = "Old")

            assertFalse(repository.edit(comment, "New"))

            assertEquals("Old", comment.withChanges(repository.changes.value).body)
            assertEquals(listOf("Edit failed"), errors)
        }

    @Test
    fun delete_takesTheCommentOutOfLists() =
        runTest {
            val repository = repository()
            val deleted = createTestComment(id = "c1")
            val kept = createTestComment(id = "c2")

            assertTrue(repository.delete(deleted))

            assertEquals(listOf(kept), listOf(deleted, kept).withCommentChanges(repository.changes.value))
            val tree = listOf(CommentOrMore.CommentItem(deleted), CommentOrMore.CommentItem(kept))
            assertEquals(listOf<CommentOrMore>(CommentOrMore.CommentItem(kept)), tree.withTreeChanges(repository.changes.value))
        }

    @Test
    fun delete_reportsAFailure() =
        runTest {
            val repository = repository()
            val errors = errorsOf(repository)
            api.deleteCommentResult = false

            assertFalse(repository.delete(createTestComment(id = "c1")))

            assertTrue(repository.changes.value.isEmpty())
            assertEquals(listOf("Delete failed"), errors)
        }

    @Test
    fun blockUser_reportsAFailure() =
        runTest {
            val repository = repository()
            val errors = errorsOf(repository)

            assertTrue(repository.blockUser("spammer"))
            api.blockUserResult = false
            assertFalse(repository.blockUser("spammer"))

            assertEquals(listOf("spammer", "spammer"), api.blockedUsers)
            assertEquals(listOf("Failed to block user"), errors)
        }

    @Test
    fun reportError_showsIt() =
        runTest {
            val repository = repository()
            val errors = errorsOf(repository)

            repository.reportError("Wait for the images to upload")

            assertEquals(listOf("Wait for the images to upload"), errors)
        }

    // ==================== Logging out ====================

    @Test
    fun loggingOut_dropsTheChanges() =
        runTest {
            val repository = repository()
            val post = createTestPost(id = "p1", isHidden = false, likes = null)
            repository.toggleHide(post)
            repository.vote(post, 1)
            settle()

            isLoggedIn.value = false
            settle()

            assertTrue(repository.changes.value.isEmpty())
            assertEquals(post, post.withChanges(repository.changes.value))
        }

    @Test
    fun loggingOut_stopsSendingWaitingChanges() =
        runTest {
            val repository = repository()
            api.actionDelayMillis = 1_000
            val post = createTestPost(id = "p1", likes = null)
            repository.vote(post, 1)
            advanceTimeBy(500)
            repository.vote(post.withChanges(repository.changes.value), -1)

            isLoggedIn.value = false
            runCurrent()
            settle()

            assertEquals(listOf("vote t3_p1 1"), api.actions)
            assertTrue(repository.changes.value.isEmpty())
        }

    @Test
    fun voteAfterLoggingIn_isSentWhileTheOldAccountsRequestFinishes() =
        runTest {
            val repository = repository()
            api.actionDelayMillis = 1_000
            val post = createTestPost(id = "p1", likes = null)
            repository.vote(post, 1)
            advanceTimeBy(500)

            // Each change is collected before the next, as a real logout and login are far apart.
            isLoggedIn.value = false
            runCurrent()
            isLoggedIn.value = true
            runCurrent()
            repository.vote(post, -1)

            // Only the new account's vote is left, and it's sent while the old account's request is still running.
            assertEquals(mapOf("t3_p1" to ItemChanges(vote = -1)), repository.changes.value)
            runCurrent()
            assertEquals(listOf("vote t3_p1 1", "vote t3_p1 -1"), api.actions)
            settle()

            assertEquals(listOf("vote t3_p1 1", "vote t3_p1 -1"), api.actions)
            assertEquals(false, post.withChanges(repository.changes.value).likes)
        }

    // ==================== Applying changes ====================

    @Test
    fun withChanges_appliedTwiceChangesNothingMore() {
        val changes = mapOf("t3_p1" to ItemChanges(vote = 1, isSaved = true))
        val post = createTestPost(id = "p1", score = 10, likes = null)

        val once = post.withChanges(changes)

        assertEquals(once, once.withChanges(changes))
        assertEquals(11, once.score)
    }

    @Test
    fun withTreeChanges_changesRepliesAndKeepsUnchangedComments() {
        val reply = createTestComment(id = "r1", parentId = "t1_c1", depth = 1, score = 3, likes = null)
        val changed = createTestComment(id = "c1", replies = listOf(reply))
        val unchanged = createTestComment(id = "c2")
        val tree = listOf(CommentOrMore.CommentItem(changed), CommentOrMore.CommentItem(unchanged))

        val result = tree.withTreeChanges(mapOf("t1_r1" to ItemChanges(vote = 1)))

        val newReply = (result[0] as CommentOrMore.CommentItem).comment.replies.single()
        assertEquals(true, newReply.likes)
        assertEquals(4, newReply.score)
        assertSame(tree[1], result[1])
    }
}
