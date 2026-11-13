package com.ahu.tlauncher.obd

/**
 * Diagnostic trouble codes: parsing ELM327 replies to mode 03 (stored), 07 (pending) and 04 (clear),
 * and plain-English descriptions for common generic codes. Pure Kotlin, unit tested.
 */
object Dtc {
    private val hexByte = Regex("^[0-9A-F]{2}$")
    private val framePrefix = Regex("^([0-9A-F]):\\s*")

    /** Two raw bytes → "P0171". */
    fun decode(a: Int, b: Int): String {
        val letter = "PCBU"[(a shr 6) and 3]
        return letter.toString() + ((a shr 4) and 3) + "%X".format(a and 0xF) + "%02X".format(b)
    }

    /**
     * Codes from a mode 03 / 07 reply ([mode] = 0x03 or 0x07). Handles CAN (count byte, multi-frame
     * "0: … 1: …" lines) and older K-line replies (three codes per line, zero padded).
     * Returns an empty list for "no codes" and null when the reply isn't a valid answer.
     */
    fun parse(raw: String, mode: Int): List<String>? {
        val resp = "%02X".format(0x40 + mode)
        val text = raw.replace(">", " ").uppercase()
        if ("NO DATA" in text) return emptyList()
        val groups = mutableListOf<MutableList<Int>>()
        val multiFrame = mutableSetOf<Int>() // indexes of groups that came as "0: … 1: …" (CAN, with count byte)
        for (rawLine in text.split('\r', '\n')) {
            var line = rawLine.trim()
            if (line.isEmpty()) continue
            val m = framePrefix.find(line)
            val frame = m?.groupValues?.get(1)?.toInt(16)
            if (m != null) line = line.substring(m.range.last + 1)
            val tokens = (if (' ' in line) line.split(Regex("\\s+")) else line.chunked(2)).filter { it.isNotEmpty() }
            if (tokens.isEmpty() || !tokens.all { hexByte.matches(it) }) continue // "SEARCHING...", "00A" length line
            val bytes = tokens.map { it.toInt(16) }
            if (frame != null && frame > 0) { groups.lastOrNull()?.addAll(bytes); continue }
            if (tokens[0] != resp) continue
            if (frame != null) multiFrame += groups.size
            groups += bytes.drop(1).toMutableList()
        }
        if (groups.isEmpty()) return null
        val codes = LinkedHashSet<String>()
        for ((gi, g) in groups.withIndex()) {
            var data: List<Int> = g
            if (gi in multiFrame || data.size % 2 == 1) { // CAN: first byte is the number of codes
                val n = data[0]
                data = data.drop(1).take(n * 2)
            }
            for (i in 0 until data.size - 1 step 2) {
                if (data[i] == 0 && data[i + 1] == 0) continue
                codes += decode(data[i], data[i + 1])
            }
        }
        return codes.toList()
    }

    /** Mode 04 reply: "44" means the ECU cleared the codes. */
    fun cleared(raw: String): Boolean = raw.uppercase().split(Regex("[\\s>]+")).any { it == "44" } || raw.uppercase().trim().startsWith("44")

    /** PID 01 01: check-engine light on/off. */
    fun milOn(raw: String): Boolean? = ElmParser.dataBytes(raw, "01")?.getOrNull(0)?.let { it and 0x80 != 0 }

    fun describe(code: String): String = KNOWN[code] ?: fallback(code)

    private fun fallback(code: String): String {
        if (code.length != 5) return "Unknown code"
        return when (code[0]) {
            'P' -> when {
                code[1] == '1' || code[1] == '3' && code[2] < '4' -> "Manufacturer-specific powertrain code (see Toyota/Daihatsu service info)"
                else -> when (code[2]) {
                    '0', '1', '2' -> "Fuel and air metering"
                    '3' -> "Ignition system or misfire"
                    '4' -> "Auxiliary emission controls"
                    '5' -> "Vehicle speed, idle control or auxiliary inputs"
                    '6' -> "Engine computer or output circuits"
                    '7', '8', '9' -> "Transmission"
                    else -> "Powertrain"
                }
            }
            'C' -> "Chassis (brakes, ABS, steering or suspension)"
            'B' -> "Body (airbags, lighting, comfort systems)"
            else -> "Network communication between control modules"
        }
    }

    private val KNOWN = mapOf(
        "P0010" to "Intake camshaft actuator circuit (Bank 1)",
        "P0011" to "Intake camshaft timing over-advanced (Bank 1)",
        "P0012" to "Intake camshaft timing over-retarded (Bank 1)",
        "P0016" to "Crankshaft / camshaft position correlation (Bank 1)",
        "P0030" to "Oxygen sensor heater control circuit (Bank 1 Sensor 1)",
        "P0031" to "Oxygen sensor heater circuit low (Bank 1 Sensor 1)",
        "P0032" to "Oxygen sensor heater circuit high (Bank 1 Sensor 1)",
        "P0036" to "Oxygen sensor heater control circuit (Bank 1 Sensor 2)",
        "P0037" to "Oxygen sensor heater circuit low (Bank 1 Sensor 2)",
        "P0038" to "Oxygen sensor heater circuit high (Bank 1 Sensor 2)",
        "P0100" to "Mass air flow sensor circuit",
        "P0101" to "Mass air flow sensor range / performance",
        "P0102" to "Mass air flow sensor circuit low",
        "P0103" to "Mass air flow sensor circuit high",
        "P0105" to "Manifold pressure (MAP) sensor circuit",
        "P0106" to "Manifold pressure (MAP) sensor range / performance",
        "P0107" to "Manifold pressure (MAP) sensor circuit low",
        "P0108" to "Manifold pressure (MAP) sensor circuit high",
        "P0110" to "Intake air temperature sensor circuit",
        "P0112" to "Intake air temperature sensor circuit low",
        "P0113" to "Intake air temperature sensor circuit high",
        "P0115" to "Engine coolant temperature sensor circuit",
        "P0116" to "Engine coolant temperature sensor range / performance",
        "P0117" to "Engine coolant temperature sensor circuit low",
        "P0118" to "Engine coolant temperature sensor circuit high",
        "P0120" to "Throttle position sensor circuit",
        "P0121" to "Throttle position sensor range / performance",
        "P0122" to "Throttle position sensor circuit low",
        "P0123" to "Throttle position sensor circuit high",
        "P0125" to "Coolant temperature too low for closed-loop fuel control",
        "P0128" to "Thermostat: coolant below regulating temperature",
        "P0130" to "Oxygen sensor circuit (Bank 1 Sensor 1)",
        "P0131" to "Oxygen sensor low voltage (Bank 1 Sensor 1)",
        "P0132" to "Oxygen sensor high voltage (Bank 1 Sensor 1)",
        "P0133" to "Oxygen sensor slow response (Bank 1 Sensor 1)",
        "P0134" to "Oxygen sensor no activity (Bank 1 Sensor 1)",
        "P0135" to "Oxygen sensor heater circuit (Bank 1 Sensor 1)",
        "P0136" to "Oxygen sensor circuit (Bank 1 Sensor 2)",
        "P0137" to "Oxygen sensor low voltage (Bank 1 Sensor 2)",
        "P0138" to "Oxygen sensor high voltage (Bank 1 Sensor 2)",
        "P0139" to "Oxygen sensor slow response (Bank 1 Sensor 2)",
        "P0140" to "Oxygen sensor no activity (Bank 1 Sensor 2)",
        "P0141" to "Oxygen sensor heater circuit (Bank 1 Sensor 2)",
        "P0171" to "System too lean (Bank 1)",
        "P0172" to "System too rich (Bank 1)",
        "P0201" to "Injector circuit, cylinder 1",
        "P0202" to "Injector circuit, cylinder 2",
        "P0203" to "Injector circuit, cylinder 3",
        "P0204" to "Injector circuit, cylinder 4",
        "P0300" to "Random / multiple cylinder misfire",
        "P0301" to "Cylinder 1 misfire",
        "P0302" to "Cylinder 2 misfire",
        "P0303" to "Cylinder 3 misfire",
        "P0304" to "Cylinder 4 misfire",
        "P0325" to "Knock sensor circuit (Bank 1)",
        "P0327" to "Knock sensor circuit low (Bank 1)",
        "P0328" to "Knock sensor circuit high (Bank 1)",
        "P0335" to "Crankshaft position sensor circuit",
        "P0336" to "Crankshaft position sensor range / performance",
        "P0340" to "Camshaft position sensor circuit (Bank 1)",
        "P0341" to "Camshaft position sensor range / performance (Bank 1)",
        "P0351" to "Ignition coil A circuit",
        "P0352" to "Ignition coil B circuit",
        "P0353" to "Ignition coil C circuit",
        "P0354" to "Ignition coil D circuit",
        "P0400" to "EGR flow",
        "P0401" to "EGR flow insufficient",
        "P0420" to "Catalytic converter efficiency below threshold (Bank 1)",
        "P0440" to "Evaporative emission (EVAP) system",
        "P0441" to "EVAP system incorrect purge flow",
        "P0442" to "EVAP system small leak detected",
        "P0443" to "EVAP purge control valve circuit",
        "P0446" to "EVAP vent control circuit",
        "P0455" to "EVAP system large leak detected (check the fuel cap)",
        "P0456" to "EVAP system very small leak detected",
        "P0500" to "Vehicle speed sensor",
        "P0502" to "Vehicle speed sensor circuit low",
        "P0503" to "Vehicle speed sensor intermittent / erratic",
        "P0505" to "Idle air control system",
        "P0506" to "Idle speed lower than expected",
        "P0507" to "Idle speed higher than expected",
        "P0560" to "System voltage",
        "P0562" to "System voltage low",
        "P0563" to "System voltage high",
        "P0600" to "Engine computer serial communication link",
        "P0601" to "Engine computer memory checksum error",
        "P0602" to "Engine computer programming error",
        "P0603" to "Engine computer keep-alive memory error",
        "P0604" to "Engine computer RAM error",
        "P0605" to "Engine computer ROM error",
        "P0606" to "Engine computer processor fault",
        "P2102" to "Throttle actuator motor circuit low",
        "P2103" to "Throttle actuator motor circuit high",
        "P2111" to "Throttle actuator stuck open",
        "P2112" to "Throttle actuator stuck closed",
        "P2118" to "Throttle actuator motor current range / performance",
        "P2119" to "Throttle body range / performance",
        "P2135" to "Throttle / pedal position sensor A/B voltage correlation",
        "P2195" to "Oxygen sensor signal stuck lean (Bank 1 Sensor 1)",
        "P2196" to "Oxygen sensor signal stuck rich (Bank 1 Sensor 1)",
        "U0100" to "Lost communication with the engine computer",
        "U0101" to "Lost communication with the transmission computer",
        "U0121" to "Lost communication with the ABS module",
        "U0140" to "Lost communication with the body control module",
        "U0155" to "Lost communication with the instrument cluster",
    )
}
