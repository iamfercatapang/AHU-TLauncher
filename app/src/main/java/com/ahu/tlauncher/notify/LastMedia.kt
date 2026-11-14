package com.ahu.tlauncher.notify

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File

/**
 * Remembers the last song played in YouTube Music or Spotify, so after the head unit restarts the
 * music card can show it and Play can wake the app up again.
 */
class LastMedia(private val context: Context, private val scope: CoroutineScope) {
    companion object {
        val REMEMBERED = setOf("com.google.android.apps.youtube.music", "com.spotify.music")
        private const val ART_PX = 320
    }

    private val dir = context.filesDir
    private val infoFile get() = File(dir, "last_media.json")
    private val artFile get() = File(dir, "last_media_art.png")

    private val _last = MutableStateFlow<MediaInfo?>(null)
    val last: StateFlow<MediaInfo?> = _last.asStateFlow()
    private val _resuming = MutableStateFlow(false)
    /** True while waiting for the app to start playing after Play was pressed. */
    val resuming: StateFlow<Boolean> = _resuming.asStateFlow()

    private var savedTitle: String? = null
    private var savedAt = 0L

    init {
        scope.launch { _last.value = withContext(Dispatchers.IO) { load() } }
        scope.launch { NotificationHub.media.collect { m -> m?.let(::remember) } }
    }

    /** Saves the playing song (title change right away, position at most every 15 s). */
    private fun remember(m: MediaInfo) {
        if (m.packageName !in REMEMBERED || m.title.isBlank()) return
        _last.value = m.copy(isPlaying = false, queue = emptyList(), shuffleOn = null, repeatMode = null)
        val now = System.currentTimeMillis()
        val newSong = m.title != savedTitle
        if (!newSong && now - savedAt < 15_000 && m.isPlaying) return
        savedTitle = m.title; savedAt = now
        val art = if (newSong) m.art else null
        scope.launch(Dispatchers.IO) {
            runCatching {
                infoFile.writeText(
                    JSONObject().put("pkg", m.packageName).put("title", m.title).put("artist", m.artist)
                        .put("pos", m.positionMs).put("dur", m.durationMs).toString(),
                )
                if (newSong) {
                    if (art != null) artFile.outputStream().use { scaled(art).compress(Bitmap.CompressFormat.PNG, 100, it) } else artFile.delete()
                }
            }
        }
    }

    private fun scaled(b: Bitmap): Bitmap {
        val s = ART_PX.toFloat() / maxOf(b.width, b.height)
        return if (s >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
    }

    private fun load(): MediaInfo? = runCatching {
        val o = JSONObject(infoFile.readText())
        MediaInfo(
            packageName = o.getString("pkg"), title = o.getString("title"), artist = o.optString("artist"),
            art = if (artFile.exists()) BitmapFactory.decodeFile(artFile.path) else null,
            isPlaying = false, positionMs = o.optLong("pos"), durationMs = o.optLong("dur"), updateTimeMs = 0, speed = 1f,
        )
    }.getOrNull()

    /**
     * Play pressed with nothing running: connect to the app's media service (the one Android Auto uses)
     * and send play. If it doesn't start within a few seconds, open the app instead.
     */
    fun resume(openApp: (String) -> Unit) {
        val m = _last.value ?: return
        if (_resuming.value) return
        _resuming.value = true
        scope.launch {
            val ok = viaMediaBrowser(m.packageName) && playingWithin(6_000)
            _resuming.value = false
            if (!ok) openApp(m.packageName)
        }
    }

    private suspend fun playingWithin(ms: Long) = withTimeoutOrNull(ms) {
        NotificationHub.media.first { it?.isPlaying == true }
    } != null

    private suspend fun viaMediaBrowser(pkg: String): Boolean {
        val service = runCatching {
            context.packageManager.queryIntentServices(Intent("android.media.browse.MediaBrowserService").setPackage(pkg), 0).firstOrNull()?.serviceInfo
        }.getOrNull() ?: return false
        val connected = kotlinx.coroutines.CompletableDeferred<Boolean>()
        var browser: MediaBrowserCompat? = null
        browser = MediaBrowserCompat(context, ComponentName(service.packageName, service.name), object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() { connected.complete(true) }
            override fun onConnectionFailed() { connected.complete(false) }
            override fun onConnectionSuspended() { connected.complete(false) }
        }, null)
        runCatching { browser.connect() }.onFailure { return false }
        val ok = withTimeoutOrNull(5_000) { connected.await() } == true
        if (ok) {
            runCatching { MediaControllerCompat(context, browser.sessionToken).transportControls.play() }
            // Keep the connection briefly so the app's service stays up while it starts playing.
            scope.launch { delay(10_000); runCatching { browser.disconnect() } }
        } else runCatching { browser.disconnect() }
        return ok
    }
}
