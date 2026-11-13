package com.ahu.tlauncher.obd

/** Engine data available on a typical manual-gearbox car via standard OBD-II PIDs. */
data class ObdData(
    val speedKmh: Int? = null,
    val rpm: Int? = null,
    val coolantC: Int? = null,
    val batteryV: Double? = null,
    // Extra live data, only polled while the Diagnostics page is open.
    val loadPct: Int? = null,
    val throttlePct: Int? = null,
    val intakeC: Int? = null,
    val mafGs: Double? = null,
    val timingDeg: Double? = null,
    val stftPct: Double? = null,
    val ltftPct: Double? = null,
    /** Extra PIDs the ECU answered with NO DATA (shown as "Not supported"). */
    val unsupported: Set<String> = emptySet(),
)

enum class ObdStatus { DISCONNECTED, CONNECTING, CONNECTED, DEMO, ERROR }

data class ObdState(
    val status: ObdStatus = ObdStatus.DISCONNECTED,
    val data: ObdData = ObdData(),
    val message: String? = null,
)

/** Result of reading / clearing trouble codes. */
data class DiagState(
    val busy: Boolean = false,
    /** Check-engine light state from PID 01 01, null until read. */
    val milOn: Boolean? = null,
    val stored: List<String>? = null,
    val pending: List<String>? = null,
    val message: String? = null,
    val error: Boolean = false,
)
