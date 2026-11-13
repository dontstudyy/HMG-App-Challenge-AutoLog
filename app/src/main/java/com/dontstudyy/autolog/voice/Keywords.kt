package com.dontstudyy.autolog.voice

import com.dontstudyy.autolog.trip.Category

/** 음성·Gleo로 받은 목적 해석 결과. [note]는 비고에 적을 문구. */
data class Answer(val category: Category, val note: String)

/**
 * 말한 문장에서 키워드로 목적을 고른다(설계서 7장). 먼저 맞는 것:
 * 1. '개인' → 비업무 (개인과 업무가 같이 나오면 개인 우선)
 * 2. '출퇴근'·'출근'·'퇴근' → 출퇴근
 * 3. '거래처·미팅·업무·회의·방문·고객·납품' → 일반업무 (비고는 말한 문장)
 * 못 알아들으면 null → 버튼으로 답한다.
 */
fun parsePurpose(text: String): Answer? {
    val t = text.trim()
    if (t.isEmpty()) return null
    if (t.contains("개인") || t.contains("사적")) return Answer(Category.PRIVATE, "개인 용무")
    if (t.contains("출퇴근")) return Answer(Category.COMMUTE, "출퇴근")
    if (t.contains("출근")) return Answer(Category.COMMUTE, "출근")
    if (t.contains("퇴근")) return Answer(Category.COMMUTE, "퇴근")
    if (BUSINESS_WORDS.any { t.contains(it) }) return Answer(Category.BUSINESS, t.take(30))
    return null
}

private val BUSINESS_WORDS = listOf("거래처", "미팅", "업무", "회의", "방문", "고객", "납품")

/** Gleo `LOG_TRIP_PURPOSE`의 purpose 값. 해석되지 않는 문장도 '업무 목적'으로 보고 일반업무로 기록한다. */
fun purposeFromIntent(purpose: String?): Answer? {
    val p = purpose?.trim().orEmpty()
    if (p.isEmpty()) return null
    return parsePurpose(p) ?: Answer(Category.BUSINESS, p.take(30))
}
