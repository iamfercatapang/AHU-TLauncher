package com.ahu.tlauncher.notify

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaInfo(
    val packageName: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val updateTimeMs: Long,
    val speed: Float,
)

data class NavCardState(val info: NavInfo, val icon: Bitmap?, val contentIntent: PendingIntent?)

/** Central place for media-session and navigation-notification state. */
object NotificationHub {
    private val _media = MutableStateFlow<MediaInfo?>(null)
    val media: StateFlow<MediaInfo?> = _media.asStateFlow()

    private val _nav = MutableStateFlow<NavCardState?>(null)
    val nav: StateFlow<NavCardState?> = _nav.asStateFlow()

    private val _accessGranted = MutableStateFlow(false)
    val accessGranted: StateFlow<Boolean> = _accessGranted.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var sessionsListenerRegistered = false
    private var listenerComponent: ComponentName? = null
    private val navByKey = LinkedHashMap<String, NavCardState>()

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun accessSettingsIntent() = android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /** Call on resume and when the listener connects. Safe to call repeatedly. */
    fun refresh(context: Context) {
        val granted = hasAccess(context)
        _accessGranted.value = granted
        if (!granted) return
        val app = context.applicationContext
        val msm = sessionManager ?: (app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager).also { sessionManager = it }
        val cn = ComponentName(app, LauncherNotificationListener::class.java).also { listenerComponent = it }
        try {
            if (!sessionsListenerRegistered) {
                msm.addOnActiveSessionsChangedListener({ list -> pickController(list ?: emptyList()) }, cn, main)
                sessionsListenerRegistered = true
            }
            pickController(msm.getActiveSessions(cn))
        } catch (_: SecurityException) {
            _accessGranted.value = false
        }
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publish()
            // If another app started playing, follow it.
            if (state?.state != PlaybackState.STATE_PLAYING) {
                val msm = sessionManager; val cn = listenerComponent
                if (msm != null && cn != null) runCatching { pickController(msm.getActiveSessions(cn)) }
            }
        }
        override fun onSessionDestroyed() { controller = null; _media.value = null }
    }

    private fun pickController(list: List<MediaController>) {
        val current = controller
        val chosen = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { current != null && it.sessionToken == current.sessionToken }
            ?: list.firstOrNull()
        if (chosen?.sessionToken == controller?.sessionToken) { publish(); return }
        controller?.unregisterCallback(callback)
        controller = chosen
        chosen?.registerCallback(callback, main)
        publish()
    }

    private fun publish() {
        val c = controller ?: run { _media.value = null; return }
        val md = c.metadata
        val ps = c.playbackState
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title == null && ps == null) { _media.value = null; return }
        _media.value = MediaInfo(
            packageName = c.packageName,
            title = title ?: "",
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "",
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            isPlaying = ps?.state == PlaybackState.STATE_PLAYING || ps?.state == PlaybackState.STATE_BUFFERING,
            positionMs = ps?.position ?: 0L,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            updateTimeMs = ps?.lastPositionUpdateTime ?: 0L,
            speed = ps?.playbackSpeed ?: 1f,
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }
    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
    fun seekTo(ms: Long) { controller?.transportControls?.seekTo(ms) }
    fun mediaSessionActivity(): PendingIntent? = controller?.sessionActivity

    // --- navigation notifications (called from the listener service) ---
    internal fun onNavPosted(key: String, state: NavCardState) {
        navByKey.remove(key); navByKey[key] = state
        _nav.value = state
    }
    internal fun onNavRemoved(key: String) {
        navByKey.remove(key)
        _nav.value = navByKey.values.lastOrNull()
    }
    internal fun clearNav() { navByKey.clear(); _nav.value = null }
}
