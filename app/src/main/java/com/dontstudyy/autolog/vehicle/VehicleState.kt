package com.dontstudyy.autolog.vehicle

enum class Gear(val label: String) {
    PARK("P"), REVERSE("R"), NEUTRAL("N"), DRIVE("D"), UNKNOWN("?");

    companion object {
        /** AAOS GEAR_SELECTION 값: N=1, R=2, P=4, D=8 (단계 0 실험에서 P=4, D=8 확인). */
        fun fromAaos(v: Int?): Gear = when (v) {
            1 -> NEUTRAL
            2 -> REVERSE
            4 -> PARK
            8 -> DRIVE
            else -> UNKNOWN
        }
    }
}

/** 화면이 구독하는 차량 상태. 거리는 0.1km 정수. */
data class VehicleState(
    val odoTenths: Int? = null,
    val gear: Gear = Gear.UNKNOWN,
    /** 주행 규제 중이면 true. 알 수 없으면 null. */
    val uxRestricted: Boolean? = null,
    val connected: Boolean = false,
    val odoUpdatedAtMs: Long = 0,
)
