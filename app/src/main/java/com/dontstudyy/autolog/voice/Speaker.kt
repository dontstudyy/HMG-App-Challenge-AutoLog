package com.dontstudyy.autolog.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.util.Locale

/**
 * 질문을 소리로도 읽어 준다. 소리는 덧붙이는 기능이라 실패해도 흐름은 멈추지 않는다(설계서 2장 B2).
 * 단계 0 V1에서 TTS 반복 호출이 불안정했으므로 호출마다 새로 만든다(⑦). 결과는 로그 태그 AUTOLOG_VOICE.
 */
object Speaker {
    private const val TAG = "AUTOLOG_VOICE"
    private var tts: TextToSpeech? = null
    private var counter = 0

    fun speak(ctx: Context, text: String) {
        val n = ++counter
        try {
            runCatching { tts?.release() }
            val t = TextToSpeech(ctx.applicationContext, Mode.ON_DEVICE)
            tts = t
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    Log.i(TAG, "TTS#$n init ok")
                    t.addEventListener(object : EventListener {
                        override fun onStart() { Log.i(TAG, "TTS#$n onStart") }
                        override fun onReady() {}
                        override fun onDone() { Log.i(TAG, "TTS#$n onDone") }
                        override fun onError(p0: ErrorType) { Log.w(TAG, "TTS#$n onError $p0") }
                        override fun onStop() {}
                        override fun onUpdatedRms(p0: Double) {}
                    })
                    t.setLocale(Locale.KOREA)
                    t.speak(text, false)
                    Log.i(TAG, "TTS#$n speak: $text")
                }
                override fun onFailure(p0: ErrorType) { Log.w(TAG, "TTS#$n init fail $p0") }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "TTS#$n failed: $t")
        }
    }

    fun release() {
        runCatching { tts?.release() }
        tts = null
    }
}
