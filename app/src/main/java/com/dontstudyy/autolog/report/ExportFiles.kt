package com.dontstudyy.autolog.report

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** [xlsxPath]·[pdfPath]는 화면에 보여 줄 경로. 공용 다운로드 폴더에 저장되면 그 경로, 아니면 앱 전용 폴더 경로. */
data class ExportResult(val doc: LogbookDoc, val xlsx: File, val pdf: File, val xlsxPath: String, val pdfPath: String)

/**
 * 엑셀과 PDF를 앱 전용 폴더(getExternalFilesDir 아래 exports)에 저장하고, 공용 다운로드 폴더(Download/AutoLog)에도 복사한다.
 * 앱 전용 폴더는 adb로 읽을 수 없어서(스코프드 스토리지) 다운로드 폴더를 둔다. adb pull로 꺼낼 수 있다.
 */
fun exportLogbook(ctx: Context, doc: LogbookDoc, monthMs: Long): ExportResult {
    val dir = File(ctx.getExternalFilesDir(null), "exports").apply { mkdirs() }
    val tag = SimpleDateFormat("yyyy-MM", Locale.KOREA).format(Date(monthMs))
    val xlsxBytes = buildXlsx(doc)
    val pdfBytes = buildPdf(doc)
    val xlsx = File(dir, "logbook_$tag.xlsx").also { it.writeBytes(xlsxBytes) }
    val pdf = File(dir, "logbook_$tag.pdf").also { it.writeBytes(pdfBytes) }
    val xp = saveToDownloads(ctx, "logbook_$tag.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxBytes)
    val pp = saveToDownloads(ctx, "logbook_$tag.pdf", "application/pdf", pdfBytes)
    return ExportResult(doc, xlsx, pdf, xp ?: xlsx.absolutePath, pp ?: pdf.absolutePath)
}

/** MediaStore로 Download/AutoLog에 저장한다. 같은 이름이 이미 있으면 지우고 새로 쓴다. 실패하면 null. */
private fun saveToDownloads(ctx: Context, name: String, mime: String, bytes: ByteArray): String? = try {
    val resolver = ctx.contentResolver
    val rel = "Download/AutoLog/"
    resolver.delete(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?", arrayOf(name, rel),
    )
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    if (uri == null) null else {
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        rel + name
    }
} catch (t: Throwable) {
    Log.w("AutoLogExport", "다운로드 폴더 저장 실패: $t")
    null
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
