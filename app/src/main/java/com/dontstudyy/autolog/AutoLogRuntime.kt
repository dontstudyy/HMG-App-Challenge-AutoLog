package com.dontstudyy.autolog

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dontstudyy.autolog.data.AutoLogDb
import com.dontstudyy.autolog.data.RoomTripStore
import com.dontstudyy.autolog.data.SettingEntity
import com.dontstudyy.autolog.trip.Category
import com.dontstudyy.autolog.trip.Trip
import com.dontstudyy.autolog.trip.TripEngine
import com.dontstudyy.autolog.vehicle.Gear
import com.dontstudyy.autolog.vehicle.VehicleSource
import com.dontstudyy.autolog.vehicle.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * 프로세스 안에서 하나만 있는 앱 상태. 서비스가 [start]로 차량 연결과 엔진을 돌리고,
 * 화면은 [vehicle]과 [trips]만 구독한다(엔진을 직접 건드리지 않음).
 * 엔진·DB 호출은 모두 메인 스레드에서 한다.
 */
object AutoLogRuntime : VehicleSource.Callback {
    val vehicle = MutableStateFlow(VehicleState())
    val trips = MutableStateFlow<List<Trip>>(emptyList())

    /** '나중에 정리'를 누른 빈 구간 운행 id. */
    val dismissedGaps = MutableStateFlow<Set<Long>>(emptySet())

    private lateinit var db: AutoLogDb
    private lateinit var store: RoomTripStore
    private lateinit var engine: TripEngine
    private lateinit var appContext: Context
    private val main = Handler(Looper.getMainLooper())
    private var source: VehicleSource? = null
    private var engineStarted = false
    private var inited = false

    private val tickRunnable = Runnable { tick() }

    fun init(ctx: Context) {
        if (inited) return
        appContext = ctx.applicationContext
        db = AutoLogDb.create(appContext)
        store = RoomTripStore(db.dao())
        engine = TripEngine(store)
        dismissedGaps.value = db.dao().getSetting(KEY_DISMISSED)
            ?.split(',')?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        refresh()
        inited = true
    }

    fun start(ctx: Context) {
        init(ctx)
        if (source != null) return
        source = VehicleSource(appContext, this).also { it.start() }
    }

    fun stop() {
        source?.stop()
        source = null
        engineStarted = false
        main.removeCallbacks(tickRunnable)
        vehicle.update { it.copy(connected = false) }
    }

    // ---- 화면에서 부르는 동작 ----

    fun reclassify(tripId: Long, category: Category) {
        if (!inited) return
        engine.reclassify(tripId, category)
        refresh()
    }

    fun dismissGap(tripId: Long) {
        val next = dismissedGaps.value + tripId
        dismissedGaps.value = next
        db.dao().putSetting(SettingEntity(KEY_DISMISSED, next.joinToString(",")))
    }

    // ---- VehicleSource.Callback (메인 스레드) ----

    override fun onReady(odoTenths: Int, gear: Gear) {
        Log.i(TAG, "READY odo=$odoTenths gear=$gear")
        vehicle.update { it.copy(odoTenths = odoTenths, gear = gear, connected = true, odoUpdatedAtMs = System.currentTimeMillis()) }
        engine.onServiceStart(System.currentTimeMillis(), odoTenths, isPark = gear == Gear.PARK || gear == Gear.UNKNOWN)
        engineStarted = true
        afterEngine()
    }

    override fun onGear(gear: Gear, odoTenths: Int?, atMs: Long) {
        Log.i(TAG, "GEAR $gear odo=$odoTenths")
        vehicle.update { it.copy(gear = gear, odoTenths = odoTenths ?: it.odoTenths) }
        if (!engineStarted || gear == Gear.UNKNOWN) return
        val odo = odoTenths ?: vehicle.value.odoTenths ?: return
        engine.onGearChanged(atMs, isPark = gear == Gear.PARK, odoTenths = odo)
        afterEngine()
    }

    override fun onOdometer(odoTenths: Int, atMs: Long) {
        Log.i(TAG, "ODO $odoTenths")
        vehicle.update { it.copy(odoTenths = odoTenths, odoUpdatedAtMs = atMs) }
        if (!engineStarted) return
        engine.onOdometer(atMs, odoTenths)
        afterEngine()
    }

    override fun onUx(restricted: Boolean) {
        vehicle.update { it.copy(uxRestricted = restricted) }
    }

    // ---- 내부 ----

    private fun tick() {
        engine.tick(System.currentTimeMillis())
        afterEngine()
    }

    /** 엔진을 부른 뒤: 화면 갱신, P 직후 대기 중이면 대기가 끝날 때 tick을 예약. */
    private fun afterEngine() {
        refresh()
        main.removeCallbacks(tickRunnable)
        engine.msUntilSettle(System.currentTimeMillis())?.let { main.postDelayed(tickRunnable, it + 20) }
    }

    private fun refresh() {
        trips.value = store.all()
    }

    private const val KEY_DISMISSED = "dismissedGaps"
    private const val TAG = "AutoLogRT"
}
