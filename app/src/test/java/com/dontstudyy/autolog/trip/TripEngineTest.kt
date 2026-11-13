package com.dontstudyy.autolog.trip

import com.dontstudyy.autolog.report.logbookRows
import com.dontstudyy.autolog.report.summarize
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class TripEngineTest {

    private fun km(v: Double) = Math.round(v * 10).toInt()

    /** 모든 운행의 주행 전 = 바로 앞 운행의 주행 후. 진행 중 운행은 마지막에만 올 수 있다. */
    private fun assertContinuous(store: TripStore) {
        val done = store.all().filter { it.status == TripStatus.DONE }
        for (i in 1 until done.size) {
            assertEquals("운행 ${i + 1}의 주행 전 != 직전 주행 후", done[i - 1].endTenths, done[i].startTenths)
        }
        store.all().filter { it.status == TripStatus.IN_PROGRESS }.forEach {
            assertEquals("진행 중 운행의 주행 전 != 마지막 주행 후", store.lastEndTenths, it.startTenths)
        }
        assertTrue("진행 중 운행은 1건 이하", store.all().count { it.status == TripStatus.IN_PROGRESS } <= 1)
    }

    /** P→D, 주행거리 올림, D→P, 대기시간 경과까지 한 번에. */
    private fun TripEngine.drive(t0: Long, from: Int, to: Int): Long {
        onGearChanged(t0, false, from)
        onOdometer(t0 + 1_000, (from + to) / 2)
        onOdometer(t0 + 2_000, to)
        onGearChanged(t0 + 2_100, true, to)
        tick(t0 + 2_100 + TripEngine.SETTLE_MS)
        return t0 + 10_000
    }

    @Test
    fun trips_json_6건이_82_1에서_85_8로() {
        val json = JSONObject(javaClass.classLoader!!.getResource("trips.json")!!.readText())
        val expect = json.getJSONObject("expect")
        val rows = json.getJSONArray("trips")
        val store = InMemoryTripStore()
        val engine = TripEngine(store)

        // 준비: 기어 P, 첫 실행 → 기준값만
        val first = rows.getJSONObject(0)
        engine.onServiceStart(0, first.getInt("before") * 10, isPark = true)
        assertTrue(store.all().isEmpty())
        assertEquals(first.getInt("before") * 10, store.lastEndTenths)

        var t = 1_000_000L
        for (i in 0 until 5) { // 1~5번: 앱이 켜진 채 운행
            val r = rows.getJSONObject(i)
            t = engine.drive(t, r.getInt("before") * 10, r.getInt("after") * 10)
            val saved = store.all().last()
            assertEquals(r.getInt("dist") * 10, saved.distanceTenths)
            engine.reclassify(
                saved.id,
                when (r.getString("cls")) {
                    "출퇴근" -> Category.COMMUTE
                    "일반업무" -> Category.BUSINESS
                    "비업무" -> Category.PRIVATE
                    else -> Category.UNCLASSIFIED
                },
            )
        }
        // 6번: 앱 종료 중 12,444 → 12,476 주행, 앱 재실행
        val r6 = rows.getJSONObject(5)
        val engine2 = TripEngine(store) // 프로세스 재시작
        engine2.onServiceStart(t, r6.getInt("after") * 10, isPark = true)

        val all = store.all()
        assertEquals(6, all.size)
        assertContinuous(store)
        assertEquals(TripKind.GAP, all[5].kind)
        assertEquals(Category.UNCLASSIFIED, all[5].category)
        assertEquals(TripEngine.GAP_NOTE, all[5].note)
        assertEquals(r6.getInt("dist") * 10, all[5].distanceTenths)

        val before = summarize(all)
        assertEquals(expect.getInt("beforeTotal") * 10, before.totalTenths)
        assertEquals(expect.getInt("beforeBiz") * 10, before.businessUseTenths)
        assertEquals(expect.getInt("commute") * 10, before.commuteTenths)
        assertEquals(expect.getInt("gap") * 10, before.unclassifiedTenths)
        assertEquals(1, before.unclassifiedCount)
        assertEquals("82.1", before.ratioText())

        // 미분류 정리: 6번을 일반업무로
        engine2.reclassify(all[5].id, Category.BUSINESS)
        val after = summarize(store.all())
        assertEquals(expect.getInt("afterTotal") * 10, after.totalTenths)
        assertEquals(expect.getInt("afterBiz") * 10, after.businessUseTenths)
        assertEquals(0, after.unclassifiedCount)
        assertEquals("85.8", after.ratioText())

        // ⑤⑥⑦: 합 = 계기판 차이
        val lb = logbookRows(store.all())
        assertEquals(155, lb.sumOf { it.distanceKm })
        assertEquals(155, lb.last().afterKm - lb.first().beforeKm)
        assertEquals(123, 155 - 32) // 검증: 정리 전 총거리
    }

    @Test
    fun 첫_실행은_기준값만_저장하고_빈구간을_만들지_않는다() {
        val store = InMemoryTripStore()
        TripEngine(store).onServiceStart(0, km(12321.0), isPark = true)
        assertTrue(store.all().isEmpty())
        assertEquals(km(12321.0), store.lastEndTenths)
    }

    @Test
    fun 기어_P_뒤_늦게_온_주행거리를_주행후로_쓴다() {
        val store = InMemoryTripStore()
        val e = TripEngine(store)
        e.onServiceStart(0, km(0.0), true)
        e.onGearChanged(1_000, false, km(0.0))
        e.onOdometer(2_000, km(0.2))
        e.onGearChanged(3_000, true, km(0.2)) // 기어가 먼저 오고 읽은 값은 낡음
        assertNotNull("대기 중에는 아직 진행 중", store.inProgress())
        e.onOdometer(4_000, km(0.3)) // 1초 뒤 최신 값
        e.tick(4_499)
        assertNotNull("1.5초 전에는 끝나지 않음", store.inProgress())
        e.tick(4_500)
        assertNull(store.inProgress())
        assertEquals(km(0.3), store.lastDone()!!.endTenths)
        assertEquals(km(0.3), store.lastEndTenths)
    }

    @Test
    fun 대기_중_msUntilSettle이_남은_시간을_알려준다() {
        val e = TripEngine(InMemoryTripStore())
        e.onServiceStart(0, 0, true)
        e.onGearChanged(1_000, false, 0)
        assertNull(e.msUntilSettle(1_500))
        e.onGearChanged(5_000, true, 10)
        assertEquals(1_000L, e.msUntilSettle(5_500))
        assertEquals(0L, e.msUntilSettle(9_000))
    }

    @Test
    fun 대기_중_다시_출발하면_앞_운행을_끝내고_이어서_시작한다() {
        val store = InMemoryTripStore()
        val e = TripEngine(store)
        e.onServiceStart(0, 0, true)
        e.onGearChanged(1_000, false, 0)
        e.onGearChanged(5_000, true, km(1.0))
        e.onGearChanged(5_300, false, km(1.0)) // 0.3초 만에 다시 D
        val all = store.all()
        assertEquals(2, all.size)
        assertEquals(TripStatus.DONE, all[0].status)
        assertEquals(km(1.0), all[0].endTenths)
        assertEquals(km(1.0), all[1].startTenths)
        assertContinuous(store)
    }

    @Test
    fun 위_0점1km_미만_운행은_버린다() {
        val store = InMemoryTripStore()
        val e = TripEngine(store)
        e.onServiceStart(0, km(100.0), true)
        e.drive(1_000, km(100.0), km(100.0)) // 거리 0
        assertTrue(store.all().isEmpty())
        assertEquals(km(100.0), store.lastEndTenths)
        e.drive(20_000, km(100.0), km(100.1)) // 0.1km는 남긴다
        assertEquals(1, store.all().size)
        assertEquals(1, store.all()[0].distanceTenths)
    }

    @Test
    fun 서비스_시작_때_진행중_운행이_남아있으면_빈구간으로_바꾼다() {
        val store = InMemoryTripStore()
        val e = TripEngine(store)
        e.onServiceStart(0, km(500.0), true)
        e.onGearChanged(1_000, false, km(500.0)) // 출발 후 앱 사망
        TripEngine(store).onServiceStart(100_000, km(518.0), isPark = true)
        val all = store.all()
        assertEquals(1, all.size)
        assertEquals(TripKind.GAP, all[0].kind)
        assertEquals(TripStatus.DONE, all[0].status)
        assertEquals(km(18.0), all[0].distanceTenths)
        assertEquals(km(518.0), store.lastEndTenths)
    }

    @Test
    fun 서비스_시작_때_기어가_이미_P가_아니면_빈구간_뒤_새_운행을_시작한다() {
        val store = InMemoryTripStore()
        TripEngine(store).onServiceStart(0, km(500.0), true)
        TripEngine(store).onServiceStart(50_000, km(512.0), isPark = false)
        val all = store.all()
        assertEquals(2, all.size)
        assertEquals(TripKind.GAP, all[0].kind)
        assertEquals(km(12.0), all[0].distanceTenths)
        assertEquals(TripStatus.IN_PROGRESS, all[1].status)
        assertEquals(km(512.0), all[1].startTenths)
        assertContinuous(store)
    }

    @Test
    fun 빈구간이_0점1km_미만이면_만들지_않는다() {
        val store = InMemoryTripStore()
        TripEngine(store).onServiceStart(0, 5000, true)
        TripEngine(store).onServiceStart(1, 5000, true)
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun 운행_행의_5_6_7은_소수점을_버린_정수이고_합은_계기판_차이와_같다() {
        val store = InMemoryTripStore()
        val e = TripEngine(store)
        e.onServiceStart(0, km(12321.4), true)
        var t = 1_000L
        t = e.drive(t, km(12321.4), km(12340.3))
        t = e.drive(t, km(12340.3), km(12358.9))
        e.drive(t, km(12358.9), km(12403.0))
        val rows = logbookRows(store.all())
        assertEquals(listOf(12321, 12340, 12358), rows.map { it.beforeKm })
        assertEquals(listOf(12340, 12358, 12403), rows.map { it.afterKm })
        rows.forEach { assertEquals(it.afterKm - it.beforeKm, it.distanceKm) }
        assertEquals(12403 - 12321, rows.sumOf { it.distanceKm })
    }

    @Test
    fun 업무사용비율_총거리_0이면_null() {
        assertNull(summarize(emptyList()).ratioPermille)
    }

    @Test
    fun 무작위_사건_순서에서도_불변식이_지켜진다() {
        for (seed in 1..300) {
            val rnd = Random(seed.toLong())
            val store = InMemoryTripStore()
            var engine = TripEngine(store)
            var odo = rnd.nextInt(100_000)
            var now = 0L
            var isPark = true
            engine.onServiceStart(now, odo, true)
            repeat(80) {
                now += 200 + rnd.nextInt(3_000)
                when (rnd.nextInt(6)) {
                    0 -> { isPark = !isPark; engine.onGearChanged(now, isPark, odo) }
                    1 -> { odo += rnd.nextInt(30); engine.onOdometer(now, odo) }
                    2 -> engine.tick(now)
                    3 -> { // 앱 종료 중 차가 움직임 → 재시작
                        odo += rnd.nextInt(200)
                        isPark = rnd.nextBoolean()
                        engine = TripEngine(store)
                        engine.onServiceStart(now, odo, isPark)
                    }
                    else -> engine.tick(now)
                }
                assertContinuous(store)
            }
        }
    }
}
