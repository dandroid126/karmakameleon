package com.karmakameleon.shared.domain.model

import com.karmakameleon.shared.createTestComment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentTest {

    @Test
    fun voteState_upvoted() {
        val comment = createTestComment(likes = true)
        assertEquals(VoteState.UPVOTED, comment.voteState)
    }

    @Test
    fun voteState_downvoted() {
        val comment = createTestComment(likes = false)
        assertEquals(VoteState.DOWNVOTED, comment.voteState)
    }

    @Test
    fun voteState_none() {
        val comment = createTestComment(likes = null)
        assertEquals(VoteState.NONE, comment.voteState)
    }

    @Test
    fun createdInstant_convertsFromEpoch() {
        val comment = createTestComment(createdUtc = 1700000100L)
        assertEquals(1700000100L, comment.createdInstant.epochSeconds)
    }

    @Test
    fun isEdited_trueWhenEdited() {
        val comment = createTestComment(edited = 1700001000L)
        assertTrue(comment.isEdited)
    }

    @Test
    fun isEdited_falseWhenNotEdited() {
        val comment = createTestComment(edited = null)
        assertFalse(comment.isEdited)
    }
}
