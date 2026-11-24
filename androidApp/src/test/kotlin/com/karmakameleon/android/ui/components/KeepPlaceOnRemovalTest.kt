package com.karmakameleon.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeepPlaceOnRemovalTest {
    private val keys = listOf("a", "b", "c", "d", "e")

    @Test
    fun removedFirstItemOnScreenGivesItsPlaceToTheNext() {
        assertEquals(2, indexAfterRemoval(keys, listOf("a", "b", "d", "e"), firstKey = "c", firstIndex = 2))
    }

    // A deleted comment goes with its replies, which follow it.
    @Test
    fun itemsRemovedWithItGiveTheirPlaceToTheNextLeft() {
        assertEquals(2, indexAfterRemoval(keys, listOf("a", "b", "e"), firstKey = "c", firstIndex = 2))
    }

    // Like blocking a subreddit that posted both the one on screen and one above it.
    @Test
    fun itemsRemovedAboveItMoveThePlaceUp() {
        assertEquals(1, indexAfterRemoval(keys, listOf("a", "d", "e"), firstKey = "c", firstIndex = 2))
    }

    @Test
    fun removedFirstItemGivesTheTopToTheNext() {
        assertEquals(0, indexAfterRemoval(keys, listOf("b", "c", "d", "e"), firstKey = "a", firstIndex = 0))
    }

    // Like the subreddit's header above its posts.
    @Test
    fun countsTheItemsAboveTheseOnes() {
        assertEquals(3, indexAfterRemoval(keys, listOf("a", "b", "d", "e"), firstKey = "c", firstIndex = 3))
    }

    @Test
    fun lastItemRemovedLeavesThePlaceAfterTheRest() {
        assertEquals(4, indexAfterRemoval(keys, listOf("a", "b", "c", "d"), firstKey = "e", firstIndex = 4))
    }

    @Test
    fun leavesTheListAloneWhenTheFirstItemOnScreenIsStillThere() {
        assertNull(indexAfterRemoval(keys, listOf("a", "b", "c", "e"), firstKey = "c", firstIndex = 2))
    }

    @Test
    fun leavesTheListAloneWhenTheFirstItemOnScreenIsNotOneOfThese() {
        assertNull(indexAfterRemoval(keys, listOf("a", "b", "d", "e"), firstKey = "header", firstIndex = 0))
    }

    // New contents start at the top instead, and comments loaded in place of "load more" show where it was.
    @Test
    fun leavesTheListAloneWhenNewItemsCame() {
        assertNull(indexAfterRemoval(keys, listOf("a", "b", "x", "y", "d", "e"), firstKey = "c", firstIndex = 2))
    }

    @Test
    fun leavesTheListAloneWhenNoneAreLeft() {
        assertNull(indexAfterRemoval(keys, emptyList(), firstKey = "c", firstIndex = 2))
    }
}
