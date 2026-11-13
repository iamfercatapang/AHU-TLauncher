package com.ahu.tlauncher.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.coroutines.coroutineContext

/**
 * Classic-Bluetooth (SPP) ELM327 adapter. Pair the adapter in Android Bluetooth settings first
 * (PIN is usually 1234 or 0000), then pick it in the launcher's OBD settings.
 */
class ElmBluetoothSource(private val deviceAddress: String) : ObdSource {

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    override fun stream(link: ObdLink): Flow<ObdState> = flow {
        var data = ObdData()
        while (coroutineContext.isActive) {
            emit(ObdState(ObdStatus.CONNECTING, data))
            var socket: BluetoothSocket? = null
            try {
                @Suppress("DEPRECATION")
                val adapter = BluetoothAdapter.getDefaultAdapter()
                    ?: throw IllegalStateException("Bluetooth not available")
                if (!adapter.isEnabled) throw IllegalStateException("Bluetooth is off")
                val device = adapter.getRemoteDevice(deviceAddress)
                adapter.cancelDiscovery()
                socket = try {
                    device.createRfcommSocketToServiceRecord(sppUuid).also { it.connect() }
                } catch (e: Exception) {
                    // Some cheap clones only accept the insecure channel.
                    device.createInsecureRfcommSocketToServiceRecord(sppUuid).also { it.connect() }
                }
                val input = socket.inputStream
                val output = socket.outputStream
                for (cmd in listOf("ATZ", "ATE0", "ATL0", "ATS1", "ATH0", "ATSP0")) {
                    send(output, input, cmd, if (cmd == "ATZ") 2500 else 1500)
                }
                // Prime protocol detection.
                send(output, input, "0100", 6000)
                emit(ObdState(ObdStatus.CONNECTED, data))

                var slowTick = 0
                var extTick = 0
                while (coroutineContext.isActive) {
                    val speed = ElmParser.speedKmh(send(output, input, "010D"))
                    val rpm = ElmParser.rpm(send(output, input, "010C"))
                    data = data.copy(speedKmh = speed ?: data.speedKmh, rpm = rpm ?: data.rpm)
                    if (slowTick++ % 8 == 0) {
                        val coolant = ElmParser.coolantC(send(output, input, "0105"))
                        val volts = ElmParser.atrv(send(output, input, "ATRV"))
                        data = data.copy(coolantC = coolant ?: data.coolantC, batteryV = volts ?: data.batteryV)
                    }
                    if (link.extended.value) {
                        // One extra PID per cycle so speed and RPM stay responsive.
                        val pid = EXTRA_PIDS[extTick++ % EXTRA_PIDS.size]
                        if (pid !in data.unsupported) {
                            val raw = send(output, input, "01$pid")
                            data = if (ElmParser.noData(raw)) data.copy(unsupported = data.unsupported + pid) else applyExtra(data, pid, raw)
                        }
                    }
                    link.serve { cmd, t -> send(output, input, cmd, t) }
                    emit(ObdState(ObdStatus.CONNECTED, data))
                    delay(150)
                }
            } catch (e: Exception) {
                emit(ObdState(ObdStatus.ERROR, data, e.message ?: e.javaClass.simpleName))
                runCatching { socket?.close() }
                delay(5000) // retry
            } finally {
                runCatching { socket?.close() }
            }
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        val EXTRA_PIDS = listOf("04", "11", "0F", "10", "0E", "06", "07")

        fun applyExtra(d: ObdData, pid: String, raw: String): ObdData = when (pid) {
            "04" -> d.copy(loadPct = ElmParser.loadPct(raw) ?: d.loadPct)
            "11" -> d.copy(throttlePct = ElmParser.throttlePct(raw) ?: d.throttlePct)
            "0F" -> d.copy(intakeC = ElmParser.intakeC(raw) ?: d.intakeC)
            "10" -> d.copy(mafGs = ElmParser.mafGs(raw) ?: d.mafGs)
            "0E" -> d.copy(timingDeg = ElmParser.timingDeg(raw) ?: d.timingDeg)
            "06" -> d.copy(stftPct = ElmParser.fuelTrimPct(raw, "06") ?: d.stftPct)
            "07" -> d.copy(ltftPct = ElmParser.fuelTrimPct(raw, "07") ?: d.ltftPct)
            else -> d
        }
    }

    private suspend fun send(out: OutputStream, input: InputStream, cmd: String, timeoutMs: Long = 1200): String {
        out.write((cmd + "\r").toByteArray())
        out.flush()
        val sb = StringBuilder()
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                if (input.available() > 0) {
                    val c = input.read()
                    if (c < 0 || c.toChar() == '>') break
                    sb.append(c.toChar())
                } else {
                    delay(10)
                }
            }
        }
        return sb.toString()
    }
}
