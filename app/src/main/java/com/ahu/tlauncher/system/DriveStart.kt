package com.ahu.tlauncher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.ahu.tlauncher.route.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A trip that was still running when the car was switched off. */
data class SavedTrip(val destination: Place, val startedAtMs: Long)

/** When the car "starts", and what to resume then. Pure, unit tested. */
object DriveStartRules {
    /** Off (screen off or asleep) for at least this long counts as the car having been switched off. */
    const val MIN_OFF_MS = 2 * 60_000L
    /** An unfinished trip older than this isn't offered again. */
    const val TRIP_MAX_AGE_MS = 12 * 60 * 60_000L
    /** Head units often restore their own audio (radio, Bluetooth) at start; wait this long before resuming music. */
    const val MUSIC_WAIT_MS = 8_000L

    /** Screen came back on after being off since [offAtElapsed] (elapsedRealtime). */
    fun afterScreenOn(offAtElapsed: Long?, nowElapsed: Long): Boolean =
        offAtElapsed != null && nowElapsed - offAtElapsed >= MIN_OFF_MS

    /**
     * The unit slept between two checks: elapsedRealtime keeps counting in deep sleep, uptimeMillis doesn't.
     * Head units that "power off" with the ignition usually just sleep, so this is the usual start signal.
     */
    fun slept(elapsedDelta: Long, uptimeDelta: Long): Boolean = elapsedDelta - uptimeDelta >= MIN_OFF_MS

    fun shouldResumeMusic(enabled: Boolean, wasPlaying: Boolean, accessGranted: Boolean, somethingPlaying: Boolean, inCall: Boolean): Boolean =
        enabled && wasPlaying && accessGranted && !somethingPlaying && !inCall

    /** The trip to offer ("Continue to X?"), or null. */
    fun tripToOffer(enabled: Boolean, saved: SavedTrip?, nowMs: Long, routeShowing: Boolean): SavedTrip? = when {
        !enabled || saved == null || routeShowing -> null
        nowMs - saved.startedAtMs !in 0..TRIP_MAX_AGE_MS -> null
        else -> saved
    }

    /**
     * Should ending this trip forget it? Yes when the user ended it or the car is at the destination; no when it
     * ended because the navigation app went away (often just the car switching off), so it can be offered again.
     */
    fun forgetOnEnd(byUser: Boolean, metresToDestination: Double?): Boolean =
        byUser || (metresToDestination != null && metresToDestination < 300)
}

/**
 * Counts "car starts": the app starting (cold boot), the screen coming back on after being off for a while, or the
 * unit waking from deep sleep. [starts] begins at 1 for the process start; consumers act once per new value.
 */
class DriveStartDetector(
    context: Context,
    scope: CoroutineScope,
    private val onScreenOff: () -> Unit = {},
    private val onScreenOn: () -> Unit = {},
) {
    private val _starts = MutableStateFlow(1)
    val starts: StateFlow<Int> = _starts.asStateFlow()
    private var screenOffAt: Long? = null

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val now = SystemClock.elapsedRealtime()
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> { if (screenOffAt == null) screenOffAt = now; onScreenOff() }
                    Intent.ACTION_SCREEN_ON -> {
                        onScreenOn()
                        if (DriveStartRules.afterScreenOn(screenOffAt, now)) bump()
                        screenOffAt = null
                    }
                }
            }
        }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }
        runCatching { ContextCompat.registerReceiver(context.applicationContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED) }
        // Deep sleep: coroutine delays stop while asleep, so after waking the next check sees the gap.
        scope.launch {
            var e = SystemClock.elapsedRealtime(); var u = SystemClock.uptimeMillis()
            while (true) {
                delay(30_000)
                val e2 = SystemClock.elapsedRealtime(); val u2 = SystemClock.uptimeMillis()
                // A screen-on after a long screen-off already counted this start.
                if (DriveStartRules.slept(e2 - e, u2 - u) && screenOffAt == null && !recentlyBumped(e2)) bump()
                e = e2; u = u2
            }
        }
    }

    private var bumpedAt = 0L
    private fun recentlyBumped(now: Long) = bumpedAt != 0L && now - bumpedAt < 60_000
    private fun bump() { bumpedAt = SystemClock.elapsedRealtime(); _starts.value += 1 }
}
