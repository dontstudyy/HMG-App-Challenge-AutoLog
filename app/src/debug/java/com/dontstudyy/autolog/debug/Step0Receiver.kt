package com.dontstudyy.autolog.debug

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.listener.SeatBackrestAnglePositionListener
import ai.pleos.playground.vehicle.listener.SeatForeAftPositionListener
import ai.pleos.playground.vehicle.listener.SeatHeightPositionListener
import ai.pleos.playground.vehicle.listener.SeatTiltPositionListener
import android.car.Car
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.dontstudyy.autolog.voice.Listener
import com.dontstudyy.autolog.voice.Speaker
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper

/**
 * 단계 0 확인 실험 ③④⑤ (debug 빌드 전용).
 * adb shell am broadcast -n com.dontstudyy.autolog/.debug.Step0Receiver -a EXP --es id <seat|pbrake|loc|prox|stop> [--es arg ...]
 */
class Step0Receiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val arg = intent.getStringExtra("arg") ?: ""
        val app = ctx.applicationContext
        try {
            when (id) {
                "seat" -> Exp.seat(app)
                "pbrake" -> Exp.pbrake(app)
                "loc" -> Exp.loc(app)
                "prox" -> Exp.prox(app, arg)
                "tts" -> Exp.ttsRepeat(app)
                "stt" -> Exp.stt(app, arg)
                "route" -> Exp.route(app, arg)
                "stop" -> Exp.stop()
                else -> Exp.log("unknown id $id")
            }
        } catch (t: Throwable) {
            Exp.log("[$id] EXCEPTION ${t.javaClass.name}: ${t.message}")
        }
    }
}

private object Exp {
    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var fused: FusedLocation? = null
    private var locL: FusedLocationListener? = null
    private val alertIds = mutableListOf<String>()

    fun log(m: String) = Log.i("AUTOLOG_EXP", "e=${SystemClock.elapsedRealtime()} $m")

    private fun veh(ctx: Context) = vehicle ?: Vehicle(ctx).also { it.initialize(); vehicle = it }

    private val ok = fun(tag: String): (Exception) -> Unit = { e -> log("$tag FAIL ${e.javaClass.simpleName}: ${e.message}") }

    // ---- ④ 좌석 위치·등받이 각도 ----
    fun seat(ctx: Context) {
        val s = veh(ctx).getSeat()
        val areas = listOf("1L" to SeatValue.ROW_1_LEFT, "1R" to SeatValue.ROW_1_RIGHT)
        s.checkSeatForeAftPositionCapabilities({ cfg, map -> log("④ foreAft capability cfg=$cfg areas=${map.keys.map { it.values.toList() }} bounds=$map") }, ok("④ foreAft cap"))
        for ((n, v) in areas) {
            val a = SeatArea(v)
            s.getCurrentSeatOccupancy(a, { log("④[$n] occupancy(SDK값)=$it") }, ok("④[$n] occupancy"))
            s.getCurrentSeatForeAftPosition(a, { log("④[$n] foreAft=$it") }, ok("④[$n] foreAft"))
            s.getCurrentSeatBackrestAnglePosition(a, { log("④[$n] backrest=$it") }, ok("④[$n] backrest"))
            s.getCurrentSeatHeightPosition(a, { log("④[$n] height=$it") }, ok("④[$n] height"))
            s.getCurrentSeatTiltPosition(a, { log("④[$n] tilt=$it") }, ok("④[$n] tilt"))
        }
        val a1 = SeatArea(SeatValue.ROW_1_LEFT)
        runCatching {
            s.registerSeatForeAftPosition(a1, object : SeatForeAftPositionListener {
                override fun onSeatForeAftPositionUpdated(p0: Int?) { log("④ EVT 1L foreAft=$p0") }
                override fun onFailed(e: Exception) { log("④ foreAft listener onFailed $e") }
            })
            s.registerSeatBackrestAnglePosition(a1, object : SeatBackrestAnglePositionListener {
                override fun onSeatBackrestAnglePositionUpdated(p0: Int?) { log("④ EVT 1L backrest=$p0") }
                override fun onFailed(e: Exception) { log("④ backrest listener onFailed $e") }
            })
            s.registerSeatHeightPosition(a1, object : SeatHeightPositionListener {
                override fun onSeatHeightPositionUpdated(p0: Int?) { log("④ EVT 1L height=$p0") }
                override fun onFailed(e: Exception) { log("④ height listener onFailed $e") }
            })
            s.registerSeatTiltPosition(a1, object : SeatTiltPositionListener {
                override fun onSeatTiltPositionUpdated(p0: Int?) { log("④ EVT 1L tilt=$p0") }
                override fun onFailed(e: Exception) { log("④ tilt listener onFailed $e") }
            })
            log("④ listeners registered (1L)")
        }.onFailure { log("④ register FAIL $it") }
    }

    // ---- ③ 주차 브레이크 (AAOS PARKING_BRAKE_ON 0x11200402) ----
    fun pbrake(ctx: Context) {
        val c = car ?: Car.createCar(ctx).also { car = it }
        val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        val id = 0x11200402
        runCatching { log("③ PARKING_BRAKE_ON bool=${pm.getBooleanProperty(id, 0)}") }
            .onFailure { log("③ PARKING_BRAKE_ON read FAIL ${it.javaClass.simpleName}: ${it.message}") }
        runCatching {
            val r = pm.registerCallback(object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(p: CarPropertyValue<*>) { log("③ EVT PARKING_BRAKE_ON=${p.value}") }
                override fun onErrorEvent(propId: Int, zone: Int) { log("③ EVT ERR $propId/$zone") }
            }, id, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            log("③ registerCallback -> $r")
        }.onFailure { log("③ register FAIL ${it.javaClass.simpleName}: ${it.message}") }
    }

    // ---- ⑤ FusedLocation ----
    fun loc(ctx: Context) {
        val f = fused ?: FusedLocation(ctx).also { it.initialize(); fused = it }
        log("⑤ isSupported=${f.isSupported()}")
        f.getLastKnownLocation(object : FusedLocationListener {
            override fun onChanged(location: LocationData?) { log("⑤ lastKnown ${fmt(location)}") }
            override fun onError(p0: Exception) { log("⑤ lastKnown onError $p0") }
        })
        if (locL == null) {
            val l = object : FusedLocationListener {
                override fun onChanged(location: LocationData?) { log("⑤ EVT ${fmt(location)}") }
                override fun onError(p0: Exception) { log("⑤ EVT onError $p0") }
            }
            locL = l
            f.registerFusedLocationCallback(l)
            log("⑤ callback registered")
        }
    }

    private fun fmt(l: LocationData?) =
        l?.let { "lat=${it.latitude} lon=${it.longitude} heading=${it.heading} conf=${it.confidence} ts=${it.timestampMillis}" } ?: "null"

    /** arg = "lat,lon,radius" */
    fun prox(ctx: Context, arg: String) {
        val f = fused ?: FusedLocation(ctx).also { it.initialize(); fused = it }
        val p = arg.split(',').map { it.trim().toDouble() }
        val req = ProximityAlertRequest(p[0], p[1], p[2].toFloat(), setOf(ProximityTransition.ENTER, ProximityTransition.EXIT))
        log("⑤ addProximityAlert validation=${runCatching { req.toString() }.getOrNull()}")
        f.addProximityAlert(req, object : ProximityAlertListener {
            override fun onRegistered(p0: String) { alertIds += p0; log("⑤ PROX registered id=$p0") }
            override fun onTransition(p0: ProximityAlertEvent) { log("⑤ PROX EVT id=${p0.alertId} transition=${p0.transition}") }
            override fun onError(p0: Exception) { log("⑤ PROX onError $p0") }
        })
    }

    // ---- ⑦ TTS 반복 호출: 호출마다 새로 만들어 3번 재생되는지 ----
    fun ttsRepeat(ctx: Context) {
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        for (i in 0 until 3) h.postDelayed({ Speaker.speak(ctx, "테스트 ${i + 1}번 입니다") }, i * 4000L)
        log("⑦ TTS 3회 예약 (4초 간격). 로그 AUTOLOG_VOICE 확인")
    }

    // ---- ⑧ STT 부분 결과: arg=file이면 filesDir/speech.pcm을 보낸다 ----
    fun stt(ctx: Context, arg: String) {
        val pcm = if (arg == "file") java.io.File(ctx.filesDir, "speech.pcm") else null
        log("⑧ STT 시작 pcm=${pcm?.let { "${it.exists()} ${it.length()}B" } ?: "마이크"}")
        Listener.listen(ctx, pcm, onAnswer = { log("⑧ STT 답: $it") }, onEnd = { log("⑧ STT 종료") })
    }

    // ---- ⑥ 길안내 요청 (좌표 arg=lat,lon,이름) ----
    private var naviForRoute: NaviHelper? = null
    fun route(ctx: Context, arg: String) {
        val n = naviForRoute ?: NaviHelper(ctx).also { it.initialize(); naviForRoute = it }
        if (arg == "cancel") { n.cancelRoute(); log("⑥ cancelRoute"); return }
        val p = arg.split(',')
        n.requestRoute(RouteInfo(latitude = p[0].toDouble(), longitude = p[1].toDouble(), poiName = p[2], poiId = "", address = "", poiSubId = ""))
        log("⑥ requestRoute ${p[2]}")
    }

    fun stop() {
        runCatching { alertIds.forEach { fused?.removeProximityAlert(it) } }
        alertIds.clear()
        runCatching { locL?.let { fused?.unregisterFusedLocationCallback(it) } }
        locL = null
        runCatching { fused?.release() }
        fused = null
        runCatching { vehicle?.release() }
        vehicle = null
        runCatching { car?.disconnect() }
        car = null
        log("stop: released")
    }
}
