package com.dontstudyy.autolog.classify

import com.dontstudyy.autolog.trip.Category

sealed interface Verdict {
    /** 자동 분류가 확실함. */
    data class Auto(val category: Category, val note: String) : Verdict

    /** 확실하지 않음 → 주차 후 질문. */
    data object Ask : Verdict
}

/**
 * 자동 분류 규칙 (설계서 7장). 위에서부터 먼저 맞는 것.
 * 1. 출발·도착이 집과 회사 → 출퇴근 (집→회사 출근, 회사→집 퇴근)
 * 2. 도착 또는 출발이 거래처 → 일반업무, 비고에 장소 이름(도착 우선)
 * 3. 길안내 목적지 이름이 등록 장소와 같으면 그 장소를 도착으로 본다(실제 도착 장소가 있으면 그쪽이 우선)
 * 4. 그 밖 → 질문
 */
fun classify(start: Place?, end: Place?, naviDestination: String?, places: List<Place>): Verdict {
    val arrival = end ?: naviDestination?.let { name -> places.firstOrNull { it.name == name } }

    if (start != null && arrival != null) {
        if (start.kind == PlaceKind.HOME && arrival.kind == PlaceKind.COMPANY) return Verdict.Auto(Category.COMMUTE, "출근")
        if (start.kind == PlaceKind.COMPANY && arrival.kind == PlaceKind.HOME) return Verdict.Auto(Category.COMMUTE, "퇴근")
    }
    if (arrival?.kind == PlaceKind.CLIENT) return Verdict.Auto(Category.BUSINESS, arrival.name)
    if (start?.kind == PlaceKind.CLIENT) return Verdict.Auto(Category.BUSINESS, start.name)
    return Verdict.Ask
}
