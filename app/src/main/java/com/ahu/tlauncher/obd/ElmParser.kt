package com.ahu.tlauncher.obd

/**
 * Parses raw ELM327 responses. Pure Kotlin, unit tested.
 *
 * A response to "010D" (with echo/headers off) looks like "41 0D 3C" possibly followed
 * by ">" prompt, "SEARCHING..." lines, or several ECU replies. We look for the first
 * "41 <pid>" sequence and decode the following bytes.
 */
object ElmParser {
    private val hexByte = Regex("^[0-9A-Fa-f]{2}$")

    fun dataBytes(raw: String, pid: String): IntArray? {
        val pidByte = pid.takeLast(2).uppercase()
        // Normalise: remove prompt, split on whitespace / line breaks; tolerate responses with no spaces.
        val cleaned = raw.replace(">", " ").uppercase()
        val lines = cleaned.split('\r', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            if (line.contains("NO DATA") || line.contains("ERROR") || line.contains("UNABLE")) continue
            val tokens = if (line.contains(' ')) line.split(Regex("\\s+")) else line.chunked(2)
            if (!tokens.all { hexByte.matches(it) }) continue
            for (i in 0 until tokens.size - 1) {
                if (tokens[i] == "41" && tokens[i + 1] == pidByte) {
                    return tokens.drop(i + 2).map { it.toInt(16) }.toIntArray()
                }
            }
        }
        return null
    }

    /** PID 0D: vehicle speed, km/h = A. */
    fun speedKmh(raw: String): Int? = dataBytes(raw, "0D")?.getOrNull(0)

    /** PID 0C: engine RPM = (256A + B) / 4. */
    fun rpm(raw: String): Int? = dataBytes(raw, "0C")?.let { b ->
        if (b.size < 2) null else (256 * b[0] + b[1]) / 4
    }

    /** PID 05: coolant temperature °C = A − 40. */
    fun coolantC(raw: String): Int? = dataBytes(raw, "05")?.getOrNull(0)?.minus(40)

    /** PID 42: control-module voltage = (256A + B) / 1000. */
    fun moduleVoltage(raw: String): Double? = dataBytes(raw, "42")?.let { b ->
        if (b.size < 2) null else (256 * b[0] + b[1]) / 1000.0
    }

    /** PID 04: calculated engine load % = A × 100 / 255. */
    fun loadPct(raw: String): Int? = dataBytes(raw, "04")?.getOrNull(0)?.let { Math.round(it * 100 / 255f) }

    /** PID 11: throttle position % = A × 100 / 255. */
    fun throttlePct(raw: String): Int? = dataBytes(raw, "11")?.getOrNull(0)?.let { Math.round(it * 100 / 255f) }

    /** PID 0F: intake air temperature °C = A − 40. */
    fun intakeC(raw: String): Int? = dataBytes(raw, "0F")?.getOrNull(0)?.minus(40)

    /** PID 10: mass air flow g/s = (256A + B) / 100. */
    fun mafGs(raw: String): Double? = dataBytes(raw, "10")?.let { b -> if (b.size < 2) null else (256 * b[0] + b[1]) / 100.0 }

    /** PID 0E: timing advance ° before TDC = A / 2 − 64. */
    fun timingDeg(raw: String): Double? = dataBytes(raw, "0E")?.getOrNull(0)?.let { it / 2.0 - 64 }

    /** PID 06 / 07: short / long term fuel trim bank 1, % = A × 100 / 128 − 100. */
    fun fuelTrimPct(raw: String, pid: String): Double? = dataBytes(raw, pid)?.getOrNull(0)?.let { Math.round((it * 100 / 128.0 - 100) * 10) / 10.0 }

    /** PID 2F: fuel tank level % = A × 100 / 255. */
    fun fuelLevelPct(raw: String): Int? = dataBytes(raw, "2F")?.getOrNull(0)?.let { Math.round(it * 100 / 255f) }

    /** PID 0B: intake manifold absolute pressure, kPa = A. */
    fun mapKpa(raw: String): Int? = dataBytes(raw, "0B")?.getOrNull(0)

    /** True when the ECU says it doesn't support the request. */
    fun noData(raw: String): Boolean = raw.uppercase().let { "NO DATA" in it || "UNABLE" in it || "?" in it }

    /** "ATRV" reply such as "12.6V" or "14.1 V". */
    fun atrv(raw: String): Double? =
        Regex("(\\d{1,2}(?:\\.\\d)?)\\s*V", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toDoubleOrNull()
}
