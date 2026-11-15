package com.ahu.tlauncher.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Box in degrees. */
data class GeoBox(val south: Double, val west: Double, val north: Double, val east: Double)

/** Sizing for offline map areas, within OpenFreeMap's rules for offline downloads. Pure, unit tested. */
object OfflineMath {
    /** OpenFreeMap allows at most 3,000 tiles and 50 MB per offline download; keep a margin under both. */
    const val TILE_LIMIT = 3_000
    const val TILE_BUDGET = 2_800
    const val SIZE_LIMIT_BYTES = 50L * 1024 * 1024
    /** OpenMapTiles vector tiles stop at 14; the map overzooms them for street level. */
    const val MAX_ZOOM = 14
    /** The styles' shaded-relief raster layer (256 px tiles, up to zoom 6). */
    private const val RELIEF_MAX_ZOOM = 6
    /** Radius choices offered in Settings, in km. */
    val RADII_KM = listOf(10, 25, 40, 50)
    /** Refresh saved areas after this long: OpenFreeMap publishes new tiles weekly under new addresses. */
    const val STALE_MS = 7L * 24 * 60 * 60_000

    fun box(lat: Double, lon: Double, radiusKm: Double): GeoBox {
        val dLat = radiusKm / 111.32
        val dLon = radiusKm / (111.32 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        return GeoBox((lat - dLat).coerceAtLeast(-85.0), lon - dLon, (lat + dLat).coerceAtMost(85.0), lon + dLon)
    }

    private fun x(lon: Double, z: Int): Int = floor((lon + 180.0) / 360.0 * (1 shl z)).toInt().coerceIn(0, (1 shl z) - 1)
    private fun y(lat: Double, z: Int): Int {
        val r = Math.toRadians(lat)
        return floor((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * (1 shl z)).toInt().coerceIn(0, (1 shl z) - 1)
    }

    /** Tiles covering [b] at zoom [z]. */
    fun tilesAt(b: GeoBox, z: Int): Long =
        (x(b.east, z) - x(b.west, z) + 1).toLong() * (y(b.south, z) - y(b.north, z) + 1)

    /** Tiles one download fetches: vector tiles 0..14 plus the relief raster (256 px tiles, one zoom deeper). */
    fun tileCount(b: GeoBox): Long =
        (0..MAX_ZOOM).sumOf { tilesAt(b, it) } + (0..RELIEF_MAX_ZOOM).sumOf { tilesAt(b, minOf(it + 1, RELIEF_MAX_ZOOM)) }

    fun fits(lat: Double, lon: Double, radiusKm: Int): Boolean = tileCount(box(lat, lon, radiusKm.toDouble())) <= TILE_BUDGET

    /** Radius choices that fit at this spot (smaller areas further from the equator). */
    fun radiiFor(lat: Double, lon: Double): List<Int> = RADII_KM.filter { fits(lat, lon, it) }

    fun isStale(savedAtMs: Long, nowMs: Long): Boolean = nowMs - savedAtMs >= STALE_MS
}
