package com.ahu.tlauncher.obd

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lets the UI run one-off commands (read/clear codes) on the adapter that the polling loop owns.
 * The loop calls [serve] between polls, so commands never interleave with live-data requests.
 */
class ObdLink {
    class Job(val cmd: String, val timeoutMs: Long, val deadlineNs: Long, val reply: CompletableDeferred<String>)

    private val jobs = Channel<Job>(Channel.UNLIMITED)

    /** True while the Diagnostics page is open: poll the extra live-data PIDs too. */
    val extended = MutableStateFlow(false)

    /** Runs [cmd] on the adapter; null when nothing answered in time. A timed-out command is dropped, never run later. */
    suspend fun exec(cmd: String, timeoutMs: Long = 5_000): String? {
        val waitMs = timeoutMs + 4_000
        val job = Job(cmd, timeoutMs, System.nanoTime() + waitMs * 1_000_000, CompletableDeferred())
        jobs.send(job)
        val r = withTimeoutOrNull(waitMs) { job.reply.await() }
        if (r == null) job.reply.cancel()
        return r
    }

    /** Called by a source between polls. */
    suspend fun serve(run: suspend (cmd: String, timeoutMs: Long) -> String) {
        while (true) {
            val job = jobs.tryReceive().getOrNull() ?: return
            if (job.reply.isCompleted || System.nanoTime() > job.deadlineNs) continue
            job.reply.complete(runCatching { run(job.cmd, job.timeoutMs) }.getOrDefault(""))
        }
    }
}
