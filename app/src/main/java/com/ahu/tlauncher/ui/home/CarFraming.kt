package com.ahu.tlauncher.ui.home

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Camera for the 3D car, following the Tesla UI prototype's vehicle scene (createVehicleScene.ts):
 * the panel's progress (0 = split view, 1 = full screen) moves the camera from high behind the car
 * (74° up, 12.9 m away) round to a low 3/4 front view (3° up, 4.8 m), and in full screen the car can be
 * orbited. Plain math (no Filament), shared by the renderer, the touch area and unit tests.
 *
 * The model (assets/models/avanza.glb) is 4.7 m long like the prototype's car: wheels on the floor
 * (y = 0), centred on x / z, front facing +Z. The prototype's car faces −Z, so its x and z are mirrored here.
 */
/** A paint as the renderer applies it: linear colour, metallic, roughness. Blends smoothly between two paints. */
internal data class PaintLook(val r: Float, val g: Float, val b: Float, val metallic: Float, val roughness: Float) {
    fun lerp(to: PaintLook, t: Float): PaintLook {
        val k = t.coerceIn(0f, 1f)
        fun mix(a: Float, b: Float) = a + (b - a) * k
        return PaintLook(mix(r, to.r), mix(g, to.g), mix(b, to.b), mix(metallic, to.metallic), mix(roughness, to.roughness))
    }

    companion object {
        fun of(p: com.ahu.tlauncher.data.CarPaint): PaintLook {
            val c = p.rgb.toInt()
            return PaintLook(
                CarFraming.srgbToLinear((c shr 16) and 0xFF), CarFraming.srgbToLinear((c shr 8) and 0xFF), CarFraming.srgbToLinear(c and 0xFF),
                p.metallic, p.roughness,
            )
        }
    }
}

internal object CarFraming {
    /**
     * The car's outline in metres (x, y, z triples): its outermost vertices in 160 directions, taken from the
     * prepared glb (without floor and reflection). Projected, they give the car's real silhouette on screen,
     * much tighter than its bounding box, whose corners stick out far beyond the roof and bonnet.
     */
    val OUTLINE = doubleArrayOf(
        0.575, 1.849, -0.999, -0.601, 1.821, 0.008, 0.0, 1.811, -2.056, 0.628, 1.756, 0.337,
        -0.568, 1.837, -1.489, 0.553, 1.816, -1.824, -0.645, 1.138, 2.018, -0.456, 1.803, -1.961,
        0.622, 1.792, 0.173, -0.629, 1.774, 0.255, 0.456, 1.803, -1.961, 0.608, 1.12, 2.062,
        -0.544, 1.813, -1.856, 0.56, 1.82, -1.792, -0.682, 1.135, 2.003, -0.137, 1.809, -2.045,
        0.753, 1.142, 1.927, -0.609, 1.829, -0.172, 0.511, 1.806, -1.916, 0.001, 1.0, 2.309,
        -0.488, 1.804, -1.94, 1.024, 1.378, 0.839, -0.775, 1.113, 1.951, 0.232, 1.805, -2.032,
        0.736, 1.054, 2.078, -0.61, 1.761, -1.837, 0.618, 1.744, -1.857, -0.57, 1.067, 2.139,
        -0.363, 1.792, -2.01, 0.834, 1.055, 1.952, -1.024, 1.376, 0.85, 0.483, 1.779, -1.97,
        0.263, 0.995, 2.28, -0.76, 1.413, -2.117, 0.642, 1.764, -1.582, -0.736, 1.054, 2.078,
        0.0, 1.798, -2.07, -0.649, 1.748, -1.624, 0.76, 1.413, -2.117, -0.261, 0.983, 2.288,
        -0.772, 1.3, -2.199, 1.028, 1.368, 0.852, -0.845, 1.025, 1.972, 0.708, 1.355, -2.195,
        0.644, 1.0, 2.156, -0.762, 1.413, -2.113, 0.762, 1.413, -2.113, -0.743, 1.012, 2.105,
        -0.663, 1.25, -2.251, 0.891, 0.834, 2.061, -1.029, 1.369, 0.845, 0.792, 1.251, -2.21,
        0.062, 0.975, 2.322, -0.792, 1.251, -2.21, 1.033, 1.36, 0.832, -0.892, 0.822, 2.067,
        0.664, 1.219, -2.263, 0.833, 0.82, 2.117, -0.824, 1.219, -2.151, 0.817, 1.189, -2.199,
        -0.57, 0.695, 2.28, -0.76, 1.25, -2.228, 0.908, 0.813, 2.042, -0.908, 0.813, 2.042,
        0.782, 1.189, -2.233, 0.483, 0.68, 2.31, -0.817, 1.189, -2.199, 0.828, 1.201, -2.142,
        -0.864, 0.799, 2.098, -0.337, 1.04, -2.338, 0.893, 0.72, 2.086, -1.039, 1.323, 0.82,
        0.803, 1.189, -2.216, -0.381, 0.66, 2.33, -0.796, 0.721, -2.277, 0.918, 0.763, 2.035,
        -0.902, 0.698, 2.076, 0.788, 0.71, -2.283, 0.555, 0.393, 2.308, -0.854, 0.717, -2.188,
        0.854, 0.717, -2.188, -0.555, 0.393, 2.308, -0.717, 0.572, -2.3, 0.906, 0.546, 2.065,
        -0.906, 0.546, 2.065, 0.786, 0.679, -2.284, 0.542, 0.386, 2.314, -0.794, 0.432, -2.239,
        0.919, 0.074, -1.512, -0.87, 0.521, 2.108, 0.356, 0.552, -2.343, 0.818, 0.48, 2.147,
        -0.915, 0.291, -1.86, 0.821, 0.43, -2.203, -0.542, 0.386, 2.314, -0.762, 0.432, -2.265,
        0.909, 0.189, 1.864, -0.909, 0.189, 1.864, 0.728, 0.431, -2.281, -0.821, 0.43, -2.203,
        0.915, 0.291, -1.86, -0.663, 0.378, 2.236, -0.656, 0.429, -2.3, 0.864, 0.49, 2.105,
        -0.909, 0.046, 1.653, 0.794, 0.432, -2.239, -0.07, 0.402, 2.343, 0.909, 0.046, 1.653,
        0.656, 0.429, -2.3, 0.614, 0.37, 2.262, -0.909, 0.052, -1.56, -0.727, 0.426, -2.279,
        0.909, 0.107, 1.788, -0.909, 0.086, 1.758, 0.761, 0.427, -2.263, -0.812, 0.413, -2.198,
        0.909, 0.018, -1.454, -0.899, 0.153, 1.85, -0.127, 0.423, -2.325, 0.899, 0.125, 1.824,
        -0.909, 0.015, -1.417, 0.779, 0.4, -2.227, -0.543, 0.381, 2.311, -0.75, 0.402, -2.249,
        0.909, 0.055, 1.689, -0.899, 0.078, 1.763, 0.718, 0.403, -2.265, 0.59, 0.347, 2.262,
        -0.899, 0.028, -1.53, 0.899, 0.028, -1.53, -0.59, 0.347, 2.262, -0.68, 0.389, -2.262,
        0.899, 0.06, 1.728, -0.899, 0.03, 1.617, 0.758, 0.372, -2.21, 0.551, 0.373, 2.302,
        -0.899, 0.044, -1.565, 0.899, 0.029, 1.579, -0.899, 0.06, 1.728, 0.414, 0.343, -2.251,
        -0.899, 0.008, -1.456, 0.899, 0.016, -1.493, -0.876, 0.055, 1.73, -0.876, 0.023, -1.532,
        0.899, 0.036, 1.655, -0.899, 0.036, 1.655, 0.876, 0.023, -1.532, 0.876, 0.041, 1.694,
        0.899, 0.005, -1.417, -0.876, 0.031, 1.656, -0.876, 0.003, -1.456,
    )

    private val TARGET = doubleArrayOf(0.0, 0.65, 0.0)
    private val YAW_SPLIT = rad(180.0)
    private val YAW_FULL = rad(-48.0) + 2 * PI
    private val EL_SPLIT = rad(74.0)
    private val EL_FULL = rad(3.0)

    /** How far the orbit may tilt the camera, relative to the 3° full-screen view: down to 2°, up to 65°. */
    val ORBIT_EL_MIN = rad(2.0) - EL_FULL
    val ORBIT_EL_MAX = rad(65.0) - EL_FULL
    /** After the last touch, the car turns back to its view after this long, over [RETURN_MS]. */
    const val RETURN_DELAY_MS = 5_000L
    const val RETURN_MS = 700

    /** Lamps fade on and off over this long; a new paint blends in over [PAINT_FADE_MS]. */
    const val LAMP_FADE_MS = 450
    const val PAINT_FADE_MS = 600

    /** After a flick the car keeps turning and slows down; higher friction stops it sooner. */
    const val FLING_FRICTION = 2f
    /** Slowest flick that still spins on, and the fastest spin (rad/s). */
    const val FLING_MIN_RAD_S = 0.3f
    const val FLING_MAX_RAD_S = 10f

    /** Horizontal finger speed (px/s) as a turn speed (rad/s): a drag across the whole view is a full turn. */
    fun flingRadPerSec(vxPxPerS: Float, viewW: Float): Float =
        if (viewW <= 0f) 0f else (vxPxPerS / viewW * 2 * PI.toFloat()).coerceIn(-FLING_MAX_RAD_S, FLING_MAX_RAD_S)

    const val NEAR = 0.1
    const val FAR = 100.0

    /**
     * The prototype's view frame is 35° tall over 630 of 1080 px of the panel, its middle 550 px down.
     * The Avanza is taller than the prototype's car, so it is drawn [SIZE] times as large and a little lower
     * ([FRAME_MID_Y]) to keep its roof clear of the speed readout.
     */
    private const val SIZE = 0.92
    private const val FRAME_H = 630.0 / 1080 * SIZE
    private const val FRAME_MID_Y = 0.56
    private val TAN_HALF_FOV = tan(rad(35.0) / 2)

    /**
     * Driving (Tesla's chase view): the camera sits high behind the car looking down the road ahead, so the
     * car stands low in the panel with the road (and the route's lane lines) running away above it.
     */
    private val CHASE_TARGET = doubleArrayOf(0.0, 0.6, 2.0)
    private val CHASE_EL = rad(21.0)
    private const val CHASE_DIST = 13.0

    /** Tyre radius (m), for turning the wheels at the car's speed. */
    const val WHEEL_RADIUS = 0.33
    /** Fastest the wheels are drawn turning (rad/s): faster would strobe at the screen's frame rate. */
    const val WHEEL_MAX_RAD_S = 2 * PI * 2.5

    /**
     * Camera position for panel progress [e] and orbit offsets (radians; they count only in full screen), blended
     * toward the chase view by [drive] (0 = parked views, 1 = driving).
     */
    fun eye(e: Float, yaw: Double = 0.0, elevation: Double = 0.0, drive: Float = 0f): DoubleArray {
        val t = e.coerceIn(0f, 1f).toDouble()
        val p = lerp(YAW_SPLIT, YAW_FULL, t) + yaw * t
        val m = lerp(EL_SPLIT, EL_FULL, t) + elevation * t
        val h = lerp(12.9, lerp(4.8, 8.8, max(0.0, elevation / ORBIT_EL_MAX)), t)
        val parked = doubleArrayOf(-sin(p) * cos(m) * h, TARGET[1] + sin(m) * h, cos(p) * cos(m) * h)
        val d = drive.coerceIn(0f, 1f).toDouble()
        if (d == 0.0) return parked
        // Round the side (not through the car): blend the angles, then place the camera.
        val chaseYaw = if (kotlin.math.abs(p - 3 * PI) < kotlin.math.abs(p - PI)) 3 * PI else PI // the nearer "behind"
        val bp = lerp(p, chaseYaw, d); val bm = lerp(m, CHASE_EL, d); val bh = lerp(h, CHASE_DIST, d)
        val tg = target(drive)
        return doubleArrayOf(tg[0] - sin(bp) * cos(bm) * bh, tg[1] + sin(bm) * bh, tg[2] + cos(bp) * cos(bm) * bh)
    }

    /** What the camera looks at: the car's middle, moving ahead of the car while driving. */
    fun target(drive: Float = 0f): DoubleArray {
        val d = drive.coerceIn(0f, 1f).toDouble()
        return doubleArrayOf(lerp(TARGET[0], CHASE_TARGET[0], d), lerp(TARGET[1], CHASE_TARGET[1], d), lerp(TARGET[2], CHASE_TARGET[2], d))
    }

    /** Focal length in pixels for a panel [viewH] px tall. */
    fun focalPx(viewH: Int): Double = FRAME_H * viewH / 2 / TAN_HALF_FOV

    /**
     * Free space for the car while it is orbited, as fractions of the visible panel: below the speed readout,
     * above the cards, clear of the engine readings on the left (and as much on the right, to stay centred).
     */
    private const val FREE_LEFT = 0.1
    private const val FREE_RIGHT = 0.9
    private const val FREE_TOP = 0.33
    private const val FREE_BOTTOM = 0.765

    /**
     * How an orbited view is fitted into the free space: scale (≤ 1, the camera backing off) and a shift of the
     * picture in px. The car's default views are never changed (the free space always includes them).
     */
    fun fit(e: Float, yaw: Double, elevation: Double, viewH: Int, visibleW: Float): DoubleArray {
        if (yaw == 0.0 && elevation == 0.0) return doubleArrayOf(1.0, 0.0, 0.0)
        val d = rawBounds(e, 0.0, 0.0, viewH, visibleW)
        val b = rawBounds(e, yaw, elevation, viewH, visibleW)
        val l = min(d[0], FREE_LEFT * visibleW); val r = max(d[2], FREE_RIGHT * visibleW)
        val t = min(d[1], FREE_TOP * viewH); val btm = max(d[3], FREE_BOTTOM * viewH)
        val k = minOf(1.0, (r - l) / (b[2] - b[0]), (btm - t) / (b[3] - b[1]))
        // Scaled around the picture's centre point, then moved back inside the free space if needed.
        val cx = visibleW / 2.0; val cy = viewH * FRAME_MID_Y
        val x0 = cx + k * (b[0] - cx); val x1 = cx + k * (b[2] - cx)
        val y0 = cy + k * (b[1] - cy); val y1 = cy + k * (b[3] - cy)
        val dx = when { x0 < l -> l - x0; x1 > r -> r - x1; else -> 0.0 }
        val dy = when { y0 < t -> t - y0; y1 > btm -> btm - y1; else -> 0.0 }
        return doubleArrayOf(k, dx, dy)
    }

    /**
     * Column-major projection for a [viewW] × [viewH] px view of which the left [visibleW] px show:
     * the car is centred on the visible part, at the prototype's height and scale, adjusted by [fit].
     */
    fun projection(viewW: Int, viewH: Int, visibleW: Float, fit: DoubleArray = doubleArrayOf(1.0, 0.0, 0.0)): DoubleArray {
        val f = focalPx(viewH) * fit[0]
        val cx = visibleW / 2.0 + fit[1]
        val cyUp = viewH * (1 - FRAME_MID_Y) - fit[2]
        val sx = 2 * f / viewW; val tx = 2 * cx / viewW - 1
        val sy = 2 * f / viewH; val ty = 2 * cyUp / viewH - 1
        val a = -(FAR + NEAR) / (FAR - NEAR)
        val b = -2 * FAR * NEAR / (FAR - NEAR)
        return doubleArrayOf(
            sx, 0.0, 0.0, 0.0,
            0.0, sy, 0.0, 0.0,
            -tx, -ty, a, -1.0,
            0.0, 0.0, b, 0.0,
        )
    }

    /** Where the car's box lands on screen (fitted): left, top, right, bottom in px (y down) of the view. */
    fun screenBounds(e: Float, yaw: Double, elevation: Double, viewH: Int, visibleW: Float): FloatArray {
        val b = rawBounds(e, yaw, elevation, viewH, visibleW)
        val ft = fit(e, yaw, elevation, viewH, visibleW)
        val cx = visibleW / 2.0; val cy = viewH * FRAME_MID_Y
        return floatArrayOf(
            (cx + ft[0] * (b[0] - cx) + ft[1]).toFloat(), (cy + ft[0] * (b[1] - cy) + ft[2]).toFloat(),
            (cx + ft[0] * (b[2] - cx) + ft[1]).toFloat(), (cy + ft[0] * (b[3] - cy) + ft[2]).toFloat(),
        )
    }

    /** The car's box on screen in the (unfitted) view blended toward the chase view by [drive]. */
    fun driveBounds(e: Float, drive: Float, viewH: Int, visibleW: Float): FloatArray =
        rawBounds(e, 0.0, 0.0, viewH, visibleW, drive).let { b -> FloatArray(4) { b[it].toFloat() } }

    /** The car's box on screen before fitting: left, top, right, bottom in px (y down). */
    private fun rawBounds(e: Float, yaw: Double, elevation: Double, viewH: Int, visibleW: Float, drive: Float = 0f): DoubleArray {
        val eye = eye(e, yaw, elevation, drive)
        val fwd = normalize(sub(target(drive), eye))
        val right = normalize(cross(fwd, doubleArrayOf(0.0, 1.0, 0.0)))
        val up = cross(right, fwd)
        val f = focalPx(viewH)
        val cx = visibleW / 2.0
        val cy = viewH * FRAME_MID_Y
        var x0 = Double.MAX_VALUE; var x1 = -Double.MAX_VALUE; var y0 = Double.MAX_VALUE; var y1 = -Double.MAX_VALUE
        for (i in OUTLINE.indices step 3) {
            val d = doubleArrayOf(OUTLINE[i] - eye[0], OUTLINE[i + 1] - eye[1], OUTLINE[i + 2] - eye[2])
            val depth = dot(d, fwd)
            val x = cx + f * dot(d, right) / depth
            val y = cy - f * dot(d, up) / depth
            x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
        }
        return doubleArrayOf(x0, y0, x1, y1)
    }

    /** sRGB 0..255 channel to linear light, for Filament's colour parameters. */
    fun srgbToLinear(c: Int): Float {
        val v = c / 255.0
        return (if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)).toFloat()
    }

    fun rad(deg: Double) = deg * PI / 180
    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
    private fun sub(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    private fun normalize(a: DoubleArray): DoubleArray = Math.sqrt(dot(a, a)).let { l -> doubleArrayOf(a[0] / l, a[1] / l, a[2] / l) }
}
