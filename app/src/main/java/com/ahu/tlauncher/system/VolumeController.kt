package com.ahu.tlauncher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VolumeState(val level: Int, val max: Int, val muted: Boolean) {
    val percent: Int get() = if (max == 0) 0 else level * 100 / max
}

class VolumeController(private val context: Context) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val stream = AudioManager.STREAM_MUSIC
    private val _state = MutableStateFlow(read())
    val state: StateFlow<VolumeState> = _state.asStateFlow()

    init {
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { _state.value = read() }
        }, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun read() = VolumeState(
        am.getStreamVolume(stream), am.getStreamMaxVolume(stream),
        runCatching { am.isStreamMute(stream) }.getOrDefault(false),
    )

    fun step(up: Boolean) {
        am.adjustStreamVolume(stream, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
        _state.value = read()
    }

    fun set(level: Int) {
        am.setStreamVolume(stream, level.coerceIn(0, am.getStreamMaxVolume(stream)), 0)
        _state.value = read()
    }

    fun toggleMute() {
        am.adjustStreamVolume(stream, AudioManager.ADJUST_TOGGLE_MUTE, 0)
        _state.value = read()
    }
}
