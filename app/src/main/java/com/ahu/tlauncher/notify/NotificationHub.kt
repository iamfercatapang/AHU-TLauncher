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
    /** Queue shared by the app (empty if it doesn't share one). */
    val queue: List<QueueEntry> = emptyList(),
    val activeQueueId: Long = -1,
    /** null = the app doesn't offer it. */
    val shuffleOn: Boolean? = null,
    /** 0 off, 1 one, 2 all; null = not offered. Same values as PlaybackStateCompat. */
    val repeatMode: Int? = null,
)

data class QueueEntry(
    val id: Long,
    val title: String,
    val subtitle: String,
    val art: Bitmap?,
    val artUri: String?,
    val durationMs: Long?,
)

/** A call notification plus its button intents. */
data class CallCardState(val info: CallInfo, val intents: Map<CallAction, PendingIntent>, val contentIntent: PendingIntent?, val firstSeenMs: Long)

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
    private var compat: android.support.v4.media.session.MediaControllerCompat? = null
    private var appContext: Context? = null
    private var sessionsListenerRegistered = false
    private var listenerComponent: ComponentName? = null
    private val navByKey = LinkedHashMap<String, NavCardState>()
    private val callByKey = LinkedHashMap<String, CallCardState>()

    private val _call = MutableStateFlow<CallCardState?>(null)
    val call: StateFlow<CallCardState?> = _call.asStateFlow()

    /** Package of the last app that posted a call notification (shown in Settings to help debugging). */
    @Volatile var lastCallPackage: String? = null; private set

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun accessSettingsIntent() = android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /** Call on resume and when the listener connects. Safe to call repeatedly. */
    fun refresh(context: Context) {
        val granted = hasAccess(context)
        _accessGranted.value = granted
        if (!granted) return
        val app = context.applicationContext
        appContext = app
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
        override fun onSessionDestroyed() { controller = null; compat = null; _media.value = null }
        override fun onQueueChanged(queue: MutableList<android.media.session.MediaSession.QueueItem>?) = publish()
    }

    private val compatCallback = object : android.support.v4.media.session.MediaControllerCompat.Callback() {
        override fun onShuffleModeChanged(shuffleMode: Int) = publish()
        override fun onRepeatModeChanged(repeatMode: Int) = publish()
        override fun onSessionReady() = publish()
    }

    private fun pickController(list: List<MediaController>) {
        val current = controller
        val chosen = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { current != null && it.sessionToken == current.sessionToken }
            ?: list.firstOrNull()
        if (chosen?.sessionToken == controller?.sessionToken) { publish(); return }
        controller?.unregisterCallback(callback)
        compat?.unregisterCallback(compatCallback)
        controller = chosen
        chosen?.registerCallback(callback, main)
        // The compat wrapper exposes shuffle / repeat, which the framework controller doesn't.
        compat = chosen?.let { c ->
            appContext?.let { ctx ->
                runCatching {
                    android.support.v4.media.session.MediaControllerCompat(ctx, android.support.v4.media.session.MediaSessionCompat.Token.fromToken(c.sessionToken))
                        .also { it.registerCallback(compatCallback, main) }
                }.getOrNull()
            }
        }
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
            queue = runCatching { c.queue }.getOrNull().orEmpty().take(60).map { q ->
                val d = q.description
                QueueEntry(
                    id = q.queueId,
                    title = d.title?.toString().orEmpty(),
                    subtitle = d.subtitle?.toString().orEmpty(),
                    art = d.iconBitmap,
                    artUri = d.iconUri?.toString(),
                    durationMs = d.extras?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 },
                )
            },
            activeQueueId = ps?.activeQueueItemId ?: -1,
            shuffleOn = shuffleState(ps),
            repeatMode = repeatState(ps),
        )
    }

    private const val ACTION_SET_REPEAT_MODE = 1L shl 18
    private const val ACTION_SET_SHUFFLE_MODE = 1L shl 21

    private fun customAction(ps: PlaybackState?, word: String) =
        ps?.customActions?.firstOrNull { it.action.contains(word, true) || it.name.toString().contains(word, true) }

    private fun shuffleState(ps: PlaybackState?): Boolean? {
        val mode = compat?.takeIf { it.isSessionReady }?.shuffleMode ?: -1
        val supported = ps != null && (ps.actions and ACTION_SET_SHUFFLE_MODE) != 0L
        return when {
            supported && mode >= 0 -> mode != 0
            customAction(ps, "shuffle") != null -> customAction(ps, "shuffle")!!.let { a ->
                // e.g. Spotify names the action after what it does next ("Shuffle on" / "Shuffle off")
                val n = (a.action + " " + a.name).lowercase(); n.contains("off") || n.contains("disable")
            }
            else -> null
        }
    }

    private fun repeatState(ps: PlaybackState?): Int? {
        val mode = compat?.takeIf { it.isSessionReady }?.repeatMode ?: -1
        val supported = ps != null && (ps.actions and ACTION_SET_REPEAT_MODE) != 0L
        return when {
            supported && mode >= 0 -> if (mode == 3) 2 else mode // GROUP counts as all
            customAction(ps, "repeat") != null -> 0
            else -> null
        }
    }

    fun toggleShuffle() {
        val m = _media.value ?: return
        val ps = controller?.playbackState
        val cc = compat
        if (cc != null && cc.isSessionReady && ps != null && (ps.actions and ACTION_SET_SHUFFLE_MODE) != 0L) {
            cc.transportControls.setShuffleMode(if (m.shuffleOn == true) android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_NONE else android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_ALL)
        } else customAction(ps, "shuffle")?.let { controller?.transportControls?.sendCustomAction(it, null) }
    }

    /** Off → all → one → off. */
    fun cycleRepeat() {
        val m = _media.value ?: return
        val ps = controller?.playbackState
        val cc = compat
        if (cc != null && cc.isSessionReady && ps != null && (ps.actions and ACTION_SET_REPEAT_MODE) != 0L) {
            cc.transportControls.setRepeatMode(when (m.repeatMode) { 0 -> android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ALL; 2 -> android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ONE; else -> android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_NONE })
        } else customAction(ps, "repeat")?.let { controller?.transportControls?.sendCustomAction(it, null) }
    }

    fun playQueueItem(id: Long) { controller?.transportControls?.skipToQueueItem(id) }

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

    // --- call notifications ---
    internal fun onCallPosted(key: String, state: CallCardState) {
        lastCallPackage = state.info.packageName
        val prev = callByKey.remove(key)
        // Keep the time we first saw the call as answered, for the in-call timer.
        val keep = if (prev != null && prev.info.phase == state.info.phase) state.copy(firstSeenMs = prev.firstSeenMs) else state
        callByKey[key] = keep
        _call.value = keep
    }
    internal fun onCallRemoved(key: String) {
        if (callByKey.remove(key) != null) _call.value = callByKey.values.lastOrNull()
    }
    internal fun clearCalls() { callByKey.clear(); _call.value = null }

    /** Presses a button on the call notification. False if it has no such button. */
    fun callAction(a: CallAction): Boolean {
        val pi = _call.value?.intents?.get(a) ?: return false
        return runCatching { pi.send(); true }.getOrDefault(false)
    }
}
