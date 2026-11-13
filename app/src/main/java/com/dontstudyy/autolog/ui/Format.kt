package com.dontstudyy.autolog.ui

import androidx.compose.ui.graphics.Color
import com.dontstudyy.autolog.trip.Category
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Palette {
    val bg = Color(0xFF0B1020)
    val card = Color(0xFF121A33)
    val cardLine = Color(0xFF243056)
    val text = Color(0xFFEAF0FF)
    val dim = Color(0xFF8A96B8)
    val accent = Color(0xFF6C8CFF)
    val accentSoft = Color(0xFF1B2850)
    val amber = Color(0xFFE5B143)
    val amberSoft = Color(0xFF2E2612)
    val green = Color(0xFF3DDC97)
    val greenSoft = Color(0xFF12301F)
}

/** 12340 → "12,340" */
fun commas(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

/** 0.1km 정수 → "12,340.5" */
fun kmText(tenths: Int): String = "${commas(tenths / 10)}.${tenths % 10}"

/** 0.1km 정수 → 소수 한 자리 km 숫자만 "18.3" */
fun kmShort(tenths: Int): String = "${tenths / 10}.${tenths % 10}"

private val dateFmt = SimpleDateFormat("M.d(E)", Locale.KOREA)
fun dateText(ms: Long): String = dateFmt.format(Date(ms))

fun categoryLabel(c: Category): String = when (c) {
    Category.COMMUTE -> "출퇴근"
    Category.BUSINESS -> "일반업무"
    Category.PRIVATE -> "개인 용무"
    Category.UNCLASSIFIED -> "미분류"
}
