package com.dontstudyy.autolog.trip

/**
 * 운행 감지 엔진 (순수 Kotlin, Android 의존 없음). 설계서 6장.
 *
 * 규칙
 * - 기어가 P에서 벗어나면 현재 누적거리를 '주행 전'으로 진행 중 운행을 저장한다.
 * - 기어가 P가 되면 곧바로 끝내지 않고 [settleMs] 동안 주행거리 이벤트를 더 받아 마지막 값을 '주행 후'로 쓴다
 *   (단계 0 ② 실험: 주행거리 갱신이 기어 P보다 1초 늦게 올 수 있음). [tick]이 종료 시점을 알린다.
 * - 0.1km 미만 운행은 버린다.
 * - 새 운행은 항상 직전 기록의 '주행 후'에서 시작한다. 어긋나면 그 차이를 '빈 구간' 운행으로 메운다.
 *   그래서 "모든 운행의 주행 전 = 바로 앞 운행의 주행 후"가 구조적으로 지켜진다.
 */
class TripEngine(
    private val store: TripStore,
    private val settleMs: Long = SETTLE_MS,
) {
    private class Pending(val endTimeMs: Long, var tenths: Int)

    private var pending: Pending? = null

    /** 마지막으로 받은 누적거리(0.1km). 화면 표시용. */
    var currentTenths: Int? = null
        private set

    /** 서비스(앱) 시작. 빈 구간 처리와 기준값 저장. */
    fun onServiceStart(nowMs: Long, odoTenths: Int, isPark: Boolean, userId: Long = 0) {
        currentTenths = odoTenths
        pending = null
        val running = store.inProgress()
        if (running != null) {
            closeAsGap(running, nowMs, odoTenths)
        } else if (store.lastEndTenths == null) {
            store.lastEndTenths = odoTenths // 첫 실행: 기준값만. 빈 구간을 만들지 않는다.
        } else {
            reconcile(nowMs, odoTenths)
        }
        if (!isPark) startTrip(nowMs, odoTenths, userId)
    }

    /** 기어 변화. [odoTenths]는 이벤트를 받은 순간 읽은 누적거리. */
    fun onGearChanged(nowMs: Long, isPark: Boolean, odoTenths: Int, userId: Long = 0) {
        tick(nowMs)
        currentTenths = odoTenths
        if (isPark) {
            if (store.inProgress() != null && pending == null) pending = Pending(nowMs, odoTenths)
        } else {
            finalizePending()
            if (store.inProgress() == null) {
                reconcile(nowMs, odoTenths)
                startTrip(nowMs, odoTenths, userId)
            }
        }
    }

    /** 누적거리 이벤트. P 직후 대기 시간 안이면 '주행 후'를 갱신한다. */
    fun onOdometer(nowMs: Long, odoTenths: Int) {
        tick(nowMs)
        currentTenths = odoTenths
        pending?.let { it.tenths = odoTenths }
    }

    /** 시간 경과 알림. 대기 시간이 지났으면 운행을 끝낸다. */
    fun tick(nowMs: Long) {
        val p = pending ?: return
        if (nowMs - p.endTimeMs >= settleMs) finalizePending()
    }

    /** 대기 중인 종료가 있으면 [tick]을 몇 ms 뒤에 다시 불러야 하는지. 없으면 null. */
    fun msUntilSettle(nowMs: Long): Long? = pending?.let { maxOf(0L, settleMs - (nowMs - it.endTimeMs)) }

    fun reclassify(tripId: Long, category: Category, note: String? = null) {
        val t = store.all().first { it.id == tripId }
        store.update(t.copy(category = category, note = note ?: t.note))
    }

    private fun startTrip(nowMs: Long, odoTenths: Int, userId: Long) {
        store.insert(Trip(startTimeMs = nowMs, userId = userId, startTenths = odoTenths))
    }

    private fun finalizePending() {
        val p = pending ?: return
        pending = null
        val t = store.inProgress() ?: return
        if (p.tenths - t.startTenths < MIN_TENTHS) {
            store.delete(t.id) // 0.1km 미만은 버린다. lastEnd는 그대로(= 이 운행의 주행 전).
            return
        }
        store.update(t.copy(endTimeMs = p.endTimeMs, endTenths = p.tenths, status = TripStatus.DONE))
        store.lastEndTenths = p.tenths
    }

    /** 앱이 주행 도중 죽은 운행을 '주행 전 ~ 지금' 빈 구간으로 바꾼다. */
    private fun closeAsGap(running: Trip, nowMs: Long, odoTenths: Int) {
        if (odoTenths - running.startTenths < MIN_TENTHS) {
            store.delete(running.id)
            return
        }
        store.update(
            running.copy(
                endTimeMs = nowMs, endTenths = odoTenths, kind = TripKind.GAP,
                category = Category.UNCLASSIFIED, note = GAP_NOTE, status = TripStatus.DONE,
            ),
        )
        store.lastEndTenths = odoTenths
    }

    /** 마지막 기록의 '주행 후'와 지금 누적거리를 맞춘다. 차이가 0.1km 이상이면 빈 구간 운행을 만든다. */
    private fun reconcile(nowMs: Long, odoTenths: Int) {
        val last = store.lastEndTenths
        if (last == null) {
            store.lastEndTenths = odoTenths
            return
        }
        val diff = odoTenths - last
        if (diff >= MIN_TENTHS) {
            store.insert(
                Trip(
                    startTimeMs = store.lastDone()?.endTimeMs ?: nowMs, endTimeMs = nowMs,
                    startTenths = last, endTenths = odoTenths, kind = TripKind.GAP,
                    category = Category.UNCLASSIFIED, note = GAP_NOTE, status = TripStatus.DONE,
                ),
            )
            store.lastEndTenths = odoTenths
        } else if (diff < 0) {
            // 누적거리가 줄어듦(차량 교체·초기화). 기준값을 새로 잡는다.
            store.lastEndTenths = odoTenths
        }
    }

    companion object {
        const val SETTLE_MS = 1500L
        const val MIN_TENTHS = 1
        const val GAP_NOTE = "앱이 꺼져 있던 구간"
    }
}
