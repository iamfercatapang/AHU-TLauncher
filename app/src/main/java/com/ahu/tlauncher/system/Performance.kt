package com.ahu.tlauncher.system

import android.app.ActivityManager
import android.content.Context

/** What this head unit has, read once at start. */
data class DeviceSpec(val ramBytes: Long, val cores: Int, val lowRamFlag: Boolean) {
    val ramGb: Double get() = ramBytes / GIB

    /** "2 GB RAM · 4 cores", for Settings. */
    fun label(): String = "${"%.0f".format(Math.ceil(ramGb))} GB RAM · $cores cores"

    companion object {
        const val GIB = 1024.0 * 1024.0 * 1024.0

        fun read(ctx: Context): DeviceSpec {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
            return DeviceSpec(mem.totalMem, Runtime.getRuntime().availableProcessors(), am?.isLowRamDevice == true)
        }
    }
}

/**
 * Low-performance mode: fewer animations, slower screen updates and a lighter map. Pure, unit tested.
 * Cheap head units usually have 1–2 GB of RAM; a "2 GB" unit reports about 1.8 GiB, a "3 GB" one about 2.8.
 */
object PerfPolicy {
    const val LOW_RAM_GIB = 2.5
    const val MIN_CORES = 4

    fun isLowEnd(spec: DeviceSpec): Boolean =
        spec.lowRamFlag || spec.ramBytes in 1 until (LOW_RAM_GIB * DeviceSpec.GIB).toLong() || spec.cores < MIN_CORES

    fun active(mode: com.ahu.tlauncher.data.PerfMode, spec: DeviceSpec): Boolean = when (mode) {
        com.ahu.tlauncher.data.PerfMode.ON -> true
        com.ahu.tlauncher.data.PerfMode.OFF -> false
        com.ahu.tlauncher.data.PerfMode.AUTO -> isLowEnd(spec)
    }

    /** How often engine data reaches the screen in low-performance mode (the fuel maths still gets every sample). */
    const val LOW_PERF_UI_MS = 500L
    /** Map frame rate cap in low-performance mode. */
    const val LOW_PERF_MAP_FPS = 30
}
