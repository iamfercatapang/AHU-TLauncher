package com.ahu.tlauncher.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.telecom.TelecomManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.ahu.tlauncher.notify.CallAction
import com.ahu.tlauncher.notify.NotificationHub

/** What Android's own call system reports (works on head units whose Bluetooth calls go through Telecom). */
enum class TelecomCall { NONE, RINGING, IN_CALL }

/**
 * Answers / ends calls. Tries, in order: the button on the phone app's call notification,
 * Android's call system (needs the "Phone" permissions), then the headset-button key event.
 */
class CallControl(private val context: Context) {
    private val telecom get() = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    private val audio get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    fun hasPermissions() = granted(Manifest.permission.READ_PHONE_STATE) && granted(Manifest.permission.ANSWER_PHONE_CALLS)

    @SuppressLint("MissingPermission")
    fun telecomState(): TelecomCall {
        if (!granted(Manifest.permission.READ_PHONE_STATE)) return TelecomCall.NONE
        val t = telecom ?: return TelecomCall.NONE
        return runCatching {
            if (!t.isInCall) return@runCatching TelecomCall.NONE // isInCall also covers ringing
            @Suppress("DEPRECATION")
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            val ringing = runCatching { tm?.callState == android.telephony.TelephonyManager.CALL_STATE_RINGING }.getOrDefault(false)
            if (ringing) TelecomCall.RINGING else TelecomCall.IN_CALL
        }.getOrDefault(TelecomCall.NONE)
    }

    @SuppressLint("MissingPermission")
    fun answer() {
        if (NotificationHub.callAction(CallAction.ANSWER)) return
        if (granted(Manifest.permission.ANSWER_PHONE_CALLS) && runCatching { telecom?.acceptRingingCall() }.isSuccess) return
        headsetHook()
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun end(ringing: Boolean) {
        if (NotificationHub.callAction(if (ringing) CallAction.DECLINE else CallAction.HANG_UP)) return
        if (NotificationHub.callAction(if (ringing) CallAction.HANG_UP else CallAction.DECLINE)) return
        if (Build.VERSION.SDK_INT >= 28 && granted(Manifest.permission.ANSWER_PHONE_CALLS) &&
            runCatching { telecom?.endCall() == true }.getOrDefault(false)
        ) return
        if (!ringing) headsetHook() // a single press ends an active call
    }

    /** Mute: the notification's own button if it has one, otherwise the microphone switch. Returns the new state if known. */
    fun toggleMute(currentlyMuted: Boolean): Boolean {
        if (NotificationHub.callAction(CallAction.MUTE)) return !currentlyMuted
        audio.isMicrophoneMute = !currentlyMuted
        return audio.isMicrophoneMute
    }

    fun micMuted(): Boolean = runCatching { audio.isMicrophoneMute }.getOrDefault(false)

    fun switchAudio(): Boolean = NotificationHub.callAction(CallAction.AUDIO_SWITCH)

    private fun headsetHook() {
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK, 0))
    }
}
