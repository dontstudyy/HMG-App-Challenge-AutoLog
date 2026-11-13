package com.dontstudyy.autolog.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * 말로 답하기(보조). 부분 결과에서 키워드를 찾으면 바로 답으로 넘기고 끝낸다.
 * 인식이 안 되거나 오류가 나면 아무 일도 하지 않는다(버튼으로 답한다). 설계서 2장 A4.
 * [pcmFile]이 있으면 마이크 대신 그 파일(16kHz PCM)을 보낸다(에뮬레이터 확인용).
 */
object Listener {
    private const val TAG = "AUTOLOG_VOICE"
    private val main = Handler(Looper.getMainLooper())
    private var stt: SpeechToText? = null
    private var handled = false

    fun listen(ctx: Context, pcmFile: File? = null, onAnswer: (Answer) -> Unit, onEnd: () -> Unit = {}) {
        stop()
        handled = false
        try {
            val s = SpeechToText(ctx.applicationContext, Mode.ON_DEVICE)
            stt = s
            s.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    s.addListener(object : ResultListener {
                        override fun onUpdated(p0: String, p1: Boolean) {
                            Log.i(TAG, "STT result final=$p1 text=$p0")
                            val a = parsePurpose(p0)
                            if (a != null && !handled) {
                                handled = true
                                main.post { onAnswer(a); stop() }
                            }
                        }
                        override fun onUpdatedEpdData(p0: Long, p1: Long) {}
                        override fun onStartedRecognition() { Log.i(TAG, "STT started") }
                        override fun onEndedRecognition() { Log.i(TAG, "STT ended"); main.post(onEnd) }
                        override fun onError(p0: ErrorType) { Log.w(TAG, "STT error $p0"); main.post(onEnd) }
                        override fun onReady() { Log.i(TAG, "STT ready") }
                        override fun onUpdatedRms(p0: Double) {}
                    })
                    s.setLocale(Locale.KOREA)
                    s.request()
                    if (pcmFile != null) feed(s, pcmFile)
                }
                override fun onFailure(p0: ErrorType) { Log.w(TAG, "STT init fail $p0"); main.post(onEnd) }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "STT failed: $t")
            onEnd()
        }
    }

    private fun feed(s: SpeechToText, f: File) {
        Thread {
            try {
                val bytes = f.readBytes()
                Thread.sleep(1500)
                var off = 0
                while (off < bytes.size) {
                    val n = minOf(3200, bytes.size - off)
                    s.sendAudio(bytes.copyOfRange(off, off + n)); off += n
                    Thread.sleep(100)
                }
                s.completeAudioSend()
            } catch (t: Throwable) {
                Log.w(TAG, "STT feed failed: $t")
            }
        }.start()
    }

    fun stop() {
        runCatching { stt?.stop() }
        runCatching { stt?.release() }
        stt = null
    }
}
