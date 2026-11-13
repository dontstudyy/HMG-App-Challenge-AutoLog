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
    /** 출발·도착 시점에 위치가 속한 등록 장소 이름. 미등록이면 빈 문자열. */
    val startPlace: String = "",
    val endPlace: String = "",
    /** 도착 시점의 마지막 위치(미등록 위치를 거래처로 저장할 때 쓴다). 위치를 모르면 null. */
    val endLat: Double? = null,
    val endLon: Double? = null,
    /** 길안내로 받은 예상 통행료(원). 길안내를 쓰지 않았으면 0. */
    val tollWon: Int = 0,
    /** 길안내 목적지 이름. 없으면 빈 문자열. */
    val naviDest: String = "",
) {
    /** 진행 중이면 0. */
    val distanceTenths: Int get() = (endTenths ?: startTenths) - startTenths
}

/** 운행 시작·도착 시점의 위치 정보. 이름은 등록 장소 이름(없으면 빈 문자열). */
data class Where(val place: String = "", val lat: Double? = null, val lon: Double? = null)

/** 킬로미터(Float)를 0.1km 단위 정수로. 소수 오차 방지를 위해 반올림한다. */
fun kmToTenths(km: Float): Int = Math.round(km * 10f)
