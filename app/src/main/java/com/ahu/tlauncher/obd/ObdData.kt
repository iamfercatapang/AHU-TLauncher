package com.ahu.tlauncher.obd

/** Engine data available on a typical manual-gearbox car via standard OBD-II PIDs. */
data class ObdData(
    val speedKmh: Int? = null,
    val rpm: Int? = null,
    val coolantC: Int? = null,
    val batteryV: Double? = null,
)

enum class ObdStatus { DISCONNECTED, CONNECTING, CONNECTED, DEMO, ERROR }

data class ObdState(
    val status: ObdStatus = ObdStatus.DISCONNECTED,
    val data: ObdData = ObdData(),
    val message: String? = null,
)
