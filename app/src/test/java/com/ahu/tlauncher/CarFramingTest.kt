package com.ahu.tlauncher

import com.ahu.tlauncher.ui.home.CarFraming
import com.ahu.tlauncher.ui.home.studioRadiance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

class CarFramingTest {
    private val target = CarFraming.target()

    /** Elevation (degrees above the horizon) and azimuth (degrees, 0 = in front of the car, + = its left). */
    private fun angles(e: DoubleArray): Pair<Double, Double> {
        val d = doubleArrayOf(e[0] - target[0], e[1] - target[1], e[2] - target[2])
        val len = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
        return Math.toDegrees(asin(d[1] / len)) to Math.toDegrees(atan2(d[0], d[2]))
    }

    private fun dist(e: DoubleArray) = sqrt((0..2).sumOf { (e[it] - target[it]) * (e[it] - target[it]) })

    @Test fun splitViewLooksDownFromBehind() {
        val e = CarFraming.eye(0f)
        val (el, az) = angles(e)
        assertEquals(74.0, el, 0.01)
        assertEquals(180.0, kotlin.math.abs(az), 0.01)
        assertEquals(12.9, dist(e), 1e-6)
    }

    @Test fun fullScreenIsLowThreeQuarterFrontLeft() {
        val e = CarFraming.eye(1f)
        val (el, az) = angles(e)
        assertEquals(3.0, el, 0.01)
        assertEquals(48.0, az, 0.01) // front-left, like the photo
        assertEquals(4.8, dist(e), 1e-6)
    }

    @Test fun orbitOnlyCountsInFullScreen() {
        val tilt = CarFraming.ORBIT_EL_MAX
        assertTrue(CarFraming.eye(0f, 1.0, tilt).contentEquals(CarFraming.eye(0f)))
        // Tilting up moves the camera back (4.8 m → 8.8 m) as well as up.
        assertEquals(8.8, dist(CarFraming.eye(1f, 0.0, tilt)), 1e-6)
        assertEquals(65.0, angles(CarFraming.eye(1f, 0.0, tilt)).first, 0.01)
    }

    @Test fun carCentredOnVisiblePartAndInsidePanel() {
        val h = 640
        for ((e, visible) in listOf(0f to 384f, 1f to 1280f)) {
            val b = CarFraming.screenBounds(e, 0.0, 0.0, h, visible)
            assertEquals("centre at $e", visible / 2, (b[0] + b[2]) / 2, visible * 0.08f)
            assertTrue("inside left/right at $e", b[0] > 0 && b[2] < visible)
            assertTrue("inside top/bottom at $e", b[1] > 0 && b[3] < h)
        }
    }

    /** The projection matrix and screenBounds agree on where the car's corners land. */
    @Test fun projectionMatchesScreenBounds() {
        val w = 1280; val h = 640; val visible = 900f
        val e = 0.6f
        val m = CarFraming.projection(w, h, visible)
        val eye = CarFraming.eye(e)
        val f = norm(doubleArrayOf(target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]))
        val r = norm(cross(f, doubleArrayOf(0.0, 1.0, 0.0)))
        val u = cross(r, f)
        var x0 = Double.MAX_VALUE; var x1 = -Double.MAX_VALUE; var y0 = Double.MAX_VALUE; var y1 = -Double.MAX_VALUE
        val o = CarFraming.OUTLINE
        for (i in o.indices step 3) {
            val c = doubleArrayOf(o[i], o[i + 1], o[i + 2])
            val d = doubleArrayOf(c[0] - eye[0], c[1] - eye[1], c[2] - eye[2])
            val v = doubleArrayOf(dot(d, r), dot(d, u), -dot(d, f), 1.0)
            val clip = DoubleArray(4) { row -> (0 until 4).sumOf { col -> m[col * 4 + row] * v[col] } }
            val px = (clip[0] / clip[3] + 1) / 2 * w
            val py = h - (clip[1] / clip[3] + 1) / 2 * h
            x0 = minOf(x0, px); x1 = maxOf(x1, px); y0 = minOf(y0, py); y1 = maxOf(y1, py)
        }
        val b = CarFraming.screenBounds(e, 0.0, 0.0, h, visible)
        assertEquals(b[0].toDouble(), x0, 0.5); assertEquals(b[2].toDouble(), x1, 0.5)
        assertEquals(b[1].toDouble(), y0, 0.5); assertEquals(b[3].toDouble(), y1, 0.5)
    }

    /** Orbiting never pushes the car into the readouts or cards; the default views are left exactly as they are. */
    @Test fun orbitKeepsCarInFreeSpace() {
        val h = 643; val w = 1280f
        assertTrue(CarFraming.fit(1f, 0.0, 0.0, h, w).contentEquals(doubleArrayOf(1.0, 0.0, 0.0)))
        val d = CarFraming.screenBounds(1f, 0.0, 0.0, h, w)
        val left = minOf(d[0], 0.1f * w); val right = maxOf(d[2], 0.9f * w)
        val top = minOf(d[1], 0.33f * h); val bottom = maxOf(d[3], 0.765f * h)
        var yaw = -3.2
        while (yaw <= 3.2) {
            var el = CarFraming.ORBIT_EL_MIN
            while (el <= CarFraming.ORBIT_EL_MAX) {
                val b = CarFraming.screenBounds(1f, yaw, el, h, w)
                assertTrue("yaw $yaw el $el: ${b.toList()}", b[0] >= left - 0.5f && b[2] <= right + 0.5f && b[1] >= top - 0.5f && b[3] <= bottom + 0.5f)
                el += 0.1
            }
            yaw += 0.2
        }
    }

    /** In full screen the car's real outline sits between the speed readout and the cards (643 px panel). */
    @Test fun defaultViewClearsReadoutAndCards() {
        val b = CarFraming.screenBounds(1f, 0.0, 0.0, 643, 1280f)
        assertTrue("roof ${b[1]}", b[1] > 205f)  // speed readout's line
        assertTrue("wheels ${b[3]}", b[3] < 497f) // top of the cards
        assertTrue("left ${b[0]}", b[0] > 128f)  // engine readings column
    }

    /** Driving: the camera is behind and above the car, in both panel widths, and the drive blend starts where parked ends. */
    @Test fun chaseViewIsBehindTheCar() {
        for (e in listOf(0f, 1f)) {
            assertTrue(CarFraming.eye(e, drive = 0f).contentEquals(CarFraming.eye(e)))
            val c = CarFraming.eye(e, drive = 1f)
            assertTrue("behind at $e: ${c.toList()}", c[2] < -5)
            assertEquals("centred at $e", 0.0, c[0], 1e-6)
            assertTrue("above at $e", c[1] > 3)
        }
    }

    /** Driving: the car stands low in the panel, below the speed readout and above the cards, with road above it. */
    @Test fun chaseViewFramesCarLow() {
        val h = 643
        for ((e, visible) in listOf(0f to 340f, 1f to 1280f)) {
            val b = CarFraming.driveBounds(e, 1f, h, visible)
            println("chase $e: ${b.toList()}")
            assertTrue("roof $e ${b[1]}", b[1] > 0.4f * h)
            assertTrue("wheels $e ${b[3]}", b[3] < 0.765f * h)
            assertTrue("sides $e", b[0] > 0 && b[2] < visible)
            assertEquals("centre $e", visible / 2, (b[0] + b[2]) / 2, visible * 0.03f)
        }
        // Mid-way through the glide the car stays on screen.
        for (d in listOf(0.25f, 0.5f, 0.75f)) {
            val b = CarFraming.driveBounds(1f, d, h, 1280f)
            assertTrue("on screen at $d: ${b.toList()}", b[1] > 0 && b[3] < h && b[0] > 0 && b[2] < 1280)
        }
    }

    @Test fun studioHasBrightPanelAboveAndDarkFloor() {
        val out = FloatArray(3)
        studioRadiance(0.0, -1.0, 0.0, out)
        assertTrue("floor ${out.toList()}", out.all { it < 0.03f })
        // Straight toward the big ceiling panel above the car's front-right (3, 6, 1).
        val l = sqrt(9.0 + 5.4 * 5.4 + 1.0)
        studioRadiance(3.0 / l, 5.4 / l, 1.0 / l, out)
        assertTrue("panel ${out.toList()}", out[0] > 3f)
    }

    @Test fun srgbToLinear() {
        assertEquals(0f, CarFraming.srgbToLinear(0), 1e-6f)
        assertEquals(1f, CarFraming.srgbToLinear(255), 1e-6f)
        assertEquals(0.2158f, CarFraming.srgbToLinear(128), 1e-3f)
    }

    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    private fun norm(a: DoubleArray) = sqrt(dot(a, a)).let { l -> doubleArrayOf(a[0] / l, a[1] / l, a[2] / l) }

    /** Swiping right must turn the car with the finger: the camera circles to its own left. */
    @Test fun swipeRightTurnsCarWithFinger() {
        val car = com.ahu.tlauncher.ui.home.Car3dState()
        val before = CarFraming.eye(1f)
        car.drag(dx = 100f, dy = 0f, viewW = 1000f, viewH = 500f)
        val after = CarFraming.eye(1f, car.yaw.toDouble(), 0.0)
        val t = CarFraming.target()
        // Camera's right on screen = forward × up (looking from the eye at the target, y up).
        val fx = t[0] - before[0]; val fz = t[2] - before[2]
        val rightX = -fz; val rightZ = fx
        val moved = (after[0] - before[0]) * rightX + (after[2] - before[2]) * rightZ
        assertTrue("camera should move left, moved $moved along its right", moved < 0)
    }

    @Test fun paintBlendsBetweenPaints() {
        val red = com.ahu.tlauncher.ui.home.PaintLook.of(com.ahu.tlauncher.data.CarPaint.RED)
        val white = com.ahu.tlauncher.ui.home.PaintLook.of(com.ahu.tlauncher.data.CarPaint.WHITE)
        val start = red.lerp(white, 0f); val end = red.lerp(white, 1f)
        assertEquals(red.r, start.r, 1e-6f); assertEquals(red.metallic, start.metallic, 1e-6f)
        assertEquals(white.r, end.r, 1e-6f); assertEquals(white.b, end.b, 1e-6f); assertEquals(white.roughness, end.roughness, 1e-6f)
        val half = red.lerp(white, 0.5f)
        assertEquals((red.g + white.g) / 2, half.g, 1e-6f)
        assertEquals((red.roughness + white.roughness) / 2, half.roughness, 1e-6f)
    }

    @Test fun flickSpeed() {
        assertEquals(2 * Math.PI.toFloat(), CarFraming.flingRadPerSec(1000f, 1000f), 1e-4f)
        assertEquals(-CarFraming.FLING_MAX_RAD_S, CarFraming.flingRadPerSec(-50_000f, 1000f), 1e-6f)
        assertEquals(0f, CarFraming.flingRadPerSec(500f, 0f), 0f)
    }
}
