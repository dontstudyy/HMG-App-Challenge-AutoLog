package com.dontstudyy.autolog.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * FusedLocation 구독(단계 0 ⑤: 포그라운드 서비스가 있어야 계속 오고, 약 1.2초 간격).
 * 수신 시각은 앱이 받은 시각으로 기록한다. 콜백은 메인 스레드로 전달한다.
 */
class LocationSource(private val ctx: Context, private val onFix: (Fix) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var fused: FusedLocation? = null
    private var listener: FusedLocationListener? = null

    fun start() {
        if (fused != null) return
        try {
            val f = FusedLocation(ctx).also { it.initialize() }
            val l = object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    val loc = location ?: return
                    val now = System.currentTimeMillis()
                    main.post { onFix(Fix(loc.latitude, loc.longitude, now)) }
                }
                override fun onError(p0: Exception) { Log.w(TAG, "location error $p0") }
            }
            f.registerFusedLocationCallback(l)
            fused = f
            listener = l
        } catch (t: Throwable) {
            Log.w(TAG, "location start failed: $t")
        }
    }

    fun stop() {
        runCatching { listener?.let { fused?.unregisterFusedLocationCallback(it) } }
        runCatching { fused?.release() }
        fused = null
        listener = null
    }

    private companion object {
        const val TAG = "AutoLogLocation"
    }
}
