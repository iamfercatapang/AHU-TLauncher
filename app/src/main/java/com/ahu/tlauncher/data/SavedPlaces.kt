package com.ahu.tlauncher.data

import com.ahu.tlauncher.route.Place
import com.ahu.tlauncher.route.RouteMath

/**
 * Home / Work as a destination: the spot pinned on the map when there is one, otherwise the typed address.
 * Null when not set. The address is kept as typed, since the Home / Work drive times are keyed by it.
 */
fun Settings.savedPlace(spot: SavedSpot): Place? {
    val (addr, pos) = when (spot) { SavedSpot.HOME -> homeAddress to homePos; SavedSpot.WORK -> workAddress to workPos }
    if (addr.isBlank() && pos == null) return null
    return Place(spot.label, addr.ifBlank { RouteMath.coordText(pos!!.lat, pos.lon) }, pos?.lat, pos?.lon)
}
