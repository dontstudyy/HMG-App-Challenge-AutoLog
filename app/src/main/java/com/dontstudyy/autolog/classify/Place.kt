package com.dontstudyy.autolog.classify

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class PlaceKind(val label: String) { HOME("집"), COMPANY("회사"), CLIENT("거래처"), OTHER("기타") }

/** 등록 장소. 반경 기본 300m(설계서 5장). */
data class Place(
    val id: Long = 0,
    val name: String,
    val kind: PlaceKind,
    val lat: Double,
    val lon: Double,
    val radiusM: Float = DEFAULT_RADIUS_M,
) {
    companion object {
        const val DEFAULT_RADIUS_M = 300f
    }
}

/** 두 좌표 사이의 거리(미터, 하버사인). */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = Math.toRadians(lat2 - lat1)
    val dl = Math.toRadians(lon2 - lon1)
    val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

/** 반경 안에 있는 장소 중 가장 가까운 것. 없으면 null. */
fun findPlace(lat: Double, lon: Double, places: List<Place>): Place? =
    places.map { it to distanceMeters(lat, lon, it.lat, it.lon) }
        .filter { (p, d) -> d <= p.radiusM }
        .minByOrNull { it.second }
        ?.first
