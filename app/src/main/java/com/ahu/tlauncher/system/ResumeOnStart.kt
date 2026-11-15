package com.ahu.tlauncher.system

import com.ahu.tlauncher.LauncherApp
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
 * to continue an unfinished trip ([offer], shown as "Continue to X?" on the map).
 */
class ResumeOnStart(private val app: LauncherApp, private val scope: CoroutineScope) {
    private val _offer = MutableStateFlow<SavedTrip?>(null)
    val offer: StateFlow<SavedTrip?> = _offer.asStateFlow()

    private val detector = DriveStartDetector(app, scope, onScreenOff = { app.lastMedia.freeze() }, onScreenOn = { app.lastMedia.unfreeze() })

    init {
        scope.launch { detector.starts.collectLatest { onStart() } }
        // A route being shown (started by hand, or from the offer) replaces the offer.
        scope.launch { app.routes.route.collect { if (it != null) _offer.value = null } }
    }

    private suspend fun onStart() {
        val s = app.prefs.settings.first()
        _offer.value = DriveStartRules.tripToOffer(s.offerTrip, app.prefs.savedTrip(), System.currentTimeMillis(), app.routes.route.value != null)
        delay(DriveStartRules.MUSIC_WAIT_MS)
        val playing = NotificationHub.media.value?.isPlaying == true
        val inCall = NotificationHub.call.value != null || app.calls.telecomState() != TelecomCall.NONE
        val resume = DriveStartRules.shouldResumeMusic(
            enabled = app.prefs.settings.first().resumeMusic, wasPlaying = app.lastMedia.wasPlaying,
            accessGranted = NotificationHub.accessGranted.value, somethingPlaying = playing, inCall = inCall,
        )
        // Don't open the music app over the dashboard if it can't be started in the background.
        if (resume) app.lastMedia.resume(openApp = {})
    }

    /** "Continue" pressed: the caller previews and starts the route. */
    fun take(): SavedTrip? = _offer.value.also { _offer.value = null }

    /** ✕ on the offer: forget the trip. */
    fun dismiss() {
        _offer.value = null
        scope.launch { app.prefs.clearTrip() }
    }
}
