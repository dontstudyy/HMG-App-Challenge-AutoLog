package com.dontstudyy.autolog

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dontstudyy.autolog.classify.Place
import com.dontstudyy.autolog.classify.PlaceKind
import com.dontstudyy.autolog.classify.Verdict
import com.dontstudyy.autolog.classify.classify
import com.dontstudyy.autolog.classify.findPlace
import com.dontstudyy.autolog.data.AutoLogDb
import com.dontstudyy.autolog.data.Profile
import com.dontstudyy.autolog.data.ProfileEntity
import com.dontstudyy.autolog.data.RoomTripStore
import com.dontstudyy.autolog.data.SettingEntity
import com.dontstudyy.autolog.data.toEntity
import com.dontstudyy.autolog.data.toPlace
import com.dontstudyy.autolog.data.toProfile
import com.dontstudyy.autolog.location.Fix
import com.dontstudyy.autolog.location.LocationSource
import com.dontstudyy.autolog.navi.NaviSource
import com.dontstudyy.autolog.report.AppSettings
import com.dontstudyy.autolog.report.ExportResult
import com.dontstudyy.autolog.report.LogbookDoc
import com.dontstudyy.autolog.report.buildLogbookDoc
import com.dontstudyy.autolog.report.exportLogbook
import com.dontstudyy.autolog.report.monthBounds
import com.dontstudyy.autolog.report.summarize
import com.dontstudyy.autolog.report.tripsOfMonth
import com.dontstudyy.autolog.trip.Category
import com.dontstudyy.autolog.trip.Trip
import com.dontstudyy.autolog.trip.TripEngine
import com.dontstudyy.autolog.trip.TripKind
import com.dontstudyy.autolog.trip.TripStatus
import com.dontstudyy.autolog.trip.Where
import com.dontstudyy.autolog.vehicle.Gear
import com.dontstudyy.autolog.vehicle.VehicleSource
import com.dontstudyy.autolog.vehicle.VehicleState
import com.dontstudyy.autolog.voice.Answer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** 일반업무로 답한 미등록 위치를 거래처로 저장할지 묻는 카드(설계서 7장). */
data class SavePlacePrompt(val lat: Double, val lon: Double)

/**
 * 프로세스 안에서 하나만 있는 앱 상태. 서비스가 [start]로 차량·위치·내비 연결과 엔진을 돌리고,
 * 화면은 StateFlow만 구독한다(엔진을 직접 건드리지 않음).
 * 엔진·DB 호출은 모두 메인 스레드에서 한다.
 */
object AutoLogRuntime : VehicleSource.Callback {
    val vehicle = MutableStateFlow(VehicleState())
    val trips = MutableStateFlow<List<Trip>>(emptyList())
    val places = MutableStateFlow<List<Place>>(emptyList())
    val profiles = MutableStateFlow<List<Profile>>(emptyList())
    val currentUserId = MutableStateFlow(0L)
    val lastFix = MutableStateFlow<Fix?>(null)
    val savePrompt = MutableStateFlow<SavePlacePrompt?>(null)
    val settings = MutableStateFlow(AppSettings())

    /** '나중에'를 누른 운행 id(질문 카드와 빈 구간 카드 공용). */
    val dismissedGaps = MutableStateFlow<Set<Long>>(emptySet())

    private lateinit var db: AutoLogDb
    private lateinit var store: RoomTripStore
    private lateinit var engine: TripEngine
    private lateinit var appContext: Context
    private val main = Handler(Looper.getMainLooper())
    private var source: VehicleSource? = null
    private var locationSource: LocationSource? = null
    private var naviSource: NaviSource? = null
    private var engineStarted = false
    private var inited = false

    /** 길안내가 시작됐지만 아직 운행에 붙이지 못한 정보(운행 시작 전에 목적지를 정한 경우). */
    private var pendingNavi: Pair<String, Int>? = null

    /** Gleo START_PRIVATE_TRIP: 이번(또는 다음) 운행을 개인 용무로 기록. */
    private var forcePrivate = false

    private val tickRunnable = Runnable { tick() }

    fun init(ctx: Context) {
        if (inited) return
        appContext = ctx.applicationContext
        db = AutoLogDb.create(appContext)
        store = RoomTripStore(db.dao())
        engine = TripEngine(store).also { it.onTripDone = ::autoClassify }
        dismissedGaps.value = db.dao().getSetting(KEY_DISMISSED)
            ?.split(',')?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        if (db.dao().profiles().isEmpty()) db.dao().insertProfile(ProfileEntity(dept = "", name = "운전자"))
        profiles.value = db.dao().profiles().map { it.toProfile() }
        places.value = db.dao().places().map { it.toPlace() }
        val saved = db.dao().getSetting(KEY_USER)?.toLongOrNull()
        currentUserId.value = profiles.value.firstOrNull { it.id == saved }?.id ?: profiles.value.first().id
        settings.value = loadSettings()
        refresh()
        inited = true
    }

    fun start(ctx: Context) {
        init(ctx)
        if (source != null) return
        source = VehicleSource(appContext, this).also { it.start() }
        locationSource = LocationSource(appContext) { lastFix.value = it }.also { it.start() }
        naviSource = NaviSource(appContext, ::onNaviRoute, ::onNaviCleared).also { it.start() }
    }

    fun stop() {
        source?.stop()
        source = null
        locationSource?.stop()
        locationSource = null
        naviSource?.stop()
        naviSource = null
        engineStarted = false
        main.removeCallbacks(tickRunnable)
        vehicle.update { it.copy(connected = false) }
    }

    // ---- 화면에서 부르는 동작 ----

    /** 분류를 바꾼다(미분류 정리 등). */
    fun reclassify(tripId: Long, category: Category, note: String? = null) {
        if (!inited) return
        engine.reclassify(tripId, category, note)
        refresh()
    }

    /** 주차 후 질문에 대한 답. 일반업무로 답했는데 도착 위치가 미등록이면 거래처 저장 여부를 묻는다. */
    fun answer(tripId: Long, category: Category, note: String? = null) {
        if (!inited) return
        val trip = trips.value.firstOrNull { it.id == tripId }
        reclassify(tripId, category, note)
        if (category == Category.BUSINESS && trip != null && trip.endPlace.isBlank() && trip.endLat != null && trip.endLon != null) {
            savePrompt.value = SavePlacePrompt(trip.endLat, trip.endLon)
        }
    }

    fun dismissGap(tripId: Long) {
        val next = dismissedGaps.value + tripId
        dismissedGaps.value = next
        db.dao().putSetting(SettingEntity(KEY_DISMISSED, next.joinToString(",")))
    }

    fun saveClientPlace(p: SavePlacePrompt) {
        val n = places.value.count { it.kind == PlaceKind.CLIENT } + 1
        addPlace(Place(name = "거래처 $n", kind = PlaceKind.CLIENT, lat = p.lat, lon = p.lon))
        savePrompt.value = null
    }

    fun skipSavePlace() {
        savePrompt.value = null
    }

    /** 마지막으로 받은 위치를 장소로 등록한다. 위치를 아직 못 받았으면 false. */
    fun registerPlaceHere(kind: PlaceKind, name: String): Boolean {
        val fix = lastFix.value ?: return false
        addPlace(Place(name = name.ifBlank { kind.label }, kind = kind, lat = fix.lat, lon = fix.lon))
        return true
    }

    fun deletePlace(id: Long) {
        db.dao().deletePlace(id)
        places.value = db.dao().places().map { it.toPlace() }
    }

    fun addProfile(dept: String, name: String) {
        if (name.isBlank()) return
        db.dao().insertProfile(ProfileEntity(dept = dept.trim(), name = name.trim()))
        profiles.value = db.dao().profiles().map { it.toProfile() }
    }

    fun deleteProfile(id: Long) {
        if (profiles.value.size <= 1) return
        db.dao().deleteProfile(id)
        profiles.value = db.dao().profiles().map { it.toProfile() }
        if (currentUserId.value == id) selectUser(profiles.value.first().id)
    }

    fun selectUser(id: Long) {
        currentUserId.value = id
        db.dao().putSetting(SettingEntity(KEY_USER, id.toString()))
    }

    fun updateSettings(s: AppSettings) {
        settings.value = s
        val d = db.dao()
        d.putSetting(SettingEntity(S_VEHICLE, s.vehicleType))
        d.putSetting(SettingEntity(S_PLATE, s.plate))
        d.putSetting(SettingEntity(S_COMPANY, s.company))
        d.putSetting(SettingEntity(S_BIZNO, s.bizNo))
        d.putSetting(SettingEntity(S_L100, s.litersPer100km.toString()))
        d.putSetting(SettingEntity(S_PRICE, s.wonPerLiter.toString()))
    }

    // ---- Gleo(adb intent)와 음성에서 부르는 동작 ----

    /** LOG_TRIP_PURPOSE: 가장 최근의 미분류 운행(없으면 가장 최근 운행)에 목적을 기록한다. 기록한 운행을 돌려준다. */
    fun logPurpose(answer: Answer): Trip? {
        if (!inited) return null
        val normal = trips.value.filter { it.kind == TripKind.NORMAL && it.status == TripStatus.DONE }
        val target = normal.lastOrNull { it.category == Category.UNCLASSIFIED && it.id !in dismissedGaps.value }
            ?: normal.lastOrNull()
            ?: return null
        answer(target.id, answer.category, answer.note)
        return target
    }

    /** START_PRIVATE_TRIP: 이번(또는 다음) 운행을 개인 용무로 기록한다. */
    fun startPrivateTrip() {
        forcePrivate = true
    }

    /** GET_BUSINESS_RATIO: 이번 달 업무사용비율(예: 85.8). 총 주행거리가 0이면 null. */
    fun businessRatioText(): String? = summarize(tripsOfMonth(trips.value, System.currentTimeMillis())).ratioText()

    /** 이번 달 운행기록부 내용. */
    fun currentLogbookDoc(): LogbookDoc {
        val now = System.currentTimeMillis()
        val (start, end) = monthBounds(now)
        val users = profiles.value.associate { it.id to (it.dept to it.name) }
        return buildLogbookDoc(tripsOfMonth(trips.value, now), users, settings.value, start, end)
    }

    /** 엑셀·PDF를 앱 전용 폴더에 저장한다. */
    fun export(): ExportResult = exportLogbook(appContext, currentLogbookDoc(), System.currentTimeMillis())

    // ---- VehicleSource.Callback (메인 스레드) ----

    override fun onReady(odoTenths: Int, gear: Gear) {
        Log.i(TAG, "READY odo=$odoTenths gear=$gear")
        vehicle.update { it.copy(odoTenths = odoTenths, gear = gear, connected = true, odoUpdatedAtMs = System.currentTimeMillis()) }
        engine.onServiceStart(
            System.currentTimeMillis(), odoTenths, isPark = gear == Gear.PARK || gear == Gear.UNKNOWN,
            userId = currentUserId.value, where = whereNow(),
        )
        engineStarted = true
        attachPendingNavi()
        afterEngine()
    }

    override fun onGear(gear: Gear, odoTenths: Int?, atMs: Long) {
        Log.i(TAG, "GEAR $gear odo=$odoTenths")
        vehicle.update { it.copy(gear = gear, odoTenths = odoTenths ?: it.odoTenths) }
        if (!engineStarted || gear == Gear.UNKNOWN) return
        val odo = odoTenths ?: vehicle.value.odoTenths ?: return
        engine.onGearChanged(atMs, isPark = gear == Gear.PARK, odoTenths = odo, userId = currentUserId.value, where = whereNow())
        if (gear != Gear.PARK) attachPendingNavi()
        afterEngine()
    }

    override fun onOdometer(odoTenths: Int, atMs: Long) {
        vehicle.update { it.copy(odoTenths = odoTenths, odoUpdatedAtMs = atMs) }
        if (!engineStarted) return
        engine.onOdometer(atMs, odoTenths)
        afterEngine()
    }

    override fun onUx(restricted: Boolean) {
        vehicle.update { it.copy(uxRestricted = restricted) }
    }

    // ---- 내비 ----

    private fun onNaviRoute(dest: String, tollWon: Int) {
        pendingNavi = dest to tollWon
        if (inited && engine.attachNavi(dest, tollWon)) refresh()
    }

    private fun onNaviCleared() {
        pendingNavi = null
    }

    private fun attachPendingNavi() {
        val p = pendingNavi ?: return
        if (engine.attachNavi(p.first, p.second)) refresh()
    }

    // ---- 내부 ----

    /** 지금 위치(마지막으로 받은 위치)가 속한 등록 장소. 위치가 끊겨도 마지막 위치로 판정한다(설계서 7장). */
    private fun whereNow(): Where {
        val fix = lastFix.value ?: return Where()
        return Where(findPlace(fix.lat, fix.lon, places.value)?.name ?: "", fix.lat, fix.lon)
    }

    /** 운행이 끝났을 때 자동 분류. 확실하지 않으면 미분류로 남겨 질문 카드가 뜨게 한다. */
    private fun autoClassify(trip: Trip) {
        pendingNavi = null
        if (forcePrivate) {
            forcePrivate = false
            engine.reclassify(trip.id, Category.PRIVATE, "개인 용무")
            return
        }
        val all = places.value
        val start = all.firstOrNull { it.name == trip.startPlace }
        val end = all.firstOrNull { it.name == trip.endPlace }
        val v = classify(start, end, naviDestination = trip.naviDest.ifBlank { null }, places = all)
        if (v is Verdict.Auto) engine.reclassify(trip.id, v.category, v.note)
    }

    private fun addPlace(p: Place) {
        db.dao().insertPlace(p.toEntity().copy(id = 0))
        places.value = db.dao().places().map { it.toPlace() }
    }

    private fun loadSettings(): AppSettings {
        val d = db.dao()
        val def = AppSettings()
        return AppSettings(
            vehicleType = d.getSetting(S_VEHICLE) ?: def.vehicleType,
            plate = d.getSetting(S_PLATE) ?: def.plate,
            company = d.getSetting(S_COMPANY) ?: def.company,
            bizNo = d.getSetting(S_BIZNO) ?: def.bizNo,
            litersPer100km = d.getSetting(S_L100)?.toDoubleOrNull() ?: def.litersPer100km,
            wonPerLiter = d.getSetting(S_PRICE)?.toIntOrNull() ?: def.wonPerLiter,
        )
    }

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
    private const val KEY_USER = "currentUser"
    private const val S_VEHICLE = "set_vehicle"
    private const val S_PLATE = "set_plate"
    private const val S_COMPANY = "set_company"
    private const val S_BIZNO = "set_bizno"
    private const val S_L100 = "set_l100"
    private const val S_PRICE = "set_price"
    private const val TAG = "AutoLogRT"
}
