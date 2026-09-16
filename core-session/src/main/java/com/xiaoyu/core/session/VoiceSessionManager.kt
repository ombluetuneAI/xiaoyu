package com.xiaoyu.core.session

import com.xiaoyu.core.wake.WakeWords

enum class VoiceSessionState {
    IDLE,
    WAKE_DETECTED,
    LISTENING,
    THINKING,
    SPEAKING,
    FOLLOW_UP,
}

enum class ListenTrigger {
    WAKE,
    FOLLOW_UP,
}

enum class SessionEndReason {
    /** 用户说「再见」等，允许服务端礼貌结束语 */
    GOODBYE,
    /** 无操作空闲超时，本地静默退出 */
    IDLE_TIMEOUT,
    /** 通知栏/调试结束对话 */
    MANUAL,
    /** 语音 WS 断开 */
    DISCONNECT,
    /** 连续对话关闭等非空闲结束 */
    NORMAL,
}

class VoiceSessionManager(
    private val continuousDialog: () -> Boolean,
    private val idleTimeoutSec: () -> Int,
    private val onStateChanged: (VoiceSessionState) -> Unit,
    private val onPauseWake: () -> Unit,
    private val onStartListening: (ListenTrigger, wakeWord: String) -> Unit,
    private val onStopListening: () -> Unit,
    private val onResumeWake: () -> Unit,
    private val onEndSession: (SessionEndReason) -> Unit,
    private val onWakeBlocked: (() -> Unit)? = null,
) {
    var state: VoiceSessionState = VoiceSessionState.IDLE
        private set

    private var idleDeadlineMs: Long = 0L

    fun transitionTo(newState: VoiceSessionState) {
        state = newState
        when (newState) {
            VoiceSessionState.LISTENING,
            VoiceSessionState.FOLLOW_UP,
            -> scheduleIdleTimeout()
            VoiceSessionState.THINKING,
            VoiceSessionState.SPEAKING,
            VoiceSessionState.IDLE,
            VoiceSessionState.WAKE_DETECTED,
            -> clearIdleTimeout()
        }
        onStateChanged(newState)
    }

    fun onWakeWordDetected(voiceAllowed: Boolean, wakeWord: String = WakeWords.DEFAULT) {
        if (!voiceAllowed) {
            onWakeBlocked?.invoke()
            return
        }
        onPauseWake()
        transitionTo(VoiceSessionState.WAKE_DETECTED)
    }

    /** 唤醒应答 TTS/WAV 与 WS 就绪后开麦；不必等本地应答 WAV 播完 */
    fun startListeningAfterWakeAck(wakeWord: String = WakeWords.DEFAULT) {
        if (state != VoiceSessionState.WAKE_DETECTED) return
        onStartListening(ListenTrigger.WAKE, wakeWord)
    }

    fun onVadEnd() {
        if (state == VoiceSessionState.LISTENING || state == VoiceSessionState.FOLLOW_UP) {
            clearIdleTimeout()
            onStopListening()
            transitionTo(VoiceSessionState.THINKING)
        }
    }

    fun onTtsStart() {
        clearIdleTimeout()
        transitionTo(VoiceSessionState.SPEAKING)
    }

    fun onTtsComplete() {
        if (continuousDialog()) {
            transitionTo(VoiceSessionState.FOLLOW_UP)
            onStartListening(ListenTrigger.FOLLOW_UP, "")
        } else {
            endSession(SessionEndReason.NORMAL)
        }
    }

    /** 用户说话/STT 活动时重置空闲计时（仍在 LISTENING/FOLLOW_UP） */
    fun onUserActivity() {
        if (state == VoiceSessionState.LISTENING || state == VoiceSessionState.FOLLOW_UP) {
            scheduleIdleTimeout()
        }
    }

    fun onIdleTimeout() {
        if (state == VoiceSessionState.LISTENING || state == VoiceSessionState.FOLLOW_UP) {
            endSession(SessionEndReason.IDLE_TIMEOUT)
        }
    }

    fun onGoodbye() {
        endSession(SessionEndReason.GOODBYE)
    }

    fun endSession(reason: SessionEndReason = SessionEndReason.MANUAL) {
        clearIdleTimeout()
        onEndSession(reason)
        transitionTo(VoiceSessionState.IDLE)
        onResumeWake()
    }

    fun isIdleExpired(): Boolean =
        idleDeadlineMs > 0L &&
            (state == VoiceSessionState.LISTENING || state == VoiceSessionState.FOLLOW_UP) &&
            System.currentTimeMillis() > idleDeadlineMs

    private fun scheduleIdleTimeout() {
        idleDeadlineMs = System.currentTimeMillis() + idleTimeoutSec() * 1000L
    }

    private fun clearIdleTimeout() {
        idleDeadlineMs = 0L
    }
}
