package com.dontstudyy.autolog.location

/** 위치 한 번 수신. [atMs]는 앱이 받은 시각(epoch ms). */
data class Fix(val lat: Double, val lon: Double, val atMs: Long)

/** 위치 수신이 이 시간(ms)보다 오래 없으면 '끊김'으로 본다(설계서 7장 기본 10초). */
const val LOST_AFTER_MS = 10_000L

fun isLost(last: Fix?, nowMs: Long, lostAfterMs: Long = LOST_AFTER_MS): Boolean =
    last == null || nowMs - last.atMs > lostAfterMs
