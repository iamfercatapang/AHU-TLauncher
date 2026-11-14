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

    /** "More" / all apps: rounded square with three dots. */
    val More by lazy { icon("More") {
        line { moveTo(6f, 4f); horizontalLineTo(18f); arcTo(2f, 2f, 0f, false, true, 20f, 6f); verticalLineTo(18f); arcTo(2f, 2f, 0f, false, true, 18f, 20f)
            horizontalLineTo(6f); arcTo(2f, 2f, 0f, false, true, 4f, 18f); verticalLineTo(6f); arcTo(2f, 2f, 0f, false, true, 6f, 4f); close() }
        fill { circle(8f, 12f, 1.2f); circle(12f, 12f, 1.2f); circle(16f, 12f, 1.2f) }
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
}
