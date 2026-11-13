package com.dontstudyy.autolog.report

import com.dontstudyy.autolog.trip.Category
import com.dontstudyy.autolog.trip.Trip
import com.dontstudyy.autolog.trip.TripStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 국세청 「업무용승용차 운행기록부」 서식(`.private/운행기록부_서식_칸구성.md`) 한 건의 내용.
 * ⑤⑥은 소수점을 버린 정수 km, ⑦ = ⑥ − ⑤, ⑧⑨는 업무용(출퇴근·일반업무)만 채운다.
 */
data class LogRowData(
    val date: String,
    val dept: String,
    val name: String,
    val beforeKm: Int,
    val afterKm: Int,
    val distanceKm: Int,
    val commuteKm: Int?,
    val businessKm: Int?,
    val remark: String,
)

data class LogbookDoc(
    val periodStart: String,
    val periodEnd: String,
    val settings: AppSettings,
    val rows: List<LogRowData>,
)

/** ⑪과세기간 총주행거리, ⑫과세기간 업무용 사용거리, ⑬업무사용비율(⑫/⑪). */
data class LogbookTotals(val totalKm: Int, val businessKm: Int) {
    /** 0.1% 단위 정수(858 = 85.8%). 총주행거리가 0이면 null. */
    val ratioPermille: Int?
        get() = if (totalKm == 0) null else ((businessKm.toLong() * 2000 + totalKm) / (2L * totalKm)).toInt()

    fun ratioText(): String? = ratioPermille?.let { "${it / 10}.${it % 10}" }
}

/** 표의 줄들에서 합계를 낸다. ⑪은 ⑦의 합(= 계기판 차이), ⑫는 ⑧ + ⑨의 합. */
fun totalsOf(rows: List<LogRowData>): LogbookTotals =
    LogbookTotals(rows.sumOf { it.distanceKm }, rows.sumOf { (it.commuteKm ?: 0) + (it.businessKm ?: 0) })

private val periodFmt get() = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
private val dayFmt get() = SimpleDateFormat("M.d(E)", Locale.KOREA)

fun weekdayDate(ms: Long): String = dayFmt.format(Date(ms))

/**
 * 완료된 운행을 서식 줄로 바꾼다. [profiles]는 사용자 id → (부서, 성명).
 * 미분류 운행도 줄과 ⑪에는 들어가지만 ⑧⑨는 비운다(정리 전에는 업무용으로 세지 않음).
 */
fun buildLogbookDoc(
    trips: List<Trip>,
    profiles: Map<Long, Pair<String, String>>,
    settings: AppSettings,
    periodStartMs: Long,
    periodEndMs: Long,
): LogbookDoc {
    val rows = logbookRows(trips.filter { it.status == TripStatus.DONE }).map { r ->
        val t = r.trip
        val (dept, name) = profiles[t.userId] ?: ("" to "운전자")
        val prefix = when (t.category) {
            Category.PRIVATE -> "비업무"
            Category.UNCLASSIFIED -> "미분류"
            else -> ""
        }
        LogRowData(
            date = weekdayDate(t.startTimeMs), dept = dept, name = name,
            beforeKm = r.beforeKm, afterKm = r.afterKm, distanceKm = r.distanceKm,
            commuteKm = r.distanceKm.takeIf { t.category == Category.COMMUTE },
            businessKm = r.distanceKm.takeIf { t.category == Category.BUSINESS },
            remark = listOf(prefix, t.note).filter { it.isNotBlank() }.joinToString(" · "),
        )
    }
    return LogbookDoc(periodFmt.format(Date(periodStartMs)), periodFmt.format(Date(periodEndMs)), settings, rows)
}
