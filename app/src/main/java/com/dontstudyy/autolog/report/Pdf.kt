package com.dontstudyy.autolog.report

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

private const val PAGE_W = 595
private const val PAGE_H = 842
private const val MARGIN = 36f
private const val ROW_H = 20f
private const val ROWS_PER_PAGE = 30

/** 같은 서식 칸 구성을 PDF로 그린다. 글꼴은 기기 기본 글꼴(한글 지원)을 쓴다. */
fun buildPdf(doc: LogbookDoc): ByteArray {
    val totals = totalsOf(doc.rows)
    val pages = doc.rows.chunked(ROWS_PER_PAGE).ifEmpty { listOf(emptyList()) }
    val pdf = PdfDocument()

    val text = Paint().apply { color = Color.BLACK; textSize = 8.5f; isAntiAlias = true }
    val bold = Paint(text).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val title = Paint(bold).apply { textSize = 16f }
    val line = Paint().apply { color = Color.DKGRAY; strokeWidth = 0.6f; style = Paint.Style.STROKE }
    val head = Paint().apply { color = Color.rgb(232, 236, 245); style = Paint.Style.FILL }

    // 열 너비: 사용일자, 부서, 성명, ⑤, ⑥, ⑦, ⑧, ⑨, ⑩
    val widths = floatArrayOf(52f, 58f, 46f, 52f, 52f, 40f, 40f, 40f, 0f)
    widths[8] = PAGE_W - 2 * MARGIN - widths.sum()
    val xs = FloatArray(widths.size + 1).also { for (i in widths.indices) it[i + 1] = it[i] + widths[i] }

    pages.forEachIndexed { pageNo, rows ->
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo + 1).create())
        val c = page.canvas
        var y = MARGIN + 8f
        c.drawText("업무용승용차 운행기록부", PAGE_W / 2f - 80f, y, title); y += 24f
        c.drawText("과세기간  ${doc.periodStart} ～ ${doc.periodEnd}", MARGIN, y, text); y += 14f
        c.drawText("상호명  ${doc.settings.company}", MARGIN, y, text)
        c.drawText("사업자등록번호  ${doc.settings.bizNo}", MARGIN + 260f, y, text); y += 14f
        c.drawText("①차 종  ${doc.settings.vehicleType}", MARGIN, y, text)
        c.drawText("②자동차등록번호  ${doc.settings.plate}", MARGIN + 260f, y, text); y += 20f

        val headers = listOf("③사용일자", "부서", "성명", "⑤주행 전(㎞)", "⑥주행 후(㎞)", "⑦주행(㎞)", "⑧출퇴근", "⑨일반업무", "⑩비 고")
        val top0 = y
        c.drawRect(MARGIN, y, PAGE_W - MARGIN, y + ROW_H, head)
        headers.forEachIndexed { i, h -> c.drawText(h, MARGIN + xs[i] + 2f, y + 13f, bold) }
        y += ROW_H
        rows.forEach { r ->
            val cells = listOf(
                r.date, r.dept, r.name, r.beforeKm.toString(), r.afterKm.toString(), r.distanceKm.toString(),
                r.commuteKm?.toString() ?: "", r.businessKm?.toString() ?: "", r.remark,
            )
            cells.forEachIndexed { i, v ->
                val shown = if (i == 8) fit(v, widths[i] - 4f, text) else v
                c.drawText(shown, MARGIN + xs[i] + 2f, y + 13f, text)
            }
            y += ROW_H
        }
        // 표 테두리(머리글 + 이 쪽의 줄 수)
        val bottom = y
        c.drawRect(MARGIN, top0, PAGE_W - MARGIN, bottom, line)
        xs.forEach { x -> c.drawLine(MARGIN + x, top0, MARGIN + x, bottom, line) }
        var ly = top0
        while (ly <= bottom) { c.drawLine(MARGIN, ly, PAGE_W - MARGIN, ly, line); ly += ROW_H }

        if (pageNo == pages.lastIndex) {
            y += 22f
            c.drawText("⑪과세기간 총주행 거리(㎞): ${totals.totalKm}", MARGIN, y, bold); y += 15f
            c.drawText("⑫과세기간 업무용 사용거리(㎞): ${totals.businessKm}", MARGIN, y, bold); y += 15f
            c.drawText("⑬업무사용비율(⑫/⑪): ${totals.ratioText()?.let { "$it%" } ?: "-"}", MARGIN, y, bold)
        }
        c.drawText("${pageNo + 1} / ${pages.size}", PAGE_W / 2f - 10f, PAGE_H - 20f, text)
        pdf.finishPage(page)
    }
    val out = ByteArrayOutputStream()
    pdf.writeTo(out)
    pdf.close()
    return out.toByteArray()
}

/** 칸 너비에 맞게 글자를 줄인다. */
private fun fit(s: String, width: Float, p: Paint): String {
    if (p.measureText(s) <= width) return s
    var n = s.length
    while (n > 0 && p.measureText(s, 0, n) > width - p.measureText("…")) n--
    return s.substring(0, n) + "…"
}
