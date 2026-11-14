package com.ahu.tlauncher.system

import android.content.Context
import android.provider.Settings

/** Head-unit screen brightness (system-wide), as 1–100 %. Needs "Modify system settings". */
class BrightnessControl(private val context: Context) {
    fun canWrite(): Boolean = Settings.System.canWrite(context)

    fun get(): Int = runCatching {
        val v = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        BrightnessMath.toPercent(v)
    }.getOrDefault(50)

    /** Returns false without the permission. */
    fun set(percent: Int): Boolean {
        if (!canWrite()) return false
        return runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, BrightnessMath.toRaw(percent))
        }.isSuccess
    }
}

object BrightnessMath {
    fun toRaw(percent: Int): Int = (percent.coerceIn(1, 100) * 255 + 50) / 100
    fun toPercent(raw: Int): Int = ((raw.coerceIn(0, 255) * 100 + 127) / 255).coerceIn(1, 100)
}
