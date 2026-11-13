package com.ahu.tlauncher.obd

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Demo data that behaves like a 1.3–1.5 L petrol MPV with a 5-speed manual:
 * warm-up, accelerating through gears (RPM drops at each shift), cruising, braking, idling.
 */
class SimulatedObdSource(private val tickMs: Long = 250) : ObdSource {
    override fun stream(): Flow<ObdState> = flow {
        var speed = 0.0
        var coolant = 38.0
        var t = 0.0
        val ratios = doubleArrayOf(0.0, 3.54, 2.05, 1.38, 1.03, 0.82) // gear ratios (illustrative)
        val finalDrive = 4.3
        val wheelCirc = 1.9 // metres, ~185/65R15
        while (true) {
            t += tickMs / 1000.0
            // 60 s driving cycle: 0–15 accelerate, 15–35 cruise, 35–45 brake, 45–60 idle
            val phase = t % 60.0
            val target = when {
                phase < 15 -> 70.0
                phase < 35 -> 60.0 + 8 * kotlin.math.sin(phase)
                phase < 45 -> 0.0
                else -> 0.0
            }
            val accel = if (target > speed) 1.6 else -2.6
            speed = if (accel > 0) min(target, speed + accel * tickMs / 250.0) else max(target, speed + accel * tickMs / 250.0)
            speed = max(0.0, speed)
            val gear = when {
                speed < 1 -> 0
                speed < 18 -> 1
                speed < 32 -> 2
                speed < 48 -> 3
                speed < 65 -> 4
                else -> 5
            }
            val rpm = if (gear == 0) 780.0 + Random.nextDouble(-20.0, 20.0) else {
                val wheelRps = speed / 3.6 / wheelCirc
                max(900.0, wheelRps * ratios[gear] * finalDrive * 60.0)
            }
            coolant = min(88.0, coolant + 0.12)
            val volts = if (rpm > 0) 14.1 + Random.nextDouble(-0.05, 0.05) else 12.6
            emit(
                ObdState(
                    status = ObdStatus.DEMO,
                    data = ObdData(
                        speedKmh = speed.roundToInt(),
                        rpm = (rpm / 10).roundToInt() * 10,
                        coolantC = coolant.roundToInt(),
                        batteryV = (volts * 10).roundToInt() / 10.0,
                    ),
                )
            )
            delay(tickMs)
        }
    }
}
