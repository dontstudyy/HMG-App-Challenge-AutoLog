package com.dontstudyy.autolog.navi

import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 길안내 이벤트를 받아 목적지 이름과 예상 통행료(원)를 전달한다(단계 0 ⑥).
 * 콜백은 메인 스레드로 보낸다. 길안내가 취소·종료되면 [onCleared].
 */
class NaviSource(
    private val ctx: Context,
    private val onRoute: (dest: String, tollWon: Int) -> Unit,
    private val onCleared: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var helper: NaviHelper? = null

    fun start() {
        if (helper != null) return
        try {
            val n = NaviHelper(ctx)
            n.initialize()
            n.addListener(object : NaviHelperEventListener {
                override fun onRouteStarted(p0: RouteStartInfo) {
                    Log.i(TAG, "onRouteStarted name=${p0.name} toll=${p0.tollFare}")
                    main.post { onRoute(p0.name.orEmpty(), p0.tollFare) }
                }
                override fun onDestinationInfo(p0: DestinationInfo) {
                    Log.i(TAG, "onDestinationInfo name=${p0.name} toll=${p0.tollFare}")
                    // 경로 계산 뒤 목적지 정보가 다시 오면 통행료를 갱신한다. 이름이 비어 있으면 무시(빈 값으로 덮어쓰지 않음).
                    val name = p0.name.orEmpty()
                    if (name.isNotBlank()) main.post { onRoute(name, p0.tollFare) }
                }
                override fun onRouteCancelled() { Log.i(TAG, "onRouteCancelled"); main.post { onCleared() } }
                override fun onDestinationArrived(p0: DestinationArrivedInfo) { Log.i(TAG, "onDestinationArrived"); main.post { onCleared() } }
                // 주의: 에뮬레이터는 onRouteStarted 직후에 onRouteEnded를 보낸다(경로 계산이 끝났다는 뜻). 종료로 보면 안 된다.
                override fun onRouteEnded() { Log.i(TAG, "onRouteEnded (무시)") }
            })
            helper = n
        } catch (t: Throwable) {
            Log.w(TAG, "navi start failed: $t")
        }
    }

    fun stop() {
        runCatching { helper?.release() }
        helper = null
    }

    private companion object {
        const val TAG = "AutoLogNavi"
    }
}
