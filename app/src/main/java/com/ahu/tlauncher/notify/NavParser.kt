package com.ahu.tlauncher.notify

/** Turn-by-turn info lifted from a Google Maps / Waze navigation notification. */
data class NavInfo(
    val packageName: String,
    val appLabel: String,
    val distance: String?,
    val instruction: String,
    val detail: String?,
    val eta: String?,
)

/**
 * Heuristic parser: Maps/Waze don't publish a stable API, but their ongoing navigation
 * notifications consistently carry the distance to the next manoeuvre, the instruction /
 * street, and a "12 min · 4.3 km · 10:42 ETA" style summary. Any field we can't recognise is
 * left null and the raw text is shown instead, so a format change degrades gracefully.
 */
object NavParser {
    const val MAPS = "com.google.android.apps.maps"
    const val WAZE = "com.waze"
    val NAV_PACKAGES = setOf(MAPS, WAZE)

    private val distanceOnly = Regex("""^\s*(\d+(?:[.,]\d+)?\s?(?:m|km|ft|mi|yd|yards|feet|miles?|meters?|metres?))\s*$""", RegexOption.IGNORE_CASE)
    private val distanceLead = Regex("""^\s*(?:in\s+)?(\d+(?:[.,]\d+)?\s?(?:m|km|ft|mi|yd))\b[\s,·:-]*""", RegexOption.IGNORE_CASE)
    private val etaHint = Regex("""\b(ETA|arrival|arrive|min|hr|h\b)|\d{1,2}:\d{2}""", RegexOption.IGNORE_CASE)

    fun parse(pkg: String, title: CharSequence?, text: CharSequence?, subText: CharSequence?, bigText: CharSequence? = null): NavInfo? {
        if (pkg !in NAV_PACKAGES) return null
        val t = title?.toString()?.trim().orEmpty()
        val x = (bigText ?: text)?.toString()?.trim().orEmpty()
        val s = subText?.toString()?.trim().orEmpty()
        if (t.isEmpty() && x.isEmpty()) return null

        var distance: String? = null
        var instruction: String
        var detail: String? = null

        when {
            distanceOnly.matches(t) -> { distance = norm(t); instruction = x; }
            distanceOnly.matches(x) -> { distance = norm(x); instruction = t; }
            else -> {
                val lt = distanceLead.find(t)
                val lx = distanceLead.find(x)
                when {
                    lt != null && t.length > lt.value.length -> {
                        distance = norm(lt.groupValues[1]); instruction = t.substring(lt.value.length); detail = x.ifEmpty { null }
                    }
                    lx != null && x.length > lx.value.length -> {
                        distance = norm(lx.groupValues[1]); instruction = t.ifEmpty { x.substring(lx.value.length) }
                        if (t.isNotEmpty()) detail = x.substring(lx.value.length).ifEmpty { null }
                    }
                    else -> { instruction = t.ifEmpty { x }; detail = if (t.isNotEmpty()) x.ifEmpty { null } else null }
                }
            }
        }
        if (instruction.isEmpty()) instruction = t.ifEmpty { x }

        val eta = s.takeIf { it.isNotEmpty() && etaHint.containsMatchIn(it) }
            ?: detail?.takeIf { etaHint.containsMatchIn(it) && distanceLead.find(it) == null }?.also { detail = null }
        return NavInfo(
            packageName = pkg,
            appLabel = if (pkg == WAZE) "Waze" else "Google Maps",
            distance = distance,
            instruction = instruction.trim().trimStart(',', '·', '-').trim(),
            detail = detail?.trim()?.ifEmpty { null },
            eta = eta,
        )
    }

    private fun norm(d: String) = d.trim().replace(Regex("""(\d)([a-zA-Z])"""), "$1 $2")
}
