package com.ahu.tlauncher.obd

/** Pure display rules, unit tested. */
object ObdDisplay {
    /** OBD readouts, labels and badges only appear with live (or demo) data. */
    fun show(status: ObdStatus): Boolean = status == ObdStatus.CONNECTED || status == ObdStatus.DEMO
}

object GpsSpeed {
    private const val MAX_AGE_MS = 10_000L

    /**
     * Speed from a GPS fix, in km/h. Null when the fix has no speed or is stale.
     * Values under 2 km/h are shown as 0 to hide GPS jitter while parked.
     */
    fun kmh(hasSpeed: Boolean, speedMps: Float, ageMs: Long): Int? {
        if (!hasSpeed || ageMs > MAX_AGE_MS || speedMps.isNaN()) return null
        val k = speedMps * 3.6f
        return if (k < 2f) 0 else Math.round(k)
    }
}
