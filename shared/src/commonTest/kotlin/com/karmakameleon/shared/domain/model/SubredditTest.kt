package com.karmakameleon.shared.domain.model

import com.karmakameleon.shared.createTestSubreddit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubredditTest {

    @Test
    fun isPublic_publicType() {
        val sub = createTestSubreddit(subredditType = "public")
        assertTrue(sub.isPublic)
        assertFalse(sub.isPrivate)
        assertFalse(sub.isRestricted)
    }

    @Test
    fun isPrivate_privateType() {
        val sub = createTestSubreddit(subredditType = "private")
        assertTrue(sub.isPrivate)
        assertFalse(sub.isPublic)
        assertFalse(sub.isRestricted)
    }

    @Test
    fun isRestricted_restrictedType() {
        val sub = createTestSubreddit(subredditType = "restricted")
        assertTrue(sub.isRestricted)
        assertFalse(sub.isPublic)
        assertFalse(sub.isPrivate)
    }
}
