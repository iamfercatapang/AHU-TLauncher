package com.ahu.tlauncher.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ahu.tlauncher.R

/** Inter stands in for the proprietary Universal Sans. Only weights 400 and 500 are used. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
)

/** Tabular figures only on live numeric readouts so values don't jitter. */
private const val TNUM = "tnum"

/**
 * Spec hierarchy (hero 40/500, product name 17/500, nav/button 14/500, body 14/400, promo 22/400),
 * nudged up slightly for arm's-length reading on a 7–10" head unit. Letter-spacing is always normal.
 */
object TType {
    /** In-car exception: glanceable speed readout. */
    val speed = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 72.sp, lineHeight = 78.sp, fontFeatureSettings = TNUM)
    val hero = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 40.sp, lineHeight = 48.sp, fontFeatureSettings = TNUM)
    val display = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp, fontFeatureSettings = TNUM)
    val promo = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 26.sp, fontFeatureSettings = TNUM)
    val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp)
    val button = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 18.sp)
    val label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM)
    val caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = TNUM)
}
