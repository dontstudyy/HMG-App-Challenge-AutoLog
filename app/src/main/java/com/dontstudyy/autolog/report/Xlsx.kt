package com.dontstudyy.autolog.report

import java.io.ByteArrayOutputStream
import java.util.TreeMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 서식 표 한 장의 데이터 줄 수(국세청 서식은 63줄). 넘으면 시트를 나눈다. */
const val ROWS_PER_SHEET = 63

private const val FIRST_DATA_ROW = 10
private const val TOTAL_LABEL_ROW = FIRST_DATA_ROW + ROWS_PER_SHEET // 73
private const val TOTAL_VALUE_ROW = TOTAL_LABEL_ROW + 1 // 74

// 스타일 번호(styles.xml의 cellXfs 순서)
private const val S_DEFAULT = 0
private const val S_TITLE = 1
private const val S_HEADER = 2
private const val S_TEXT = 3
private const val S_NUM = 4
private const val S_PCT = 5
private const val S_LABEL = 6

/**
 * 외부 라이브러리 없이 쓰는 최소 xlsx(zip + XML). 열: A 사용일자, B 부서, C 성명, D ⑤, E ⑥, F ⑦, G ⑧, H ⑨, I ⑩.
 * 숫자는 숫자 셀로, ⑬은 85.8 같은 숫자에 `0.0"%"` 서식을 입힌다. 문자열은 inlineStr.
 */
fun buildXlsx(doc: LogbookDoc): ByteArray {
    val pages = doc.rows.chunked(ROWS_PER_SHEET).ifEmpty { listOf(emptyList()) }
    val totals = totalsOf(doc.rows)
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z ->
        fun put(name: String, body: String) {
            z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray(Charsets.UTF_8)); z.closeEntry()
        }
        put("[Content_Types].xml", contentTypes(pages.size))
        put("_rels/.rels", ROOT_RELS)
        put("xl/workbook.xml", workbook(pages.size))
        put("xl/_rels/workbook.xml.rels", workbookRels(pages.size))
        put("xl/styles.xml", STYLES)
        pages.forEachIndexed { i, rows -> put("xl/worksheets/sheet${i + 1}.xml", sheet(doc, rows, totals)) }
    }
    return out.toByteArray()
}

private class SheetBuilder {
    val rows = TreeMap<Int, TreeMap<Int, String>>()
    val merges = mutableListOf<String>()

    private fun colName(c: Int) = ('A' + c).toString()

    fun str(r: Int, c: Int, text: String, style: Int) {
        rows.getOrPut(r) { TreeMap() }[c] =
            """<c r="${colName(c)}$r" s="$style" t="inlineStr"><is><t xml:space="preserve">${esc(text)}</t></is></c>"""
    }

    fun num(r: Int, c: Int, value: String, style: Int) {
        rows.getOrPut(r) { TreeMap() }[c] = """<c r="${colName(c)}$r" s="$style"><v>$value</v></c>"""
    }

    fun blank(r: Int, c: Int, style: Int) {
        rows.getOrPut(r) { TreeMap() }.putIfAbsent(c, """<c r="${colName(c)}$r" s="$style"/>""")
    }

    /** 병합 범위의 나머지 칸에도 같은 스타일의 빈 셀을 채워 테두리가 이어지게 한다. */
    fun merge(r1: Int, c1: Int, r2: Int, c2: Int, style: Int) {
        merges += "${colName(c1)}$r1:${colName(c2)}$r2"
        for (r in r1..r2) for (c in c1..c2) blank(r, c, style)
    }

    fun xml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetFormatPr defaultRowHeight="20"/>""")
        append("<cols>")
        listOf(12, 10, 10, 16, 16, 12, 12, 12, 34).forEachIndexed { i, w ->
            append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""")
        }
        append("</cols><sheetData>")
        for ((r, cells) in rows) {
            append("""<row r="$r">""")
            cells.values.forEach { append(it) }
            append("</row>")
        }
        append("</sheetData>")
        append("""<mergeCells count="${merges.size}">""")
        merges.forEach { append("""<mergeCell ref="$it"/>""") }
        append("</mergeCells>")
        append("""<pageMargins left="0.5" right="0.5" top="0.6" bottom="0.6" header="0.3" footer="0.3"/>""")
        append("""<pageSetup paperSize="9" orientation="portrait" fitToHeight="0"/>""")
        append("</worksheet>")
    }
}

private fun sheet(doc: LogbookDoc, rows: List<LogRowData>, totals: LogbookTotals): String {
    val b = SheetBuilder()
    val s = doc.settings

    b.str(1, 0, "업무용승용차 운행기록부", S_TITLE); b.merge(1, 0, 1, 8, S_TITLE)
    b.str(2, 0, "과세기간", S_LABEL); b.merge(2, 0, 2, 2, S_LABEL)
    b.str(2, 3, "${doc.periodStart} ～ ${doc.periodEnd}", S_TEXT); b.merge(2, 3, 2, 8, S_TEXT)
    b.str(3, 0, "상호명", S_LABEL); b.merge(3, 0, 3, 2, S_LABEL)
    b.str(3, 3, s.company, S_TEXT); b.merge(3, 3, 3, 8, S_TEXT)
    b.str(4, 0, "사업자등록번호", S_LABEL); b.merge(4, 0, 4, 2, S_LABEL)
    b.str(4, 3, s.bizNo, S_TEXT); b.merge(4, 3, 4, 8, S_TEXT)

    b.str(5, 0, "1. 기본정보", S_DEFAULT)
    b.str(6, 0, "①차 종", S_LABEL)
    b.str(6, 1, s.vehicleType, S_TEXT); b.merge(6, 1, 6, 2, S_TEXT)
    b.str(6, 3, "②자동차등록번호", S_LABEL)
    b.str(6, 4, s.plate, S_TEXT); b.merge(6, 4, 6, 8, S_TEXT)

    b.str(7, 0, "2. 업무용 사용비율 계산", S_DEFAULT)
    // 머리글 두 줄(8~9행)
    b.str(8, 0, "③사용일자(요일)", S_HEADER); b.merge(8, 0, 9, 0, S_HEADER)
    b.str(8, 1, "④사용자", S_HEADER); b.merge(8, 1, 8, 2, S_HEADER)
    b.str(9, 1, "부서", S_HEADER); b.str(9, 2, "성명", S_HEADER)
    b.str(8, 3, "운행 내역", S_HEADER); b.merge(8, 3, 8, 5, S_HEADER)
    b.str(9, 3, "⑤주행 전 계기판의 거리(㎞)", S_HEADER)
    b.str(9, 4, "⑥주행 후 계기판의 거리(㎞)", S_HEADER)
    b.str(9, 5, "⑦주행거리(㎞)", S_HEADER)
    b.str(8, 6, "업무용 사용거리(㎞)", S_HEADER); b.merge(8, 6, 8, 7, S_HEADER)
    b.str(9, 6, "⑧출․퇴근용(㎞)", S_HEADER)
    b.str(9, 7, "⑨일반 업무용(㎞)", S_HEADER)
    b.str(8, 8, "⑩비 고", S_HEADER); b.merge(8, 8, 9, 8, S_HEADER)

    // 데이터 63줄(남는 줄은 테두리만)
    for (i in 0 until ROWS_PER_SHEET) {
        val r = FIRST_DATA_ROW + i
        val d = rows.getOrNull(i)
        if (d == null) { for (c in 0..8) b.blank(r, c, S_TEXT); continue }
        b.str(r, 0, d.date, S_TEXT)
        b.str(r, 1, d.dept, S_TEXT)
        b.str(r, 2, d.name, S_TEXT)
        b.num(r, 3, d.beforeKm.toString(), S_NUM)
        b.num(r, 4, d.afterKm.toString(), S_NUM)
        b.num(r, 5, d.distanceKm.toString(), S_NUM)
        if (d.commuteKm != null) b.num(r, 6, d.commuteKm.toString(), S_NUM) else b.blank(r, 6, S_NUM)
        if (d.businessKm != null) b.num(r, 7, d.businessKm.toString(), S_NUM) else b.blank(r, 7, S_NUM)
        b.str(r, 8, d.remark, S_TEXT)
    }

    // ⑪⑫⑬ (과세기간 전체 합계)
    b.str(TOTAL_LABEL_ROW, 0, "⑪과세기간 총주행 거리(㎞)", S_LABEL); b.merge(TOTAL_LABEL_ROW, 0, TOTAL_LABEL_ROW, 2, S_LABEL)
    b.str(TOTAL_LABEL_ROW, 3, "⑫과세기간 업무용 사용거리(㎞)", S_LABEL); b.merge(TOTAL_LABEL_ROW, 3, TOTAL_LABEL_ROW, 5, S_LABEL)
    b.str(TOTAL_LABEL_ROW, 6, "⑬업무사용비율(⑫/⑪)", S_LABEL); b.merge(TOTAL_LABEL_ROW, 6, TOTAL_LABEL_ROW, 8, S_LABEL)
    b.num(TOTAL_VALUE_ROW, 0, totals.totalKm.toString(), S_NUM); b.merge(TOTAL_VALUE_ROW, 0, TOTAL_VALUE_ROW, 2, S_NUM)
    b.num(TOTAL_VALUE_ROW, 3, totals.businessKm.toString(), S_NUM); b.merge(TOTAL_VALUE_ROW, 3, TOTAL_VALUE_ROW, 5, S_NUM)
    val ratio = totals.ratioText()
    if (ratio != null) b.num(TOTAL_VALUE_ROW, 6, ratio, S_PCT) else b.str(TOTAL_VALUE_ROW, 6, "-", S_TEXT)
    b.merge(TOTAL_VALUE_ROW, 6, TOTAL_VALUE_ROW, 8, if (ratio != null) S_PCT else S_TEXT)
    return b.xml()
}

private fun esc(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private const val XML_HEAD = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""

private fun contentTypes(sheets: Int) = buildString {
    append(XML_HEAD)
    append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
    append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
    append("""<Default Extension="xml" ContentType="application/xml"/>""")
    append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
    append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
    for (i in 1..sheets) {
        append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
    }
    append("</Types>")
}

private const val ROOT_RELS = XML_HEAD +
    """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
    """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
    "</Relationships>"

private fun workbook(sheets: Int) = buildString {
    append(XML_HEAD)
    append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
    for (i in 1..sheets) {
        val name = if (i == 1) "운행기록부" else "운행기록부 $i"
        append("""<sheet name="$name" sheetId="$i" r:id="rId$i"/>""")
    }
    append("</sheets></workbook>")
}

private fun workbookRels(sheets: Int) = buildString {
    append(XML_HEAD)
    append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
    for (i in 1..sheets) {
        append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
    }
    append("""<Relationship Id="rId${sheets + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
    append("</Relationships>")
}

private const val STYLES = XML_HEAD +
    """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
    """<numFmts count="1"><numFmt numFmtId="164" formatCode="0.0&quot;%&quot;"/></numFmts>""" +
    """<fonts count="3"><font><sz val="11"/><name val="맑은 고딕"/></font>""" +
    """<font><b/><sz val="11"/><name val="맑은 고딕"/></font>""" +
    """<font><b/><sz val="16"/><name val="맑은 고딕"/></font></fonts>""" +
    """<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>""" +
    """<fill><patternFill patternType="solid"><fgColor rgb="FFE8ECF5"/><bgColor indexed="64"/></patternFill></fill></fills>""" +
    """<borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border>""" +
    """<border><left style="thin"><color auto="1"/></left><right style="thin"><color auto="1"/></right>""" +
    """<top style="thin"><color auto="1"/></top><bottom style="thin"><color auto="1"/></bottom><diagonal/></border></borders>""" +
    """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
    """<cellXfs count="7">""" +
    """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
    """<xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>""" +
    """<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>""" +
    """<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>""" +
    """<xf numFmtId="3" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>""" +
    """<xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>""" +
    """<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>""" +
    "</cellXfs></styleSheet>"
