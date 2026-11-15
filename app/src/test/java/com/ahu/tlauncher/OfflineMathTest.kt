package com.ahu.tlauncher

import com.ahu.tlauncher.map.GeoBox
import com.ahu.tlauncher.map.OfflineMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMathTest {
    // Manila
    private val lat = 14.5995
    private val lon = 120.9842

    @Test fun wholeWorldAtZoomZero() = assertEquals(1L, OfflineMath.tilesAt(GeoBox(-80.0, -170.0, 80.0, 170.0), 0))
    @Test fun wholeWorldAtZoomTwo() = assertEquals(16L, OfflineMath.tilesAt(GeoBox(-80.0, -170.0, 80.0, 170.0), 2))

    @Test fun boxIsAboutTheRadius() {
        val b = OfflineMath.box(lat, lon, 25.0)
        assertEquals(50.0 / 111.32, b.north - b.south, 1e-6)
        assertTrue(b.east - b.west > b.north - b.south) // degrees of longitude are shorter away from the equator
    }

    // ~2.4 km per zoom-14 tile here: a 50 km box is ~21 tiles across, so ~450 at z14 and ~600 in all.
    @Test fun twentyFiveKmCount() {
        val n = OfflineMath.tileCount(OfflineMath.box(lat, lon, 25.0))
        assertTrue("$n", n in 450L..800L)
    }

    @Test fun allRadiiFitNearTheEquator() = assertEquals(OfflineMath.RADII_KM, OfflineMath.radiiFor(lat, lon))
    @Test fun fiftyKmFitsInManila() = assertTrue(OfflineMath.fits(lat, lon, 50))
    // Tiles get smaller in km further north, so the big radii stop fitting.
    @Test fun fiftyKmTooBigInOslo() = assertFalse(OfflineMath.fits(59.91, 10.75, 50))
    @Test fun budgetUnderOpenFreeMapLimit() = assertTrue(OfflineMath.TILE_BUDGET < OfflineMath.TILE_LIMIT)

    @Test fun stale() = assertTrue(OfflineMath.isStale(0, OfflineMath.STALE_MS))
    @Test fun fresh() = assertFalse(OfflineMath.isStale(0, OfflineMath.STALE_MS - 1))
}
