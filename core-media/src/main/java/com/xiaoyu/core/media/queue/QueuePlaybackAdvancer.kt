package com.xiaoyu.core.media.queue

import android.util.Log
import com.xiaoyu.core.media.MediaResolver
import com.xiaoyu.core.media.PlaybackMode
import com.xiaoyu.core.media.player.MediaPlayerFacade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 队列自动续播：串行化 onEnded，仅在播放成功后才推进 currentIndex。
 * 失败重试只 peek 后续曲目，避免「已请求 TXB 但未播」的中间索引被跳过。
 */
class QueuePlaybackAdvancer(
    private val scope: CoroutineScope,
    private val queueManager: MusicQueueManager,
    private val mediaResolver: MediaResolver,
    private val playerFacade: MediaPlayerFacade,
    private val onAdvanced: () -> Unit = {},
) {
    private val advanceMutex = Mutex()

    fun onTrackEnded() {
        if (queueManager.mode.value != PlaybackMode.QUEUE_LOOP) return
        scope.launch(Dispatchers.IO) {
            val locked = advanceMutex.tryLock()
            if (!locked) {
                Log.i(TAG, "skip duplicate onEnded while advance in progress")
                return@launch
            }
            try {
                advanceToNextPlayable()
            } finally {
                advanceMutex.unlock()
            }
        }
    }

    private suspend fun advanceToNextPlayable() {
        val list = queueManager.tracks.value
        if (list.isEmpty()) return

        val startIndex = queueManager.currentIndex.value
        val attemptLimit = minOf(MAX_ATTEMPTS, list.size)

        for (offset in 1..attemptLimit) {
            val candidate = queueManager.peekAtOffset(offset) ?: break
            Log.i(TAG, "try offset=$offset from=$startIndex title=${candidate.title}")

            val resolved = mediaResolver.resolveTrackUrl(candidate) ?: candidate
            if (resolved.url.isBlank()) {
                Log.w(TAG, "skip offset=$offset (no url): ${candidate.title}")
                continue
            }

            val ok = playerFacade.playTrackAwait(resolved, queueManager.mode.value)
            if (ok) {
                queueManager.jumpTo((startIndex + offset) % list.size)
                onAdvanced()
                Log.i(TAG, "advanced to index=${queueManager.currentIndex.value} title=${resolved.title}")
                return
            }
            Log.w(TAG, "playback failed offset=$offset title=${resolved.title}")
        }

        Log.w(TAG, "queue advance exhausted from index=$startIndex")
    }

    companion object {
        private const val TAG = "QueuePlaybackAdvancer"
        private const val MAX_ATTEMPTS = 10
    }
}
