package com.ahu.tlauncher.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * TLauncher's own line icons, drawn in the thin, rounded style of in-car dashboards
 * (24 × 24 grid, 1.8 stroke, round caps). Tinted by the Icon that shows them.
 */
object TIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.line(w: Float = 1.8f, d: PathBuilder.() -> Unit) =
        path(stroke = SolidColor(Color.Black), strokeLineWidth = w, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = d)

    private fun ImageVector.Builder.fill(d: PathBuilder.() -> Unit) = path(fill = SolidColor(Color.Black), pathBuilder = d)

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy); arcTo(r, r, 0f, true, true, cx + r, cy); arcTo(r, r, 0f, true, true, cx - r, cy); close()
    }

    val Music by lazy { icon("Music") {
        line { moveTo(9f, 18f); verticalLineTo(5.5f); lineTo(19f, 3.8f); verticalLineTo(16f) }
        line { circle(6.8f, 18f, 2.3f) }
        line { circle(16.8f, 16f, 2.3f) }
    } }

    /** App drawer: a 3 × 3 grid of dots, the usual sign for "all apps", drawn as plainly as the other icons. */
    val AppGrid by lazy { icon("AppGrid") {
        fill { for (y in listOf(5.5f, 12f, 18.5f)) for (x in listOf(5.5f, 12f, 18.5f)) circle(x, y, 1.9f) }
    } }

    val Speaker by lazy { icon("Speaker") {
        line { moveTo(4f, 9.5f); horizontalLineTo(7.5f); lineTo(12f, 5.5f); verticalLineTo(18.5f); lineTo(7.5f, 14.5f); horizontalLineTo(4f); close() }
        line { moveTo(15.5f, 9f); arcTo(4f, 4f, 0f, false, true, 15.5f, 15f) }
        line { moveTo(18f, 6.5f); arcTo(7.5f, 7.5f, 0f, false, true, 18f, 17.5f) }
    } }

    val SpeakerOff by lazy { icon("SpeakerOff") {
        line { moveTo(4f, 9.5f); horizontalLineTo(7.5f); lineTo(12f, 5.5f); verticalLineTo(18.5f); lineTo(7.5f, 14.5f); horizontalLineTo(4f); close() }
        line { moveTo(15.5f, 9.5f); lineTo(20.5f, 14.5f); moveTo(20.5f, 9.5f); lineTo(15.5f, 14.5f) }
    } }

    val ChevronUp by lazy { icon("ChevronUp") { line(2f) { moveTo(6f, 15f); lineTo(12f, 9f); lineTo(18f, 15f) } } }
    val ChevronDown by lazy { icon("ChevronDown") { line(2f) { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) } } }
    val ChevronRight by lazy { icon("ChevronRight") { line(2f) { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) } } }
    val ChevronLeft by lazy { icon("ChevronLeft") { line(2f) { moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f) } } }

    /** Navigation arrow (paper-plane style). */
    val Navigate by lazy { icon("Navigate") { line { moveTo(20f, 4f); lineTo(3.5f, 11f); lineTo(11f, 13f); lineTo(13f, 20.5f); close() } } }

    val Search by lazy { icon("Search") { line { circle(10.5f, 10.5f, 6f) }; line { moveTo(15f, 15f); lineTo(20f, 20f) } } }

    val Bluetooth by lazy { icon("Bluetooth") { line { moveTo(7f, 7.5f); lineTo(17f, 16f); lineTo(12f, 20.5f); verticalLineTo(3.5f); lineTo(17f, 8f); lineTo(7f, 16.5f) } } }

    val BluetoothOff by lazy { icon("BluetoothOff") {
        line { moveTo(12f, 12f); verticalLineTo(20.5f); lineTo(17f, 16f); lineTo(14.5f, 13.8f); moveTo(12f, 9f); verticalLineTo(3.5f); lineTo(17f, 8f); lineTo(14.8f, 9.9f) }
        line { moveTo(4f, 4f); lineTo(20f, 20f) }
    } }

    /** Wi-Fi with [bars] of 0–3 arcs filled (unfilled arcs are drawn faint by the caller's alpha). */
    fun wifi(bars: Int) = icon("Wifi$bars") {
        fill { circle(12f, 18.5f, 1.5f) }
        if (bars >= 1) line(2f) { moveTo(8.8f, 15f); arcTo(4.6f, 4.6f, 0f, false, true, 15.2f, 15f) }
        if (bars >= 2) line(2f) { moveTo(5.8f, 12f); arcTo(8.8f, 8.8f, 0f, false, true, 18.2f, 12f) }
        if (bars >= 3) line(2f) { moveTo(2.8f, 9f); arcTo(13f, 13f, 0f, false, true, 21.2f, 9f) }
    }

    val WifiOff by lazy { icon("WifiOff") {
        line(2f) { moveTo(5.8f, 12f); arcTo(8.8f, 8.8f, 0f, false, true, 18.2f, 12f) }
        fill { circle(12f, 18.5f, 1.5f) }
        line { moveTo(4f, 4f); lineTo(20f, 20f) }
    } }

    val Sun by lazy { icon("Sun") {
        line { circle(12f, 12f, 4f) }
        line {
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val c = kotlin.math.cos(a).toFloat(); val s = kotlin.math.sin(a).toFloat()
                moveTo(12f + 7f * c, 12f + 7f * s); lineTo(12f + 9.2f * c, 12f + 9.2f * s)
            }
        }
    } }

    val Gear by lazy { icon("Gear") {
        line { circle(12f, 12f, 3f) }
        line {
            // eight rounded teeth around a ring
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val c = kotlin.math.cos(a).toFloat(); val s = kotlin.math.sin(a).toFloat()
                moveTo(12f + 6.6f * c, 12f + 6.6f * s); lineTo(12f + 8.8f * c, 12f + 8.8f * s)
            }
        }
        line { circle(12f, 12f, 6.6f) }
    } }

    val Plus by lazy { icon("Plus") { line(2.2f) { moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f) } } }
    val Minus by lazy { icon("Minus") { line(2.2f) { moveTo(5f, 12f); horizontalLineTo(19f) } } }

    val Locate by lazy { icon("Locate") {
        line { circle(12f, 12f, 6.5f) }
        fill { circle(12f, 12f, 2.4f) }
        line { moveTo(12f, 2.5f); verticalLineTo(5.5f); moveTo(12f, 18.5f); verticalLineTo(21.5f); moveTo(2.5f, 12f); horizontalLineTo(5.5f); moveTo(18.5f, 12f); horizontalLineTo(21.5f) }
    } }

    val Home by lazy { icon("Home") { line { moveTo(4f, 11f); lineTo(12f, 4f); lineTo(20f, 11f); moveTo(6.5f, 9.5f); verticalLineTo(20f); horizontalLineTo(17.5f); verticalLineTo(9.5f) } } }

    val Work by lazy { icon("Work") {
        line { moveTo(4.5f, 8f); horizontalLineTo(19.5f); verticalLineTo(19f); horizontalLineTo(4.5f); close() }
        line { moveTo(9f, 8f); verticalLineTo(5.5f); horizontalLineTo(15f); verticalLineTo(8f); moveTo(4.5f, 12.5f); horizontalLineTo(19.5f) }
    } }

    val Close by lazy { icon("Close") { line(2f) { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) } } }

    // Playback: solid shapes, like the prototype's CSS-drawn controls.
    val Play by lazy { icon("Play") { fill { moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close() } } }
    val Pause by lazy { icon("Pause") { fill { moveTo(7f, 5f); horizontalLineTo(10f); verticalLineTo(19f); horizontalLineTo(7f); close(); moveTo(14f, 5f); horizontalLineTo(17f); verticalLineTo(19f); horizontalLineTo(14f); close() } } }
    val Next by lazy { icon("Next") { fill { moveTo(6f, 6f); lineTo(15f, 12f); lineTo(6f, 18f); close(); moveTo(16f, 6f); horizontalLineTo(18.2f); verticalLineTo(18f); horizontalLineTo(16f); close() } } }
    val Previous by lazy { icon("Previous") { fill { moveTo(18f, 6f); lineTo(9f, 12f); lineTo(18f, 18f); close(); moveTo(5.8f, 6f); horizontalLineTo(8f); verticalLineTo(18f); horizontalLineTo(5.8f); close() } } }

    // Turn arrows for TLauncher's own guidance (free map).
    val Straight by lazy { icon("Straight") { line(2.4f) { moveTo(12f, 21f); verticalLineTo(4f); moveTo(6.5f, 9.5f); lineTo(12f, 4f); lineTo(17.5f, 9.5f) } } }
    val TurnRight by lazy { icon("TurnRight") { line(2.4f) { moveTo(7f, 21f); verticalLineTo(12f); arcTo(4f, 4f, 0f, false, true, 11f, 8f); horizontalLineTo(19f); moveTo(14.5f, 3.5f); lineTo(19f, 8f); lineTo(14.5f, 12.5f) } } }
    val TurnLeft by lazy { icon("TurnLeft") { line(2.4f) { moveTo(17f, 21f); verticalLineTo(12f); arcTo(4f, 4f, 0f, false, false, 13f, 8f); horizontalLineTo(5f); moveTo(9.5f, 3.5f); lineTo(5f, 8f); lineTo(9.5f, 12.5f) } } }
    val SlightRight by lazy { icon("SlightRight") { line(2.4f) { moveTo(8f, 21f); verticalLineTo(14f); lineTo(17f, 5f); moveTo(10.5f, 5f); horizontalLineTo(17f); verticalLineTo(11.5f) } } }
    val SlightLeft by lazy { icon("SlightLeft") { line(2.4f) { moveTo(16f, 21f); verticalLineTo(14f); lineTo(7f, 5f); moveTo(13.5f, 5f); horizontalLineTo(7f); verticalLineTo(11.5f) } } }
    val UTurn by lazy { icon("UTurn") { line(2.4f) { moveTo(16f, 21f); verticalLineTo(9f); arcTo(4.5f, 4.5f, 0f, false, false, 7f, 9f); verticalLineTo(16f); moveTo(3.5f, 12.5f); lineTo(7f, 16f); lineTo(10.5f, 12.5f) } } }
    val Roundabout by lazy { icon("Roundabout") {
        line(2.2f) { circle(12f, 10f, 4f) }
        line(2.4f) { moveTo(12f, 21f); verticalLineTo(14f); moveTo(15f, 7.2f); lineTo(19f, 3.5f); moveTo(14.5f, 3.5f); horizontalLineTo(19f); verticalLineTo(8f) }
    } }
    val Arrive by lazy { icon("Arrive") {
        line(2f) { moveTo(12f, 21f); curveTo(12f, 21f, 5f, 14.5f, 5f, 9.5f); arcTo(7f, 7f, 0f, false, true, 19f, 9.5f); curveTo(19f, 14.5f, 12f, 21f, 12f, 21f); close() }
        fill { circle(12f, 9.5f, 2.6f) }
    } }

    /** Arrow for a route step (OSRM type + modifier). */
    fun turn(type: String, modifier: String?) = when {
        type == "arrive" -> Arrive
        type.startsWith("roundabout") || type == "rotary" -> Roundabout
        modifier == "uturn" -> UTurn
        modifier == "slight right" || (type == "fork" && modifier?.contains("right") == true) -> SlightRight
        modifier == "slight left" || (type == "fork" && modifier?.contains("left") == true) -> SlightLeft
        modifier?.contains("right") == true -> TurnRight
        modifier?.contains("left") == true -> TurnLeft
        else -> Straight
    }

    // ---------------------------------------------------------------- settings sidebar & status (thin line)
    val Display by lazy { icon("Display") {
        line { moveTo(4f, 6f); horizontalLineTo(20f); verticalLineTo(16f); horizontalLineTo(4f); close() }
        line { moveTo(9f, 20f); horizontalLineTo(15f); moveTo(12f, 16f); verticalLineTo(20f) }
    } }
    val DockBar by lazy { icon("DockBar") {
        line { moveTo(3.5f, 15f); horizontalLineTo(20.5f); verticalLineTo(19.5f); horizontalLineTo(3.5f); close() }
        fill { circle(8f, 17.25f, 1f); circle(12f, 17.25f, 1f); circle(16f, 17.25f, 1f) }
        line { moveTo(6f, 4.5f); horizontalLineTo(18f); verticalLineTo(11f); horizontalLineTo(6f); close() }
    } }
    /** OBD plug: connector body with two pins. */
    val Plug by lazy { icon("Plug") {
        line { moveTo(6.5f, 9f); horizontalLineTo(17.5f); verticalLineTo(13f); arcTo(5.5f, 5.5f, 0f, false, true, 6.5f, 13f); close() }
        line { moveTo(9.5f, 9f); verticalLineTo(4f); moveTo(14.5f, 9f); verticalLineTo(4f); moveTo(12f, 18.5f); verticalLineTo(21f) }
    } }
    /** Service wrench (Car settings). */
    val Wrench by lazy { icon("Wrench") {
        line { moveTo(14.5f, 4f); arcTo(4.5f, 4.5f, 0f, false, false, 13.2f, 10.8f); lineTo(4.5f, 19.5f); lineTo(5.5f, 20.5f) ; lineTo(14.2f, 11.8f); arcTo(4.5f, 4.5f, 0f, false, false, 20f, 9.5f); lineTo(17f, 9.8f); lineTo(14.6f, 7.4f); close() }
    } }
    val Shield by lazy { icon("Shield") {
        line { moveTo(12f, 3f); lineTo(19.5f, 6f); verticalLineTo(11.5f); curveTo(19.5f, 16f, 16.5f, 19.5f, 12f, 21f); curveTo(7.5f, 19.5f, 4.5f, 16f, 4.5f, 11.5f); verticalLineTo(6f); close() }
        line { moveTo(9f, 12f); lineTo(11.2f, 14.2f); lineTo(15.2f, 10f) }
    } }
    val Info by lazy { icon("Info") {
        line { circle(12f, 12f, 8.5f) }
        line(2f) { moveTo(12f, 11f); verticalLineTo(16.5f) }
        fill { circle(12f, 7.8f, 1.2f) }
    } }
    val Bell by lazy { icon("Bell") {
        line { moveTo(6f, 16.5f); verticalLineTo(11f); arcTo(6f, 6f, 0f, false, true, 18f, 11f); verticalLineTo(16.5f); lineTo(19.5f, 18f); horizontalLineTo(4.5f); close() }
        line { moveTo(10f, 20.5f); horizontalLineTo(14f) }
    } }
    /** Speaker with three sound waves (volume). */
    val SpeakerWaves by lazy { icon("SpeakerWaves") {
        line { moveTo(3f, 9.5f); horizontalLineTo(6.5f); lineTo(11f, 5.5f); verticalLineTo(18.5f); lineTo(6.5f, 14.5f); horizontalLineTo(3f); close() }
        line { moveTo(14f, 10f); arcTo(3f, 3f, 0f, false, true, 14f, 14f) }
        line { moveTo(16.5f, 7.5f); arcTo(6f, 6f, 0f, false, true, 16.5f, 16.5f) }
        line { moveTo(19f, 5f); arcTo(9.5f, 9.5f, 0f, false, true, 19f, 19f) }
    } }
    /** Mobile signal: four rising bars, [level] 0–4 drawn solid, the rest faint (caller draws two passes). */
    fun signalBars(level: Int, solid: Boolean) = icon("Signal$level$solid") {
        for (i in 0 until 4) {
            val on = i < level
            if (on == solid) fill {
                val x = 4f + i * 4.5f; val top = 18f - (i + 1) * 3.4f
                moveTo(x, top); horizontalLineTo(x + 2.8f); verticalLineTo(19f); horizontalLineTo(x); close()
            }
        }
    }
}
