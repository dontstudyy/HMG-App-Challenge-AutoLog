package com.dontstudyy.autolog.trip

/** 분류. PRIVATE = 비업무(개인 용무). */
enum class Category { COMMUTE, BUSINESS, PRIVATE, UNCLASSIFIED }

enum class TripKind { NORMAL, GAP }

enum class TripStatus { IN_PROGRESS, DONE }

/**
 * 운행 1건. 거리는 모두 0.1km 단위 정수(tenths)다. 예: 12,321.4km = 123214.
 * 시각은 epoch 밀리초.
 */
data class Trip(
    val id: Long = 0,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val userId: Long = 0,
    val startTenths: Int,
    val endTenths: Int? = null,
    val category: Category = Category.UNCLASSIFIED,
    val note: String = "",
    val kind: TripKind = TripKind.NORMAL,
    val status: TripStatus = TripStatus.IN_PROGRESS,
) {
    /** 진행 중이면 0. */
    val distanceTenths: Int get() = (endTenths ?: startTenths) - startTenths
}

/** 킬로미터(Float)를 0.1km 단위 정수로. 소수 오차 방지를 위해 반올림한다. */
fun kmToTenths(km: Float): Int = Math.round(km * 10f)
