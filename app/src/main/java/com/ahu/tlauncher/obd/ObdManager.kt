package com.ahu.tlauncher.obd

import com.ahu.tlauncher.data.ObdSourceType
import com.ahu.tlauncher.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ObdManager(scope: CoroutineScope, settings: Flow<Settings>) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ObdState> = settings
        .map { it.obdSource to it.obdDeviceAddress }
        .distinctUntilChanged()
        .flatMapLatest { (type, addr) ->
            when {
                type == ObdSourceType.DEMO -> SimulatedObdSource().stream()
                addr.isBlank() -> flowOf(ObdState(ObdStatus.DISCONNECTED, message = "No adapter selected"))
                else -> ElmBluetoothSource(addr).stream()
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ObdState())
}
