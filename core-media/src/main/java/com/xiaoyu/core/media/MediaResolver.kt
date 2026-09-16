package com.xiaoyu.core.media

import android.util.Log
import com.xiaoyu.core.media.api.MusicApiClient
import com.xiaoyu.core.media.api.RadioApiClient
import com.xiaoyu.core.media.api.TxbMusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaResolver(
    private val musicApi: MusicApiClient,
    private val radioApi: RadioApiClient,
) {
    suspend fun resolveGeneral(limit: Int = 30, src: String = TxbMusicSource.DEFAULT): ResolveResult =
        buildPlayQueue(musicApi.fetchPlaylists(limit, src))

    suspend fun resolveCollection(
        query: String,
        limit: Int = 30,
        src: String = TxbMusicSource.DEFAULT,
    ): ResolveResult {
        if (query.isBlank()) return ResolveResult.failure("invalid_args", "缺少 query")
        return buildPlayQueue(musicApi.search(query, limit, src), preserveApiOrder = true)
    }

    /** 指定歌名/关键词：走 search 列表，按返回顺序 QUEUE_LOOP 连播 */
    suspend fun resolveTrack(
        keyword: String,
        limit: Int = 30,
        src: String = TxbMusicSource.DEFAULT,
    ): ResolveResult {
        if (keyword.isBlank()) return ResolveResult.failure("invalid_args", "缺少 keyword")
        return buildPlayQueue(musicApi.search(keyword, limit, src), preserveApiOrder = true)
    }

    suspend fun resolveRadio(name: String?): ResolveResult = radioApi.play(name)

    suspend fun resolveRadioRandom(): ResolveResult = radioApi.play(null)

    suspend fun resolveTrackUrl(track: Track): Track? {
        if (track.url.isNotBlank()) return track
        return withContext(Dispatchers.IO) {
            musicApi.resolvePlayUrl(track)
        }
    }

    /**
     * 只 eager resolve 第一首可播曲目，其余进队列 lazy resolve（切歌/onEnded 再解析）。
     * 最多尝试 [MAX_FIRST_RESOLVE_ATTEMPTS] 首，避免列表前 N 首不可播时连打 N 次 TXB。
     */
    private suspend fun buildPlayQueue(
        result: ResolveResult,
        singleMode: Boolean = false,
        preserveApiOrder: Boolean = false,
    ): ResolveResult {
        if (!result.ok) return result
        val sorted = if (preserveApiOrder) {
            result.tracks
        } else {
            result.tracks.sortedWith(compareBy({ !preferUrl(it.url) }, { it.url.isBlank() }))
        }
        if (sorted.isEmpty()) {
            return ResolveResult.failure("no_match", result.message ?: "没有可播放曲目")
        }

        var firstResolved: Track? = null
        var firstIndex = -1
        val attemptLimit = minOf(MAX_FIRST_RESOLVE_ATTEMPTS, sorted.size)
        for (i in 0 until attemptLimit) {
            val track = sorted[i]
            if (track.url.isNotBlank()) {
                firstResolved = track
                firstIndex = i
                break
            }
            Log.d(TAG, "lazy resolve first track attempt ${i + 1}/$attemptLimit: ${track.title}")
            val resolved = resolveTrackUrl(track) ?: continue
            if (resolved.url.isNotBlank()) {
                firstResolved = resolved
                firstIndex = i
                break
            }
        }

        if (firstResolved == null) {
            Log.w(TAG, "queue resolve failed for label=${result.queueLabel}, no playable first track")
            return ResolveResult.failure("no_match", result.message ?: "没有可播放曲目")
        }

        val queue = sorted.toMutableList()
        if (firstIndex > 0) {
            queue.removeAt(firstIndex)
            queue.add(0, firstResolved)
        } else {
            queue[0] = firstResolved
        }

        val mode = if (singleMode) PlaybackMode.SINGLE else result.mode
        Log.i(TAG, "queue ready: label=${result.queueLabel} size=${queue.size} first=${firstResolved.title}")
        return ResolveResult.success(queue, mode, result.queueLabel)
    }

    private fun preferUrl(url: String): Boolean =
        url.contains(".m4a", ignoreCase = true) ||
            url.contains(".mp3", ignoreCase = true) ||
            url.contains(".aac", ignoreCase = true)

    companion object {
        private const val TAG = "MediaResolver"
        private const val MAX_FIRST_RESOLVE_ATTEMPTS = 3
    }
}
