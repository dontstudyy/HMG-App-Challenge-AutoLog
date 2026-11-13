package com.dontstudyy.autolog.report

import com.dontstudyy.autolog.trip.Category
import com.dontstudyy.autolog.trip.Trip
import com.dontstudyy.autolog.trip.TripStatus

/**
 * 월간 집계. 거리는 0.1km 정수. 미분류는 정리 전까지 총 주행거리에서 빼고 따로 센다(설계서 2장 B7).
 */
data class MonthlySummary(
    val commuteTenths: Int,
    val businessTenths: Int,
    val privateTenths: Int,
    val unclassifiedTenths: Int,
    val unclassifiedCount: Int,
) {
    /** 총 주행거리 = 출퇴근 + 일반업무 + 비업무 (미분류 제외). */
    val totalTenths: Int get() = commuteTenths + businessTenths + privateTenths
    val businessUseTenths: Int get() = commuteTenths + businessTenths

    /** 업무사용비율을 0.1% 단위 정수로(821 = 82.1%). 총 주행거리가 0이면 null. */
    val ratioPermille: Int?
        get() = if (totalTenths == 0) null
        else ((businessUseTenths.toLong() * 2000 + totalTenths) / (2L * totalTenths)).toInt()

    fun ratioText(): String? = ratioPermille?.let { "${it / 10}.${it % 10}" }
}

fun summarize(trips: List<Trip>): MonthlySummary {
    var commute = 0
    var business = 0
    var private = 0
    var unc = 0
    var uncCount = 0
    for (t in trips) {
        if (t.status != TripStatus.DONE) continue
        val d = t.distanceTenths
        when (t.category) {
            Category.COMMUTE -> commute += d
            Category.BUSINESS -> business += d
            Category.PRIVATE -> private += d
            Category.UNCLASSIFIED -> { unc += d; uncCount++ }
        }
    }
    return MonthlySummary(commute, business, private, unc, uncCount)
}

/** 운행기록부 한 줄. ⑤⑥은 소수점을 버린 정수 km, ⑦ = ⑥ − ⑤ (설계서 6장). */
data class LogbookRow(val beforeKm: Int, val afterKm: Int, val distanceKm: Int, val trip: Trip)

fun logbookRows(trips: List<Trip>): List<LogbookRow> =
    trips.filter { it.status == TripStatus.DONE }.map {
        val b = it.startTenths / 10
        val a = (it.endTenths ?: it.startTenths) / 10
        LogbookRow(b, a, a - b, it)
    }
