package com.ahu.tlauncher.obd

import com.ahu.tlauncher.data.ObdSourceType
import com.ahu.tlauncher.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ObdManager(private val scope: CoroutineScope, settings: Flow<Settings>) {
    private val link = ObdLink()
    private val _diag = MutableStateFlow(DiagState())
    val diag: StateFlow<DiagState> = _diag.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ObdState> = settings
        .map { it.obdSource to it.obdDeviceAddress }
        .distinctUntilChanged()
        .flatMapLatest { (type, addr) ->
            _diag.value = DiagState() // results belong to the previous adapter
            when {
                type == ObdSourceType.DEMO -> SimulatedObdSource().stream(link)
                addr.isBlank() -> flowOf(ObdState(ObdStatus.DISCONNECTED, message = "No adapter selected"))
                else -> ElmBluetoothSource(addr).stream(link)
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ObdState())

    /** Poll the extra live-data PIDs (only while the Diagnostics page is visible). */
    fun setExtended(on: Boolean) { link.extended.value = on }

    fun readCodes() = run("Reading codes…") {
        val stored = link.exec("03")?.let { Dtc.parse(it, 0x03) } ?: return@run fail("The car didn't answer. Turn the ignition on and try again.")
        val pending = link.exec("07")?.let { Dtc.parse(it, 0x07) }
        val mil = link.exec("0101")?.let { Dtc.milOn(it) }
        DiagState(milOn = mil, stored = stored, pending = pending, message = if (stored.isEmpty() && pending.isNullOrEmpty()) "No trouble codes" else null)
    }

    fun clearCodes() = run("Clearing codes…") {
        val ok = link.exec("04", 8_000)?.let { Dtc.cleared(it) } == true
        if (!ok) return@run fail("Codes were not cleared. Some cars only allow this with the engine off and ignition on.")
        val stored = link.exec("03")?.let { Dtc.parse(it, 0x03) }
        val pending = link.exec("07")?.let { Dtc.parse(it, 0x07) }
        val mil = link.exec("0101")?.let { Dtc.milOn(it) }
        DiagState(milOn = mil, stored = stored, pending = pending, message = "Codes cleared")
    }

    private fun fail(msg: String) = _diag.value.copy(busy = false, message = msg, error = true)

    private fun run(label: String, block: suspend () -> DiagState) {
        if (_diag.value.busy) return
        if (!ObdDisplay.show(state.value.status)) {
            _diag.value = DiagState(message = "Connect an OBD adapter first", error = true); return
        }
        _diag.value = _diag.value.copy(busy = true, message = label, error = false)
        scope.launch { _diag.value = runCatching { block() }.getOrElse { fail(it.message ?: "Failed") } }
    }
}
