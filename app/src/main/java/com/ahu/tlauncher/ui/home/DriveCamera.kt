package com.ahu.tlauncher.ui.home

/** Map camera policy: parked overview vs. driving view. Pure, unit tested. */
object DriveCamera {
    enum class Mode { PARK, DRIVE }

    const val DRIVE_ON_KMH = 8
    const val DRIVE_OFF_KMH = 3
    /** Flat while driving and navigating, like Google Maps' 2D heading-up view (also light on head units). */
    const val DRIVE_TILT = 0.0
    const val PARK_TILT = 0.0
    const val PARK_ZOOM = 16.5
    const val REFOLLOW_MS = 10_000L

    /**
     * Zoom levels here are OpenFreeMap's (MapLibre: 512 dp tiles). Google Maps and the osmdroid fallback use
     * 256 dp tiles, so the same view is one level higher there: add this to show the same streets at the same size.
     */
    const val TILE_256_OFFSET = 1.0

    /** Driving tilt: flat (it used to tilt, except in low-performance mode). */
    @Suppress("UNUSED_PARAMETER")
    fun driveTilt(lowPerf: Boolean): Double = DRIVE_TILT

    /** Navigating keeps the drive view even when stopped (lights, traffic), instead of the parked overview. */
    fun driveView(mode: Mode, navigating: Boolean): Boolean = mode == Mode.DRIVE || navigating

    /** How far down the map the car sits while driving: most of the view shows the road ahead. */
    const val CAR_Y = 0.88f
    /** Navigating: the car sits this far across, away from the turn and trip cards (mirrored with the cards on the right). */
    const val CAR_X_NAV = 0.72f

    /** Where the car sits on the map while driving, as fractions (x from the left, y from the top). */
    fun carAnchor(navigating: Boolean, cardsOnRight: Boolean): Pair<Float, Float> = when {
        !navigating -> 0.5f to CAR_Y
        cardsOnRight -> (1f - CAR_X_NAV) to CAR_Y
        else -> CAR_X_NAV to CAR_Y
    }

    /** Map padding (left, top, right, bottom, px) that puts the followed car at [anchor] on a [w] × [h] map. */
    fun padding(w: Int, h: Int, anchor: Pair<Float, Float>): IntArray {
        val (ax, ay) = anchor
        val left = ((2 * ax - 1) * w).coerceAtLeast(0f)
        val right = ((1 - 2 * ax) * w).coerceAtLeast(0f)
        val top = ((2 * ay - 1) * h).coerceAtLeast(0f)
        val bottom = ((1 - 2 * ay) * h).coerceAtLeast(0f)
        return intArrayOf(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom))
    }

    /** Hysteresis so the view doesn't flip back and forth in slow traffic. Unknown speed keeps the mode. */
    fun nextMode(current: Mode, speedKmh: Int?): Mode = when {
        speedKmh == null -> current
        current == Mode.PARK && speedKmh >= DRIVE_ON_KMH -> Mode.DRIVE
        current == Mode.DRIVE && speedKmh <= DRIVE_OFF_KMH -> Mode.PARK
        else -> current
    }

    /** Close in town (Google Maps' navigation zoom), easing out on the highway: ≤40 km/h → 17.5, ≥90 → 16.5. */
    fun zoomFor(speedKmh: Int): Double {
        val s = speedKmh.coerceIn(40, 90)
        return 17.5 - (s - 40) / 50.0
    }
}
