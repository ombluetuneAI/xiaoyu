package com.xiaoyu.core.media.player

/**
 * 播放「自动续播意图」仲裁器（纯逻辑，可单元测试）。
 *
 * 背景乐会在两类场景被暂停并稍后自动恢复：
 *  1. 语音会话/小智 TTS 打断（[resumeAfterVoice]）；
 *  2. 瞬时失焦，如来电、导航播报（[resumeOnFocusGain]）。
 *
 * 关键约束：**用户显式暂停必须“粘手”**——一旦用户主动暂停，
 * 任何待定的自动续播意图都要作废，避免「暂停后过段时间又自己播放」。
 * 永久失焦（用户切到其它音频 App）也不应自动续播。
 */
class PlaybackResumeArbiter {

    /** 语音会话结束后是否应恢复被打断的背景乐 */
    var resumeAfterVoice: Boolean = false
        private set

    /** 瞬时失焦恢复后是否应自动续播 */
    var resumeOnFocusGain: Boolean = false
        private set

    /** 用户显式暂停后保持，直到继续播放/新开一曲；用于忽略离线「下一首」误触 */
    var userStickyPause: Boolean = false
        private set

    /** 唤醒/TTS 前暂停背景乐：仅当此前确在播放时，才记下会话后续播意图 */
    fun onVoiceOutputPaused(wasPlaying: Boolean) {
        if (wasPlaying) resumeAfterVoice = true
    }

    /** 语音会话结束，读取并清除“会话后续播”意图 */
    fun consumeVoiceResume(): Boolean {
        val should = resumeAfterVoice
        resumeAfterVoice = false
        return should
    }

    /** 用户说「再见」/主动结束会话：仅取消“会话后续播”意图 */
    fun cancelVoiceResume() {
        resumeAfterVoice = false
    }

    /** 瞬时失焦暂停：仅当此前确在播放时，才记下失焦恢复后的续播意图 */
    fun onTransientFocusLossPaused(wasPlaying: Boolean) {
        if (wasPlaying) resumeOnFocusGain = true
    }

    /** 重新获得焦点，读取并清除“失焦后续播”意图 */
    fun consumeFocusResume(): Boolean {
        val should = resumeOnFocusGain
        resumeOnFocusGain = false
        return should
    }

    /** 永久失焦（用户切到其它音频 App）：不再自动续播 */
    fun onPermanentFocusLoss() {
        resumeOnFocusGain = false
    }

    /**
     * 用户显式暂停（通知栏/播放页/语音「暂停」/离线播控）：
     * 取消一切自动续播意图，让暂停粘手。
     */
    fun onUserPause() {
        resumeAfterVoice = false
        resumeOnFocusGain = false
        userStickyPause = true
    }

    /** 用户继续播放或开始播放新曲目：解除暂停粘手 */
    fun onUserResumePlayback() {
        userStickyPause = false
    }

    fun blocksOfflineNextWhilePaused(): Boolean = userStickyPause

    /** 停止播放：清空全部续播意图 */
    fun onStop() {
        resumeAfterVoice = false
        resumeOnFocusGain = false
        userStickyPause = false
    }
}
