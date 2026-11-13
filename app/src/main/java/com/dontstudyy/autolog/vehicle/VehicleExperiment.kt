package com.dontstudyy.autolog.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import android.car.Car
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.SystemClock
import android.util.Log

/** 단계 0 확인 실험 ①② 전용. 결과 확인 후 정식 vehicle 층으로 교체한다. 로그 태그: AUTOLOG_EXP */
class VehicleExperiment(private val ctx: Context) {
    private var vehicle: Vehicle? = null
    private var car: Car? = null
    @Volatile private var lastListenerKm: Float? = null

    private fun log(m: String) = Log.i("AUTOLOG_EXP", "e=${SystemClock.elapsedRealtime()} $m")

    fun start() {
        val v = Vehicle(ctx).also { it.initialize() }
        vehicle = v
        val odo = v.getOdometer()
        odo.getCurrentDistanceDriven({ d -> lastListenerKm = d; log("① init getCurrentDistanceDriven=$d") },
            { e -> log("① init getCurrentDistanceDriven FAIL $e") })
        odo.registerDistanceDriven(object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(p0: Float?) { lastListenerKm = p0; log("① EVT listener distance=$p0") }
            override fun onFailed(e: Exception) { log("① listener onFailed $e") }
        })

        val c = Car.createCar(ctx)
        car = c
        log("Car.createCar isConnected=${c?.isConnected}")
        val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        val cb = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(p: CarPropertyValue<*>) {
                when (p.propertyId) {
                    GEAR -> {
                        val t0 = SystemClock.elapsedRealtime()
                        log("② EVT gear=${p.value} listenerLast=$lastListenerKm")
                        // 기어 이벤트를 받은 순간 즉시 읽기 (동기: AAOS PERF_ODOMETER)
                        val sync = runCatching { pm.getFloatProperty(ODO, 0) }
                        log("② gear 직후 AAOS PERF_ODOMETER sync=${sync.getOrNull() ?: sync.exceptionOrNull()} (+${SystemClock.elapsedRealtime() - t0}ms)")
                        // 기어 이벤트 직후 Pleos getCurrentDistanceDriven
                        odo.getCurrentDistanceDriven({ d -> log("② gear 직후 Pleos getCurrentDistanceDriven=$d (+${SystemClock.elapsedRealtime() - t0}ms)") },
                            { e -> log("② gear 직후 getCurrentDistanceDriven FAIL $e") })
                    }
                    ODO -> log("① EVT AAOS PERF_ODOMETER=${p.value}")
                }
            }
            override fun onErrorEvent(propId: Int, zone: Int) { log("EVT ERR prop=0x%08X zone=$zone".format(propId)) }
        }
        for ((id, nm) in listOf(GEAR to "GEAR_SELECTION", ODO to "PERF_ODOMETER")) {
            runCatching { pm.registerCallback(cb, id, CarPropertyManager.SENSOR_RATE_FASTEST) }
                .onSuccess { log("register $nm -> $it") }.onFailure { log("register $nm FAIL $it") }
        }
        runCatching { log("초기 GEAR_SELECTION=${pm.getIntProperty(GEAR, 0)}") }.onFailure { log("초기 gear FAIL $it") }
        runCatching { log("초기 AAOS PERF_ODOMETER=${pm.getFloatProperty(ODO, 0)}") }.onFailure { log("초기 odo FAIL $it") }
    }

    fun stop() {
        runCatching { vehicle?.release() }
        runCatching { car?.disconnect() }
    }

    private companion object {
        const val GEAR = 0x11400400
        const val ODO = 0x11600204
    }
}
