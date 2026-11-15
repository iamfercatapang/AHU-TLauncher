package com.ahu.tlauncher.obd

/** Pure display rules, unit tested. */
object ObdDisplay {
    /** OBD readouts, labels and badges only appear with live (or demo) data. */
    fun show(status: ObdStatus): Boolean = status == ObdStatus.CONNECTED || status == ObdStatus.DEMO

    /**
     * Whether a new OBD reading changes anything you can see on screen. Tiny changes (RPM within 50,
     * voltage within 0.1 V, the sample time) don't, so the screen isn't redrawn for them.
     */
    fun visiblyChanged(old: ObdState, new: ObdState): Boolean =
        old.status != new.status || old.message != new.message || shown(old.data) != shown(new.data)

    private fun shown(d: ObdData) = d.copy(
        rpm = d.rpm?.let { (it + 25) / 50 * 50 },
        batteryV = d.batteryV?.let { Math.round(it * 10) / 10.0 },
        mafGs = d.mafGs?.let { Math.round(it * 10) / 10.0 },
        timingDeg = d.timingDeg?.let { Math.round(it * 10) / 10.0 },
        stftPct = d.stftPct?.let { Math.round(it * 10) / 10.0 },
        ltftPct = d.ltftPct?.let { Math.round(it * 10) / 10.0 },
        sampleAtMs = 0,
    )
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
