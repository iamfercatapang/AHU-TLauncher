package com.ahu.tlauncher.ui.home

/** Map camera policy: parked overview vs. driving view. Pure, unit tested. */
object DriveCamera {
    enum class Mode { PARK, DRIVE }

    const val DRIVE_ON_KMH = 8
    const val DRIVE_OFF_KMH = 3
    const val DRIVE_TILT = 50.0
    const val PARK_TILT = 0.0
    const val PARK_ZOOM = 16.0
    const val REFOLLOW_MS = 10_000L

    /** Hysteresis so the view doesn't flip back and forth in slow traffic. Unknown speed keeps the mode. */
    fun nextMode(current: Mode, speedKmh: Int?): Mode = when {
        speedKmh == null -> current
        current == Mode.PARK && speedKmh >= DRIVE_ON_KMH -> Mode.DRIVE
        current == Mode.DRIVE && speedKmh <= DRIVE_OFF_KMH -> Mode.PARK
        else -> current
    }

    /** Closer in town, further out on the highway: ≤30 km/h → 17, 60 → 16, ≥90 → 15. */
    fun zoomFor(speedKmh: Int): Double {
        val s = speedKmh.coerceIn(30, 90)
        return 17.0 - (s - 30) / 30.0
    }
}
