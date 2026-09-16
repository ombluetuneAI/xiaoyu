package com.xiaoyu.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log

/** 唤醒应答：内置 WAV，不依赖系统 TTS 引擎 */
class WakeAckPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null

    var onPlayingChanged: ((Boolean) -> Unit)? = null
    var onBeforePlay: (() -> Unit)? = null

    fun playRandom(onComplete: () -> Unit) {
        mainHandler.post {
            stopInternal(releaseOnly = true)
            val resId = ACK_RESOURCES.random()
            onBeforePlay?.invoke()
            onPlayingChanged?.invoke(true)
            val mp = MediaPlayer.create(appContext, resId)
            if (mp == null) {
                Log.w(TAG, "wake ack MediaPlayer.create failed resId=$resId")
                onPlayingChanged?.invoke(false)
                onComplete()
                return@post
            }
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            player = mp
            mp.setOnCompletionListener {
                stopInternal(releaseOnly = true)
                onPlayingChanged?.invoke(false)
                onComplete()
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "wake ack playback error what=$what extra=$extra")
                stopInternal(releaseOnly = true)
                onPlayingChanged?.invoke(false)
                onComplete()
                true
            }
            Log.i(TAG, "wake ack playing resId=$resId")
            mp.start()
        }
    }

    fun stop() {
        mainHandler.post {
            stopInternal(releaseOnly = true)
            onPlayingChanged?.invoke(false)
        }
    }

    private fun stopInternal(releaseOnly: Boolean) {
        player?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        player = null
    }

    companion object {
        private const val TAG = "WakeAckPlayer"
        private val ACK_RESOURCES = intArrayOf(
            R.raw.wake_zai_ne,
            R.raw.wake_wo_zai,
        )
    }
}
