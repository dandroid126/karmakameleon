package com.karmakameleon.android.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// PendingQuote and PendingNotificationAction are objects, so each test clears what it left behind.
class PendingStateTest {
    @After
    fun clear() {
        PendingQuote.consume()
        PendingNotificationAction.consumeOpenInboxUnread()
    }

    @Test
    fun quoteIsEmptyUntilSet() {
        assertNull(PendingQuote.text.value)
        assertNull(PendingQuote.consume())
    }

    @Test
    fun consumingQuoteReturnsItOnce() {
        PendingQuote.set("quoted text")
        assertEquals("quoted text", PendingQuote.text.value)

        assertEquals("quoted text", PendingQuote.consume())
        assertNull(PendingQuote.text.value)
        assertNull(PendingQuote.consume())
    }

    @Test
    fun newQuoteReplacesOld() {
        PendingQuote.set("first")
        PendingQuote.set("second")
        assertEquals("second", PendingQuote.consume())
    }

    @Test
    fun openInboxUnreadIsSetUntilConsumed() {
        assertFalse(PendingNotificationAction.openInboxUnread.value)

        PendingNotificationAction.triggerOpenInboxUnread()
        assertTrue(PendingNotificationAction.openInboxUnread.value)

        PendingNotificationAction.consumeOpenInboxUnread()
        assertFalse(PendingNotificationAction.openInboxUnread.value)
    }
}
