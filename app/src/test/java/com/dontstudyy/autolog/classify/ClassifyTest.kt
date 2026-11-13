package com.dontstudyy.autolog.classify

import com.dontstudyy.autolog.location.Fix
import com.dontstudyy.autolog.location.isLost
import com.dontstudyy.autolog.trip.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifyTest {
    private val home = Place(1, "집", PlaceKind.HOME, 37.5500, 126.9800)
    private val company = Place(2, "회사", PlaceKind.COMPANY, 37.5700, 127.0100)
    private val client = Place(3, "거래처", PlaceKind.CLIENT, 37.5300, 127.0500)
    private val places = listOf(home, company, client)

    // ---- 거리·장소 판정 ----

    @Test
    fun 거리_계산은_위도_0점001도가_약_111m() {
        val d = distanceMeters(37.5500, 126.98, 37.5510, 126.98)
        assertEquals(111.2, d, 1.0)
    }

    @Test
    fun 반경_300m_경계_밖은_null_안은_장소() {
        // Step 0 ⑤ 실험 좌표: 약 344m 밖, 약 245m 안
        assertNull(findPlace(37.5531, 126.98, places))
        assertEquals(home, findPlace(37.5522, 126.98, places))
    }

    @Test
    fun 반경_안에_둘_이상이면_가장_가까운_장소() {
        val near = Place(4, "근처", PlaceKind.OTHER, 37.5502, 126.98)
        assertEquals(near, findPlace(37.5503, 126.98, listOf(home, near)))
    }

    @Test
    fun 장소마다_반경이_다를_수_있다() {
        val wide = home.copy(radiusM = 1000f)
        assertEquals(wide, findPlace(37.5531, 126.98, listOf(wide)))
    }

    // ---- 자동 분류 규칙 (설계서 7장) ----

    @Test
    fun 집에서_회사는_출근() {
        assertEquals(Verdict.Auto(Category.COMMUTE, "출근"), classify(home, company, null, places))
    }

    @Test
    fun 회사에서_집은_퇴근() {
        assertEquals(Verdict.Auto(Category.COMMUTE, "퇴근"), classify(company, home, null, places))
    }

    @Test
    fun 도착이_거래처면_일반업무_비고에_장소_이름() {
        assertEquals(Verdict.Auto(Category.BUSINESS, "거래처"), classify(company, client, null, places))
    }

    @Test
    fun 출발이_거래처여도_일반업무() {
        assertEquals(Verdict.Auto(Category.BUSINESS, "거래처"), classify(client, null, null, places))
    }

    @Test
    fun 도착과_출발이_모두_거래처면_도착_이름() {
        val other = Place(5, "고객사", PlaceKind.CLIENT, 37.0, 127.0)
        assertEquals(Verdict.Auto(Category.BUSINESS, "고객사"), classify(client, other, null, places + other))
    }

    @Test
    fun 길안내_목적지_이름이_등록_장소와_같으면_그_장소로_본다() {
        assertEquals(Verdict.Auto(Category.BUSINESS, "거래처"), classify(company, null, "거래처", places))
        assertEquals(Verdict.Auto(Category.COMMUTE, "출근"), classify(home, null, "회사", places))
    }

    @Test
    fun 실제_도착_장소가_있으면_길안내_이름보다_우선() {
        assertEquals(Verdict.Auto(Category.COMMUTE, "퇴근"), classify(company, home, "거래처", places))
    }

    @Test
    fun 등록되지_않은_길안내_이름은_무시() {
        assertEquals(Verdict.Ask, classify(home, null, "모르는곳", places))
    }

    @Test
    fun 미등록_위치는_질문() {
        assertEquals(Verdict.Ask, classify(home, null, null, places))
        assertEquals(Verdict.Ask, classify(null, null, null, places))
    }

    @Test
    fun 집에서_집_또는_회사에서_회사는_질문() {
        assertEquals(Verdict.Ask, classify(home, home, null, places))
        assertEquals(Verdict.Ask, classify(company, company, null, places))
    }

    @Test
    fun 회사만_관련되면_질문() {
        assertEquals(Verdict.Ask, classify(company, null, null, places))
    }

    // ---- 위치 끊김 ----

    @Test
    fun 위치_수신이_없으면_끊김() {
        assertTrue(isLost(null, 1_000))
    }

    @Test
    fun 마지막_수신이_10초_넘게_지나야_끊김() {
        val fix = Fix(37.5, 127.0, 100_000)
        assertFalse(isLost(fix, 100_000 + 10_000))
        assertTrue(isLost(fix, 100_000 + 10_001))
    }
}
