package com.dontstudyy.autolog.report

import com.dontstudyy.autolog.trip.Category
import com.dontstudyy.autolog.trip.Trip
import com.dontstudyy.autolog.trip.TripKind
import com.dontstudyy.autolog.trip.TripStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class CostAndExportTest {
    private fun fixtureTrips(fixed: Boolean): List<Trip> {
        val json = JSONObject(javaClass.classLoader!!.getResource("trips.json")!!.readText())
        val rows = json.getJSONArray("trips")
        return (0 until rows.length()).map { i ->
            val r = rows.getJSONObject(i)
            val cls = if (fixed && r.has("fixCls")) r.getString("fixCls") else r.getString("cls")
            Trip(
                id = i + 1L, startTimeMs = 1_764_547_200_000L + i * 3_600_000L, endTimeMs = 1_764_547_200_000L + i * 3_600_000L + 1,
                startTenths = r.getInt("before") * 10, endTenths = r.getInt("after") * 10, userId = if (r.getString("user").contains("박")) 2 else 1,
                category = when (cls) { "출퇴근" -> Category.COMMUTE; "일반업무" -> Category.BUSINESS; "비업무" -> Category.PRIVATE; else -> Category.UNCLASSIFIED },
                note = r.getString("note"), kind = if (i == 5) TripKind.GAP else TripKind.NORMAL, status = TripStatus.DONE,
                tollWon = if (i == 1) 2600 else 0,
            )
        }
    }

    private val settings = AppSettings(
        vehicleType = "업무용 승용차", plate = "12가 3456", company = "샘플상사", bizNo = "123-45-67890",
        litersPer100km = 7.1, wonPerLiter = 1650,
    )

    // ---- 유류비·통행료 ----

    @Test
    fun 예상_유류비는_연비_거리_단가_100원_단위_반올림() {
        // trips.json: 155km, 7.1L/100km, 1,650원 → 18,160 → 화면 값 18,200
        assertEquals(18_200, estimateFuelWon(1550, settings.litersPer100km, settings.wonPerLiter))
        assertEquals(0, estimateFuelWon(0, 7.1, 1650))
    }

    @Test
    fun 예상_통행료는_완료된_운행의_합() {
        assertEquals(2_600, summarize(fixtureTrips(true)).tollWon)
        val running = Trip(id = 9, startTimeMs = 1, startTenths = 0, tollWon = 5_000) // 진행 중은 제외
        assertEquals(2_600, summarize(fixtureTrips(true) + running).tollWon)
    }

    // ---- 운행기록부 합계 (⑪⑫⑬) ----

    @Test
    fun 정리_후_합계는_155_133_85_8() {
        val t = totalsOf(buildDoc(fixtureTrips(true)).rows)
        assertEquals(155, t.totalKm)
        assertEquals(133, t.businessKm)
        assertEquals("85.8", t.ratioText())
    }

    @Test
    fun 미분류가_남은_채_내보내면_업무사용비율은_65_2() {
        // 미분류(32km)도 ⑪ 총주행거리(= 계기판 차이)에는 들어간다. 그래서 정리 후와 비율이 다르다.
        val t = totalsOf(buildDoc(fixtureTrips(false)).rows)
        assertEquals(155, t.totalKm)
        assertEquals(101, t.businessKm)
        assertEquals("65.2", t.ratioText())
    }

    @Test
    fun 행_구성은_5_6_7_8_9_칸_규칙을_따른다() {
        val rows = buildDoc(fixtureTrips(true)).rows
        assertEquals(6, rows.size)
        val r1 = rows[0]
        assertEquals(12321, r1.beforeKm); assertEquals(12340, r1.afterKm); assertEquals(19, r1.distanceKm)
        assertEquals(19, r1.commuteKm); assertNull(r1.businessKm)
        val r2 = rows[1]
        assertNull(r2.commuteKm); assertEquals(18, r2.businessKm)
        val r5 = rows[4] // 비업무: ⑧⑨ 모두 비움
        assertNull(r5.commuteKm); assertNull(r5.businessKm)
        assertEquals("영업팀 박과장".substringAfter(' '), rows[2].name) // userId 2 → 박과장
    }

    // ---- xlsx ----

    @Test
    fun xlsx는_서식_칸_이름과_합계_값을_담는다() {
        val bytes = buildXlsx(buildDoc(fixtureTrips(true)))
        val parts = unzip(bytes)
        assertTrue(parts.keys.containsAll(listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml")))
        val sheet = parts.getValue("xl/worksheets/sheet1.xml")
        val text = cellTexts(sheet)
        assertEquals("업무용승용차 운행기록부", text["A1"])
        assertEquals("샘플상사", text["D3"])
        assertEquals("123-45-67890", text["D4"])
        assertEquals("업무용 승용차", text["B6"])
        assertEquals("12가 3456", text["E6"])
        // 표 머리글
        assertTrue(text.values.any { it.startsWith("③사용일자") })
        assertTrue(text.values.any { it.startsWith("⑤주행 전 계기판의 거리") })
        assertTrue(text.values.any { it.startsWith("⑥주행 후 계기판의 거리") })
        assertTrue(text.values.any { it.startsWith("⑩비") })
        // 데이터 첫 줄(10행): ⑤⑥⑦⑧
        assertEquals("12321", text["D10"]); assertEquals("12340", text["E10"]); assertEquals("19", text["F10"]); assertEquals("19", text["G10"])
        // ⑪⑫⑬ (표 63줄 아래 74행)
        assertTrue(text["A73"]!!.startsWith("⑪")); assertTrue(text["D73"]!!.startsWith("⑫")); assertTrue(text["G73"]!!.startsWith("⑬"))
        assertEquals("155", text["A74"]); assertEquals("133", text["D74"]); assertEquals("85.8", text["G74"])
    }

    @Test
    fun 줄이_63건을_넘으면_시트를_나눈다() {
        val many = (1..70).map { i ->
            Trip(id = i.toLong(), startTimeMs = 1_764_547_200_000L + i, endTimeMs = 1_764_547_200_001L + i, startTenths = i * 100, endTenths = i * 100 + 50,
                category = Category.BUSINESS, status = TripStatus.DONE)
        }
        val parts = unzip(buildXlsx(buildDoc(many)))
        assertTrue(parts.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(parts.containsKey("xl/worksheets/sheet2.xml"))
        val second = cellTexts(parts.getValue("xl/worksheets/sheet2.xml"))
        assertEquals("350", second["D74"]) // 합계 칸은 모든 시트에 같이 들어간다
        assertEquals("640", second["D10"]) // 2번째 시트 첫 줄 = 64번째 운행(⑤ 640km)
        assertEquals("350", cellTexts(parts.getValue("xl/worksheets/sheet1.xml"))["A74"])
    }

    @Test
    fun XML_특수문자는_이스케이프된다() {
        val trip = Trip(id = 1, startTimeMs = 1_764_547_200_000L, endTimeMs = 1_764_547_200_001L, startTenths = 0, endTenths = 100,
            category = Category.BUSINESS, status = TripStatus.DONE, note = "A&B <납품>")
        val sheet = unzip(buildXlsx(buildDoc(listOf(trip)))).getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("A&amp;B &lt;납품&gt;"))
    }

    private fun buildDoc(trips: List<Trip>) = buildLogbookDoc(
        trips, profiles = mapOf(1L to ("영업팀" to "김대리"), 2L to ("영업팀" to "박과장")), settings = settings,
        periodStartMs = 1_764_547_200_000L, periodEndMs = 1_767_139_199_000L,
    )

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val out = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) { out[e.name] = z.readBytes().toString(Charsets.UTF_8); e = z.nextEntry }
        }
        return out
    }

    /** 셀 주소 → 값(숫자 또는 인라인 문자열). */
    private fun cellTexts(sheetXml: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        Regex("""<c r="([A-Z]+\d+)"[^>]*?(?:/>|>(.*?)</c>)""", RegexOption.DOT_MATCHES_ALL).findAll(sheetXml).forEach { m ->
            val body = m.groupValues[2]
            val v = Regex("<v>(.*?)</v>").find(body)?.groupValues?.get(1)
                ?: Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)
            if (v != null) map[m.groupValues[1]] = v.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        }
        return map
    }
}
