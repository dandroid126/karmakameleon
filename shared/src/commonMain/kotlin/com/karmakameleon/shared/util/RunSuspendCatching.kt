package com.karmakameleon.shared.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], for code that suspends: a [CancellationException] is rethrown instead of being returned as a
 * failure, so a cancelled coroutine stops. Catching specific exceptions instead wouldn't avoid this, since
 * [CancellationException] is an [IllegalStateException].
 *
 * Catches every [Exception] because a failed Reddit call can throw network, HTTP, parsing or configuration errors,
 * and missing one would crash the app instead of showing an error.
 */
@Suppress("TooGenericExceptionCaught") // See above.
inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
