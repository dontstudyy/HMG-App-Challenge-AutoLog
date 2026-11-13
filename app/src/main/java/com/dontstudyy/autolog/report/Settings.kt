package com.dontstudyy.autolog.report

import kotlin.math.roundToInt

/** 설정에서 입력하는 값. 기본값은 설계서 2장 B4·B10(차종·차량번호, 연비·단가는 입력값 기준). */
data class AppSettings(
    val vehicleType: String = "업무용 승용차",
    val plate: String = "12가 3456",
    /** 운행기록부 머리의 상호명·사업자등록번호. 실존 회사를 기본값으로 넣지 않는다. */
    val company: String = "",
    val bizNo: String = "",
    /** 연비: 100km당 연료 소모(L). */
    val litersPer100km: Double = 7.1,
    /** 연료 단가(원/L). */
    val wonPerLiter: Int = 1650,
)

/** 예상 유류비(원): 총 주행거리 × 연비 × 단가를 100원 단위로 반올림. */
fun estimateFuelWon(totalTenths: Int, litersPer100km: Double, wonPerLiter: Int): Int {
    val km = totalTenths / 10.0
    val won = km * litersPer100km / 100.0 * wonPerLiter
    return (won / 100.0).roundToInt() * 100
}
