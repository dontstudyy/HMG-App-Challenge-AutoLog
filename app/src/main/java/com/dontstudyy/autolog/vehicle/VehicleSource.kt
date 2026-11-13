package com.dontstudyy.autolog.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dontstudyy.autolog.trip.kmToTenths

/**
 * 차량 신호를 읽는 층. Pleos Odometer(누적거리), AAOS GEAR_SELECTION(기어), CarUxRestrictionsManager(주행 규제).
 * 모든 콜백은 메인 스레드로 전달한다(엔진을 한 스레드에서만 부르기 위해).
 *
 * 기어 이벤트를 받으면 그 순간 누적거리를 다시 읽어 함께 넘긴다(단계 0 ② 실험).
 * 주행거리 갱신이 기어보다 늦게 올 수 있으므로, 이후의 [Callback.onOdometer]로 엔진이 마지막 값을 고친다.
 */
class VehicleSource(private val ctx: Context, private val cb: Callback) {
    interface Callback {
        fun onReady(odoTenths: Int, gear: Gear)
        fun onGear(gear: Gear, odoTenths: Int?, atMs: Long)
        fun onOdometer(odoTenths: Int, atMs: Long)
        fun onUx(restricted: Boolean)
    }

    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var pm: CarPropertyManager? = null
    @Volatile private var lastOdo: Int? = null
    private var stopped = false
    private var ready = false
    private var registered = false

    fun start() {
        stopped = false
        connect()
    }

    private fun connect() {
        if (stopped || ready) return
        try {
            if (!registered) {
                val v = vehicle ?: Vehicle(ctx).also { it.initialize(); vehicle = it }
                val odo = v.getOdometer()
                odo.registerDistanceDriven(object : DistanceDrivenListener {
                    override fun onDistanceDrivenUpdated(p0: Float?) {
                        val t = p0?.let { kmToTenths(it) } ?: return
                        val at = System.currentTimeMillis()
                        main.post { lastOdo = t; cb.onOdometer(t, at) }
                    }
                    override fun onFailed(e: Exception) { Log.w(TAG, "odometer listener failed $e") }
                })

                val c = Car.createCar(ctx)
                car = c
                val manager = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                pm = manager
                manager.registerCallback(object : CarPropertyManager.CarPropertyEventCallback {
                    override fun onChangeEvent(p: CarPropertyValue<*>) {
                        if (p.propertyId != GEAR_SELECTION) return
                        val gear = Gear.fromAaos(p.value as? Int)
                        val at = System.currentTimeMillis()
                        readOdometer { odo -> cb.onGear(gear, odo ?: lastOdo, at) }
                    }
                    override fun onErrorEvent(propId: Int, zone: Int) { Log.w(TAG, "gear error $propId/$zone") }
                }, GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_FASTEST)

                runCatching {
                    val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                    cb.onUx(ux.currentCarUxRestrictions.isRequiresDistractionOptimization)
                    ux.registerListener { r -> main.post { cb.onUx(r.isRequiresDistractionOptimization) } }
                }.onFailure { Log.w(TAG, "ux restrictions unavailable $it") }

                registered = true
            }
            val manager = pm ?: throw IllegalStateException("no property manager")

            // 시작 시점의 누적거리와 기어를 읽으면 준비 완료
            readOdometer { odo ->
                if (odo == null) { retry(); return@readOdometer }
                lastOdo = odo
                ready = true
                cb.onReady(odo, Gear.fromAaos(runCatching { manager.getIntProperty(GEAR_SELECTION, 0) }.getOrNull()))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "connect failed: $t")
            retry()
        }
    }

    private fun retry() {
        if (!stopped && !ready) main.postDelayed({ connect() }, RETRY_MS)
    }

    /** 누적거리를 지금 읽는다. 결과는 메인 스레드로 돌려준다(실패하면 null). */
    private fun readOdometer(done: (Int?) -> Unit) {
        val v = vehicle
        if (v == null) { main.post { done(null) }; return }
        v.getOdometer().getCurrentDistanceDriven(
            { d -> main.post { done(d?.let { kmToTenths(it) }) } },
            { e -> Log.w(TAG, "getCurrentDistanceDriven failed $e"); main.post { done(null) } },
        )
    }

    fun stop() {
        stopped = true
        main.removeCallbacksAndMessages(null)
        runCatching { vehicle?.release() }
        runCatching { car?.disconnect() }
        vehicle = null; car = null; pm = null; ready = false; registered = false
    }

    private companion object {
        const val TAG = "AutoLogVehicle"
        const val GEAR_SELECTION = 0x11400400
        const val RETRY_MS = 3_000L
    }
}
