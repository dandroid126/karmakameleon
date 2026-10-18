package com.karmakameleon.shared.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class RunSuspendCatchingTest {
    @Test
    fun runSuspendCatching_returnsValue() {
        assertEquals(42, runSuspendCatching { 42 }.getOrNull())
    }

    @Test
    fun runSuspendCatching_returnsException() {
        val exception = IllegalStateException("failed")
        assertSame(exception, runSuspendCatching { throw exception }.exceptionOrNull())
    }

    @Test
    fun runSuspendCatching_rethrowsCancellation() {
        assertFailsWith<CancellationException> {
            runSuspendCatching { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun runSuspendCatching_letsCancelledCoroutineStop() =
        runTest {
            var continuedAfterCancel = false
            val job =
                launch {
                    runSuspendCatching { awaitCancellation() }
                    continuedAfterCancel = true
                }
            yield()
            job.cancelAndJoin()
            assertFalse(continuedAfterCancel)
        }
}
