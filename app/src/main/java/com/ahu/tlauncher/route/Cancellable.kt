package com.ahu.tlauncher.route

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but a cancelled coroutine stays cancelled. Each new search or route cancels the one
 * before it; plain runCatching caught that cancellation as a failure, so the old request carried on and
 * showed "… was cancelled" over the newer results.
 */
inline fun <T> runCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

/** Text for the search and route cards: our own readable messages, otherwise [fallback] (never an internal exception's text). */
fun userMessage(e: Throwable, fallback: String): String = (e as? ApiException)?.message?.takeIf { it.isNotBlank() } ?: fallback
