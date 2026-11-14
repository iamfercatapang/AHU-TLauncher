package com.ahu.tlauncher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln

/** What the user set. Band gains are in dB at each band's centre frequency. */
data class EqSettings(
    val enabled: Boolean = false,
    val preset: String = "Flat",
    val gainsDb: List<Float> = emptyList(),
    /** 0–100 %. */
    val bass: Int = 0,
    /** 0–100 % (up to +10 dB). */
    val loudness: Int = 0,
    val custom: Map<String, List<Float>> = emptyMap(),
)

/** What the head unit's audio system offers. */
data class EqCaps(val supported: Boolean, val freqsHz: List<Int>, val minDb: Float, val maxDb: Float, val bassSupported: Boolean, val loudnessSupported: Boolean)

/** Pure preset maths and storage, unit tested. */
object EqMath {
    /** Built-in curves: gain (dB) at reference frequencies (Hz); bands in between are interpolated on a log scale. */
    val PRESETS: Map<String, List<Pair<Int, Float>>> = linkedMapOf(
        "Flat" to listOf(60 to 0f, 14000 to 0f),
        "Bass boost" to listOf(60 to 6f, 150 to 4.5f, 400 to 1.5f, 1000 to 0f, 14000 to 0f),
        "Vocal" to listOf(60 to -2f, 250 to -1f, 1000 to 2.5f, 3000 to 3.5f, 6000 to 2f, 14000 to 0f),
        "Rock" to listOf(60 to 4.5f, 250 to 2f, 1000 to -1f, 3500 to 2f, 14000 to 4f),
        "Pop" to listOf(60 to -1f, 250 to 2f, 1000 to 3.5f, 3500 to 2f, 14000 to -1f),
        "Jazz" to listOf(60 to 3f, 250 to 1.5f, 1000 to -1f, 3500 to 1f, 14000 to 3f),
        "Classical" to listOf(60 to 3.5f, 250 to 1f, 1000 to -1f, 3500 to 1f, 14000 to 3.5f),
    )

    fun curve(points: List<Pair<Int, Float>>, freqs: List<Int>): List<Float> = freqs.map { f ->
        val x = ln(f.toDouble())
        val i = points.indexOfFirst { it.first >= f }
        when {
            i <= 0 -> points.first().second
            else -> {
                val (f0, g0) = points[i - 1]; val (f1, g1) = points[i]
                val t = (x - ln(f0.toDouble())) / (ln(f1.toDouble()) - ln(f0.toDouble()))
                (g0 + (g1 - g0) * t).toFloat()
            }
        }.let { Math.round(it * 2) / 2f } // half-dB steps
    }

    /** "31", "176", "1K", "2.4K", "14K". */
    fun freqLabel(hz: Int): String = when {
        hz < 1000 -> "$hz"
        hz % 1000 == 0 || hz >= 10_000 -> "${Math.round(hz / 1000.0)}K"
        else -> "%.1fK".format(java.util.Locale.US, hz / 1000.0)
    }

    fun encode(s: EqSettings): String = JSONObject()
        .put("on", s.enabled).put("preset", s.preset).put("gains", JSONArray(s.gainsDb.map { it.toDouble() }))
        .put("bass", s.bass).put("loud", s.loudness)
        .put("custom", JSONObject().apply { s.custom.forEach { (k, v) -> put(k, JSONArray(v.map { it.toDouble() })) } })
        .toString()

    fun decode(json: String?): EqSettings = runCatching {
        val o = JSONObject(json ?: return EqSettings())
        fun floats(a: JSONArray) = (0 until a.length()).map { a.getDouble(it).toFloat() }
        val c = o.optJSONObject("custom")
        EqSettings(
            enabled = o.optBoolean("on"), preset = o.optString("preset", "Flat"),
            gainsDb = o.optJSONArray("gains")?.let(::floats).orEmpty(),
            bass = o.optInt("bass"), loudness = o.optInt("loud"),
            custom = c?.keys()?.asSequence()?.associateWith { floats(c.getJSONArray(it)) }.orEmpty(),
        )
    }.getOrDefault(EqSettings())
}

/**
 * Android's audio effects on the global output mix (session 0) plus any session a music app opens
 * (ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, sent by e.g. Spotify). Audio the head unit routes through
 * its own sound chip (radio, sometimes Bluetooth) isn't affected.
 */
class SoundEq(private val context: Context) {
    private class Chain(val eq: Equalizer?, val bass: BassBoost?, val loud: LoudnessEnhancer?) {
        fun release() { runCatching { eq?.release() }; runCatching { bass?.release() }; runCatching { loud?.release() } }
    }

    private val chains = HashMap<Int, Chain>()
    private var settings = EqSettings()
    private val _caps = MutableStateFlow(EqCaps(false, listOf(60, 230, 910, 3600, 14000), -15f, 15f, false, false))
    val caps: StateFlow<EqCaps> = _caps.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val session = i?.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1) ?: return
            if (session <= 0) return
            when (i.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> attach(session)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> chains.remove(session)?.release()
            }
        }
    }

    init {
        val f = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        runCatching { ContextCompat.registerReceiver(context, receiver, f, ContextCompat.RECEIVER_EXPORTED) }
        attach(0) // global output mix
    }

    private fun attach(session: Int) {
        if (chains.containsKey(session)) return
        val eq = runCatching { Equalizer(0, session) }.getOrNull()
        val bass = runCatching { BassBoost(0, session) }.getOrNull()
        val loud = runCatching { LoudnessEnhancer(session) }.getOrNull()
        if (eq == null && bass == null && loud == null) return
        chains[session] = Chain(eq, bass, loud)
        if (session == 0 || !_caps.value.supported) eq?.let { e ->
            val n = e.numberOfBands.toInt()
            val range = e.bandLevelRange
            _caps.value = EqCaps(
                supported = n > 0,
                freqsHz = (0 until n).map { e.getCenterFreq(it.toShort()) / 1000 },
                minDb = range[0] / 100f, maxDb = range[1] / 100f,
                bassSupported = bass?.strengthSupported == true, loudnessSupported = loud != null,
            )
        }
        apply(settings)
    }

    /** Applies [s] to every attached session. */
    fun apply(s: EqSettings) {
        settings = s
        val caps = _caps.value
        chains.values.forEach { c ->
            runCatching {
                c.eq?.let { e ->
                    e.enabled = s.enabled
                    s.gainsDb.forEachIndexed { i, db ->
                        if (i < e.numberOfBands) e.setBandLevel(i.toShort(), (db.coerceIn(caps.minDb, caps.maxDb) * 100).toInt().toShort())
                    }
                }
                c.bass?.let { b -> b.enabled = s.enabled && s.bass > 0; if (b.strengthSupported) b.setStrength((s.bass * 10).toShort()) }
                c.loud?.let { l -> l.enabled = s.enabled && s.loudness > 0; l.setTargetGain(s.loudness * 10) }
            }
        }
    }
}
