package com.ahu.tlauncher

import com.ahu.tlauncher.route.ApiException
import com.ahu.tlauncher.route.runCancellable
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellableTest {
    /** Typing quickly cancels the previous search: it must not write its cancellation over the newer results. */
    @Test fun cancelledSearchDoesNotShowAnError() = runBlocking {
        var error: String? = null
        var results = "none"
        val old = launch(start = CoroutineStart.UNDISPATCHED) {
            runCancellable { delay(10_000); "old results" }
                .onSuccess { results = it }
                .onFailure { error = it.message }
        }
        old.cancel()
        // the newer search finishes
        runCancellable { yield(); "new results" }.onSuccess { results = it }.onFailure { error = it.message }
        old.join()
        assertNull(error)
        assertEquals("new results", results)
        assertTrue(old.isCancelled)
    }

    @Test fun realFailuresAreStillReported() {
        val r = runCancellable<String> { throw ApiException("No internet connection") }
        assertEquals("No internet connection", r.exceptionOrNull()?.message)
        assertEquals(5, runCancellable { 2 + 3 }.getOrNull())
    }

    @Test fun onlyReadableMessagesReachTheScreen() {
        assertEquals("No internet connection", com.ahu.tlauncher.route.userMessage(ApiException("No internet connection"), "Couldn't search"))
        assertEquals("Couldn't search", com.ahu.tlauncher.route.userMessage(org.json.JSONException("Value <html> of type java.lang.String"), "Couldn't search"))
        assertEquals("Couldn't search", com.ahu.tlauncher.route.userMessage(IllegalStateException(), "Couldn't search"))
    }
}
