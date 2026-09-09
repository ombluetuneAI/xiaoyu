package com.xiaoyu.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class TtsHelper(context: Context) : TextToSpeech.OnInitListener {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, this)
    private val ready = AtomicBoolean(false)
    var onSpeakingChanged: ((Boolean) -> Unit)? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS engine init failed status=$status")
            return
        }
        val langResult = when {
            tts.setLanguage(Locale.SIMPLIFIED_CHINESE) >= TextToSpeech.LANG_AVAILABLE -> TextToSpeech.LANG_AVAILABLE
            tts.setLanguage(Locale.CHINESE) >= TextToSpeech.LANG_AVAILABLE -> TextToSpeech.LANG_AVAILABLE
            else -> tts.setLanguage(Locale.getDefault())
        }
        if (langResult < TextToSpeech.LANG_AVAILABLE) {
            Log.e(TAG, "TTS Chinese language unavailable result=$langResult")
            return
        }
        ready.set(true)
        Log.i(TAG, "TTS engine ready")
    }

    /** 音箱式唤醒应答：随机「在呢」/「我在」，播完再回调（用于延迟开麦）。 */
    fun speakWakeAck(onComplete: () -> Unit) {
        speakWakeAckInternal(onComplete, attemptsLeft = 50)
    }

    private fun speakWakeAckInternal(onComplete: () -> Unit, attemptsLeft: Int) {
        if (!ready.get()) {
            if (attemptsLeft <= 0) {
                Log.w(TAG, "wake ack skipped: TTS not ready")
                onComplete()
                return
            }
            mainHandler.postDelayed({ speakWakeAckInternal(onComplete, attemptsLeft - 1) }, 100)
            return
        }
        val phrase = WAKE_ACK_PHRASES.random()
        Log.i(TAG, "wake ack TTS: $phrase")
        speak(phrase, onComplete)
    }

    fun speak(message: String) {
        speak(message, onComplete = null)
    }

    fun speak(message: String, onComplete: (() -> Unit)?) {
        if (!ready.get()) {
            Log.w(TAG, "speak skipped (not ready): $message")
            onComplete?.invoke()
            return
        }
        onSpeakingChanged?.invoke(true)
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                onSpeakingChanged?.invoke(false)
                onComplete?.invoke()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onSpeakingChanged?.invoke(false)
                onComplete?.invoke()
            }
        })
        val result = tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "xiaoyu_tts")
        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "speak failed result=$result message=$message")
            onSpeakingChanged?.invoke(false)
            onComplete?.invoke()
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    companion object {
        private const val TAG = "TtsHelper"
        private val WAKE_ACK_PHRASES = listOf("在呢", "我在")
    }
}
