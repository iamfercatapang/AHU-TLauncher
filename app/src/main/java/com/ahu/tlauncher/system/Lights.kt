package com.ahu.tlauncher.system

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the head unit exposes its ILL (lights-on) wire. Chosen by the "learn" step in Settings. */
sealed interface LightsSource {
    data object Off : LightsSource
    /** The unit switches Android to night mode with the lights. */
    data object NightMode : LightsSource
    /** The unit dims the screen with the lights: on while brightness is below [threshold]. */
    data class Brightness(val threshold: Int) : LightsSource
    /** A system setting the unit's firmware flips with the lights (e.g. "system:ill_state" = "1"). */
    data class Setting(val key: String, val onValue: String) : LightsSource

    fun label(): String = when (this) {
        Off -> "Not set up"
        NightMode -> "Head unit night mode"
        is Brightness -> "Screen dimming (brightness below $threshold)"
        is Setting -> "Head unit setting ${key.substringAfter(':')}"
    }

    fun encode(): String = when (this) {
        Off -> "off"
        NightMode -> "night"
        is Brightness -> "brightness:$threshold"
        is Setting -> "setting:$key\n$onValue" // keys and values may contain ':'; neither contains a newline
    }

    companion object {
        fun decode(s: String?): LightsSource = when {
            s == null || s == "off" -> Off
            s == "night" -> NightMode
            s.startsWith("brightness:") -> s.removePrefix("brightness:").toIntOrNull()?.let { Brightness(it) } ?: Off
            s.startsWith("setting:") -> s.removePrefix("setting:").let { r ->
                val i = r.indexOf('\n'); if (i < 0) Off else Setting(r.substring(0, i), r.substring(i + 1))
            }
            else -> Off
        }
    }
}

/** Everything we can see that might follow the ILL wire. Settings keys are prefixed "system:" / "global:". */
data class LightsSnapshot(val night: Boolean, val brightness: Int?, val settings: Map<String, String>)

/** Pure logic, unit tested. */
object LightsLogic {
    private val noise = Regex("""(brightness|time|alarm|volume|ringtone|_count|usage|battery|wifi|bluetooth|airplane|location|lock|screen_off|auto_time)""", RegexOption.IGNORE_CASE)
    private val likely = Regex("""(ill|light|lamp|night|head|dim|dark|backlight)""", RegexOption.IGNORE_CASE)

    fun isOn(src: LightsSource, s: LightsSnapshot): Boolean = when (src) {
        LightsSource.Off -> false
        LightsSource.NightMode -> s.night
        is LightsSource.Brightness -> s.brightness != null && s.brightness < src.threshold
        is LightsSource.Setting -> s.settings[src.key] == src.onValue
    }

    /** Compares "lights off" and "lights on" snapshots and picks the most trustworthy signal, or null. */
    fun learn(off: LightsSnapshot, on: LightsSnapshot): LightsSource? {
        val changed = on.settings.keys.filter { k -> off.settings[k] != on.settings[k] && !noise.containsMatchIn(k) }
        val best = changed.sortedByDescending { if (likely.containsMatchIn(it)) 1 else 0 }.firstOrNull()
        if (best != null) return LightsSource.Setting(best, on.settings.getValue(best))
        if (on.night && !off.night) return LightsSource.NightMode
        val b0 = off.brightness; val b1 = on.brightness
        if (b0 != null && b1 != null && b1 < b0) return LightsSource.Brightness((b0 + b1 + 1) / 2)
        return null
    }
}

data class LightsState(val on: Boolean = false, val snapshot: LightsSnapshot = LightsSnapshot(false, null, emptyMap()), val recentChanges: List<String> = emptyList())

/** Watches night mode, screen brightness and system settings for the configured lights signal. */
class LightsMonitor(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(LightsState())
    val state: StateFlow<LightsState> = _state.asStateFlow()
    private val recent = ArrayDeque<String>()
    @Volatile private var source: LightsSource = LightsSource.Off

    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            uri?.let { u ->
                val table = u.pathSegments.firstOrNull() ?: "?"
                val key = u.lastPathSegment ?: return@let
                val v = read(table, key)
                recent.addFirst("$table:$key = ${v ?: "?"}"); while (recent.size > 6) recent.removeLast()
            }
            refresh()
        }
    }

    init {
        runCatching {
            context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
            context.contentResolver.registerContentObserver(Settings.Global.CONTENT_URI, true, observer)
        }
        context.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = refresh()
            @Deprecated("Deprecated in Java") override fun onLowMemory() {}
        })
        refresh()
    }

    fun setSource(src: LightsSource) { source = src; refresh() }

    fun refresh() {
        val snap = snapshot(includeAll = source is LightsSource.Setting)
        _state.value = LightsState(LightsLogic.isOn(source, snap), snap, recent.toList())
    }

    /** Current values. [includeAll] reads every readable system/global setting (used by the learn step). */
    fun snapshot(includeAll: Boolean = true): LightsSnapshot {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bright = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrNull()
        val settings = if (includeAll) readTable("system") + readTable("global") else {
            (source as? LightsSource.Setting)?.let { s -> read(s.key.substringBefore(':'), s.key.substringAfter(':'))?.let { mapOf(s.key to it) } } ?: emptyMap()
        }
        return LightsSnapshot(night, bright, settings)
    }

    private fun readTable(table: String): Map<String, String> = runCatching {
        val uri = if (table == "system") Settings.System.CONTENT_URI else Settings.Global.CONTENT_URI
        val out = HashMap<String, String>()
        context.contentResolver.query(uri, arrayOf("name", "value"), null, null, null)?.use { c ->
            while (c.moveToNext()) { val n = c.getString(0) ?: continue; out["$table:$n"] = c.getString(1) ?: "" }
        }
        out
    }.getOrDefault(emptyMap())

    private fun read(table: String, key: String): String? = runCatching {
        if (table == "global") Settings.Global.getString(context.contentResolver, key) else Settings.System.getString(context.contentResolver, key)
    }.getOrNull()
}
