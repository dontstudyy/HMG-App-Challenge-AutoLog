package com.dontstudyy.autolog.voice

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.dontstudyy.autolog.AutoLogRuntime
import com.dontstudyy.autolog.service.RecordingService

/**
 * Gleo 음성 명령 진입점(화면 없음). intent의 action이 곧 기능 이름이다(단계 0 V3).
 * 시연은 adb로 같은 intent를 보낸다. 예) adb shell am start -a LOG_TRIP_PURPOSE --es purpose "거래처 미팅"
 *
 * - LOG_TRIP_PURPOSE(purpose): 가장 최근 미분류 운행에 목적을 기록
 * - START_PRIVATE_TRIP: 이번(또는 다음) 운행을 개인 용무로 기록
 * - GET_BUSINESS_RATIO: 이번 달 업무사용비율을 알려 줌(토스트 + 음성)
 */
class GleoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutoLogRuntime.init(this)
        RecordingService.start(this)
        val msg = handle()
        Log.i(TAG, "action=${intent.action} -> $msg")
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun handle(): String = when (intent.action) {
        "LOG_TRIP_PURPOSE" -> {
            val answer = purposeFromIntent(intent.getStringExtra("purpose"))
            if (answer == null) {
                "목적을 알려 주세요"
            } else {
                val trip = AutoLogRuntime.logPurpose(answer)
                if (trip == null) "기록할 운행이 없어요" else "운행 목적을 기록했어요: ${answer.note}"
            }
        }
        "START_PRIVATE_TRIP" -> {
            AutoLogRuntime.startPrivateTrip()
            "이번 운행은 개인 용무로 기록할게요"
        }
        "GET_BUSINESS_RATIO" -> {
            val r = AutoLogRuntime.businessRatioText()
            val text = if (r == null) "아직 이번 달 운행이 없어요" else "이번 달 업무사용비율은 ${r}퍼센트예요"
            Speaker.speak(this, text)
            text
        }
        else -> "알 수 없는 명령이에요"
    }

    private companion object {
        const val TAG = "AUTOLOG_GLEO"
    }
}
