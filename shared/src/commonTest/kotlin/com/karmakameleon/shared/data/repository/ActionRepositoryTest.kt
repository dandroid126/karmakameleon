package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.createTestComment
import com.karmakameleon.shared.createTestMessage
import com.karmakameleon.shared.createTestPost
import com.karmakameleon.shared.data.api.CommentOrMore
import com.karmakameleon.shared.data.api.RedditApiException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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

    // Runs its requests in the test's scope, which advanceUntilIdle runs, unlike backgroundScope.
    private fun TestScope.repository() = ActionRepository(api, this)

    // Collects the errors the repository reports while the test runs.
    private fun TestScope.errorsOf(repository: ActionRepository): List<String> {
        val errors = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.errors.toList(errors) }
        return errors
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
            advanceUntilIdle()
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
            advanceUntilIdle()

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
            advanceUntilIdle()

            assertEquals(listOf("you are not allowed to do that"), errors)
        }

    @Test
    fun vote_onAPostAlreadyShownWithTheVoteDoesNothing() =
        runTest {
            val repository = repository()

            repository.vote(createTestPost(id = "p1", likes = true), 1)
            advanceUntilIdle()

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
            advanceUntilIdle()

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
            advanceUntilIdle()

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
            advanceUntilIdle()

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
            advanceUntilIdle()

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
            advanceUntilIdle()

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
            advanceUntilIdle()

            assertFalse(post.withChanges(repository.changes.value).isSaved)
            assertEquals(listOf("Save failed"), errors)
        }

    @Test
    fun toggleHide_hidesAndUnhides() =
        runTest {
            val repository = repository()
            val post = createTestPost(id = "p1", isHidden = false)

            repository.toggleHide(post)
            advanceUntilIdle()
            assertTrue(post.withChanges(repository.changes.value).isHidden)
            repository.toggleHide(post)
            advanceUntilIdle()

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
            advanceUntilIdle()

            assertFalse(post.withChanges(repository.changes.value).isHidden)
            assertEquals(listOf("Hide failed"), errors)
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
