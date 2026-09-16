package com.xiaoyu.core.media

import android.content.Context
import android.media.AudioManager
import android.os.Build
import kotlin.math.roundToInt

data class VolumeState(
    val percent: Int,
    val index: Int,
    val maxIndex: Int,
    val muted: Boolean,
)

class VolumeController(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val stream = AudioManager.STREAM_MUSIC
    private var volumeBeforeMute: Int? = null

    fun currentState(): VolumeState {
        val max = audioManager.getStreamMaxVolume(stream)
        val index = audioManager.getStreamVolume(stream)
        return VolumeState(
            percent = volumeIndexToPercent(index, max),
            index = index,
            maxIndex = max,
            muted = isMuted(index),
        )
    }

    fun setPercent(percent: Int): VolumeState {
        val max = audioManager.getStreamMaxVolume(stream)
        val index = percentToVolumeIndex(percent, max)
        if (isMuted(audioManager.getStreamVolume(stream))) {
            unmuteInternal(max)
        }
        audioManager.setStreamVolume(stream, index, 0)
        volumeBeforeMute = null
        return currentState()
    }

    fun adjustPercent(deltaPercent: Int): VolumeState {
        val state = currentState()
        val target = (state.percent + deltaPercent).coerceIn(0, 100)
        return setPercent(target)
    }

    fun setMuted(mute: Boolean): VolumeState {
        if (mute) {
            muteInternal()
        } else {
            unmuteInternal(audioManager.getStreamMaxVolume(stream))
        }
        return currentState()
    }

    private fun muteInternal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!audioManager.isStreamMute(stream)) {
                volumeBeforeMute = audioManager.getStreamVolume(stream)
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
            }
        } else {
            val current = audioManager.getStreamVolume(stream)
            if (current > 0) {
                volumeBeforeMute = current
                audioManager.setStreamVolume(stream, 0, 0)
            }
        }
    }

    private fun unmuteInternal(max: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (audioManager.isStreamMute(stream)) {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
            }
        }
        if (audioManager.getStreamVolume(stream) == 0) {
            val restore = (volumeBeforeMute ?: (max / 2).coerceAtLeast(1)).coerceIn(1, max)
            audioManager.setStreamVolume(stream, restore, 0)
        }
        volumeBeforeMute = null
    }

    private fun isMuted(index: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager.isStreamMute(stream)) {
            return true
        }
        return index == 0
    }

    companion object {
        fun volumeIndexToPercent(index: Int, maxIndex: Int): Int {
            if (maxIndex <= 0) return 0
            return ((index * 100f) / maxIndex).roundToInt().coerceIn(0, 100)
        }

        fun percentToVolumeIndex(percent: Int, maxIndex: Int): Int {
            if (maxIndex <= 0) return 0
            val p = percent.coerceIn(0, 100)
            return (p * maxIndex / 100f).roundToInt().coerceIn(0, maxIndex)
        }
    }
}
