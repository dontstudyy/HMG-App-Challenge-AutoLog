package com.dontstudyy.autolog.report

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ExportResult(val doc: LogbookDoc, val xlsx: File, val pdf: File)

/** 앱 전용 폴더(getExternalFilesDir 아래 exports)에 엑셀과 PDF를 저장한다. adb pull로 꺼낼 수 있다. */
fun exportLogbook(ctx: Context, doc: LogbookDoc, monthMs: Long): ExportResult {
    val dir = File(ctx.getExternalFilesDir(null), "exports").apply { mkdirs() }
    val tag = SimpleDateFormat("yyyy-MM", Locale.KOREA).format(Date(monthMs))
    val xlsx = File(dir, "logbook_$tag.xlsx").also { it.writeBytes(buildXlsx(doc)) }
    val pdf = File(dir, "logbook_$tag.pdf").also { it.writeBytes(buildPdf(doc)) }
    return ExportResult(doc, xlsx, pdf)
}

/** nowMs가 속한 달의 첫 순간과 마지막 순간(ms). */
fun monthBounds(nowMs: Long): Pair<Long, Long> {
    val c = Calendar.getInstance().apply { timeInMillis = nowMs }
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    val start = c.timeInMillis
    c.add(Calendar.MONTH, 1)
    return start to c.timeInMillis - 1
}

/** 이번 달에 시작한 운행만 고른다. */
fun tripsOfMonth(trips: List<com.dontstudyy.autolog.trip.Trip>, nowMs: Long): List<com.dontstudyy.autolog.trip.Trip> {
    val (s, e) = monthBounds(nowMs)
    return trips.filter { it.startTimeMs in s..e }
}
