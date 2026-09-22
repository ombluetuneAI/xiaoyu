package com.xiaoyu.core.media.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackResumeArbiterTest {

    @Test
    fun voiceInterruption_thenSessionEnd_resumesOnce() {
        val arbiter = PlaybackResumeArbiter()
        arbiter.onVoiceOutputPaused(wasPlaying = true)
        assertTrue("语音打断后应记下续播意图", arbiter.resumeAfterVoice)
        assertTrue("会话结束应恢复背景乐", arbiter.consumeVoiceResume())
        assertFalse("续播意图应被消费掉，不应重复恢复", arbiter.consumeVoiceResume())
    }

    @Test
    fun voiceOutputPaused_whileNotPlaying_noResumeIntent() {
        val arbiter = PlaybackResumeArbiter()
        arbiter.onVoiceOutputPaused(wasPlaying = false)
        assertFalse("此前未在播放则不应恢复", arbiter.consumeVoiceResume())
    }

    /** 既有「避免误续播」回归保护：语音打断后用户显式暂停，会话结束不得自动续播 */
    @Test
    fun userPause_afterVoiceInterruption_doesNotResumeAfterSession() {
        val arbiter = PlaybackResumeArbiter()
        arbiter.onVoiceOutputPaused(wasPlaying = true)
        arbiter.onUserPause()
        assertFalse("用户显式暂停后，会话结束不应自动续播", arbiter.consumeVoiceResume())
    }

    @Test
    fun transientFocusLoss_whilePlaying_resumesOnGain() {
        val arbiter = PlaybackResumeArbiter()
        arbiter.onTransientFocusLossPaused(wasPlaying = true)
        assertTrue("瞬时失焦（如来电）恢复后应自动续播", arbiter.consumeFocusResume())
        assertFalse("续播意图应被消费", arbiter.consumeFocusResume())
    }

    @Test
    fun transientFocusLoss_whileNotPlaying_noResumeOnGain() {
        val arbiter = PlaybackResumeArbiter()
        arbiter.onTransientFocusLossPaused(wasPlaying = false)
        assertFalse("此前未在播放则失焦恢复不应续播", arbiter.consumeFocusResume())
    }

    /**
     * 核心 bug 修复：「暂停后过段时间又自己播放」。
     * 音乐因瞬时失焦被暂停并置位自动续播意图后，用户又显式暂停，
     * 稍后焦点恢复不得自动续播。
     */
    @Test
    fun userPause_afterFocusLossArmed_doesNotResumeOnGain() {
        val arbiter = PlaybackResumeArbiter()
        arbiter.onTransientFocusLossPaused(wasPlaying = true)
        assertTrue(arbiter.resumeOnFocusGain)
        arbiter.onUserPause()
        assertFalse("用户暂停应作废失焦续播意图，暂停必须粘手", arbiter.consumeFocusResume())
    }

    /** 永久失焦（用户切到其它音频 App）：不应自动续播 */
    @Test
    fun permanentFocusLoss_doesNotResumeOnGain() {
        val arbiter = PlaybackResumeArbiter()
        arbiter.onTransientFocusLossPaused(wasPlaying = true)
        arbiter.onPermanentFocusLoss()
        assertFalse("永久失焦后不应自动续播", arbiter.consumeFocusResume())
    }

    @Test
    fun cancelVoiceResume_clearsVoiceIntentOnly() {
        val arbiter = PlaybackResumeArbiter()
        arbiter.onVoiceOutputPaused(wasPlaying = true)
        arbiter.onTransientFocusLossPaused(wasPlaying = true)
        arbiter.cancelVoiceResume()
        assertFalse("说再见应仅清除会话续播意图", arbiter.consumeVoiceResume())
        assertTrue("失焦续播意图应保留", arbiter.consumeFocusResume())
    }

    @Test
    fun userPause_clearsBothIntents() {
        val arbiter = PlaybackResumeArbiter()
        arbiter.onVoiceOutputPaused(wasPlaying = true)
        arbiter.onTransientFocusLossPaused(wasPlaying = true)
        arbiter.onUserPause()
        assertFalse(arbiter.consumeVoiceResume())
        assertFalse(arbiter.consumeFocusResume())
    }

    @Test
    fun userStickyPause_blocksOfflineNextUntilResume() {
        val arbiter = PlaybackResumeArbiter()
        arbiter.onUserPause()
        assertTrue(arbiter.blocksOfflineNextWhilePaused())
        arbiter.onUserResumePlayback()
        assertFalse(arbiter.blocksOfflineNextWhilePaused())
    }

    @Test
    fun stop_clearsBothIntents() {
        val arbiter = PlaybackResumeArbiter()
        arbiter.onVoiceOutputPaused(wasPlaying = true)
        arbiter.onTransientFocusLossPaused(wasPlaying = true)
        arbiter.onStop()
        assertFalse(arbiter.consumeVoiceResume())
        assertFalse(arbiter.consumeFocusResume())
    }
}
