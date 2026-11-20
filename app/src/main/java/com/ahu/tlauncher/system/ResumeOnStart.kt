package com.ahu.tlauncher.system

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.ahu.tlauncher.LauncherApp
import com.ahu.tlauncher.MainActivity
import com.ahu.tlauncher.notify.NotificationHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * When the car starts: plays the last song again if it was playing when the car was switched off, and offers
 * to continue an unfinished trip ([offer], shown as "Continue to X?" on the map), and brings the dashboard to the
 * front if the head unit reopened another app.
 */
class ResumeOnStart(private val app: LauncherApp, private val scope: CoroutineScope) {
    private val _offer = MutableStateFlow<SavedTrip?>(null)
    val offer: StateFlow<SavedTrip?> = _offer.asStateFlow()

    private val detector = DriveStartDetector(app, scope, onScreenOff = { app.lastMedia.freeze() }, onScreenOn = { app.lastMedia.unfreeze() })

    init {
        scope.launch { detector.starts.collectLatest { onStart(it) } }
        // A route being shown (started by hand, or from the offer) replaces the offer.
        scope.launch { app.routes.route.collect { if (it != null) _offer.value = null } }
    }

    private suspend fun onStart(start: Int) {
        app.carCare.onCarStart()
        val s = app.prefs.settings.first()
        _offer.value = DriveStartRules.tripToOffer(s.offerTrip, app.prefs.savedTrip(), System.currentTimeMillis(), app.routes.route.value != null)
        val carStart = DriveStartRules.isCarStart(start, SystemClock.elapsedRealtime())
        delay(DriveStartRules.FRONT_WAIT_MS)
        if (DriveStartRules.shouldBringToFront(
                enabled = app.prefs.settings.first().openOnStart, carStart = carStart, visible = app.dashboardVisible,
                inCall = inCall(), canStartFromBackground = Settings.canDrawOverlays(app),
            )) bringToFront()
        delay(DriveStartRules.MUSIC_WAIT_MS - DriveStartRules.FRONT_WAIT_MS)
        val playing = NotificationHub.media.value?.isPlaying == true
        val calling = inCall()
        val resume = DriveStartRules.shouldResumeMusic(
            enabled = app.prefs.settings.first().resumeMusic, wasPlaying = app.lastMedia.wasPlaying,
            accessGranted = NotificationHub.accessGranted.value, somethingPlaying = playing, inCall = calling,
        )
        // Don't open the music app over the dashboard if it can't be started in the background.
        if (resume) app.lastMedia.resume(openApp = {})
    }

    private fun inCall() = NotificationHub.call.value != null || app.calls.telecomState() != TelecomCall.NONE

    /**
     * Head units often reopen the last app (or their own launcher) when they wake, instead of the default home app.
     * Android only lets a background app open a screen with "Display over other apps" allowed.
     */
    private fun bringToFront() {
        runCatching {
            app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }.onFailure { Log.w("ResumeOnStart", "Couldn't bring the dashboard to the front", it) }
    }

    /** Reached a fuel stop on the way: offer to carry on to where the trip was going. */
    fun offerNow(p: com.ahu.tlauncher.route.Place) { _offer.value = SavedTrip(p, System.currentTimeMillis()) }

    /** "Continue" pressed: the caller previews and starts the route. */
    fun take(): SavedTrip? = _offer.value.also { _offer.value = null }

    /** ✕ on the offer: forget the trip. */
    fun dismiss() {
        _offer.value = null
        scope.launch { app.prefs.clearTrip() }
    }
}
