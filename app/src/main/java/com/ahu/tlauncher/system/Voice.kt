package com.ahu.tlauncher.system

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Spoken turn-by-turn for the free map, through the head unit's text-to-speech.
 * Music is ducked while a sentence is spoken (navigation-guidance audio focus).
 */
class Voice(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var ready = false
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = if (Build.VERSION.SDK_INT >= 26) AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build() else null

    private val tts: TextToSpeech by lazy {
        TextToSpeech(app) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts.language = Locale.getDefault().takeIf { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE } ?: Locale.US
                tts.setAudioAttributes(attrs)
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) = release()
                    @Deprecated("Deprecated in Java") override fun onError(id: String?) = release()
                })
                pending?.let { speak(it) }; pending = null
            }
        }
    }
    private var pending: String? = null

    fun speak(text: String) {
        val t = tts
        if (!ready) { pending = text; return }
        if (Build.VERSION.SDK_INT >= 26) focus?.let { audio.requestAudioFocus(it) }
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav-" + System.nanoTime())
    }

    fun stop() { if (ready) tts.stop(); release() }

    private fun release() { if (Build.VERSION.SDK_INT >= 26) focus?.let { audio.abandonAudioFocusRequest(it) } }
}
