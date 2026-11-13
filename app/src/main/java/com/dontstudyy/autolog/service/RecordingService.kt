package com.dontstudyy.autolog.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.dontstudyy.autolog.AutoLogRuntime
import com.dontstudyy.autolog.MainActivity
import com.dontstudyy.autolog.R
import android.app.PendingIntent

/** 앱 화면이 없어도 차량 신호를 받아 운행을 기록하는 포그라운드 서비스(dataSync). */
class RecordingService : Service() {

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "운행 기록", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("운행을 자동으로 기록하고 있어요")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        AutoLogRuntime.start(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AutoLogRuntime.start(this) // 이미 시작됐으면 아무 일도 하지 않음
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AutoLogRuntime.stop()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "autolog_recording"
        private const val NOTIFICATION_ID = 1

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, RecordingService::class.java))
        }
    }
}
