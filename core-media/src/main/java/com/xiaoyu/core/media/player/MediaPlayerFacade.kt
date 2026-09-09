package com.xiaoyu.core.media.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.xiaoyu.core.media.AudioFocusCoordinator
import com.xiaoyu.core.media.PlaybackMode
import com.xiaoyu.core.media.Track
import com.xiaoyu.core.media.queue.MusicQueueManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class MediaPlayerFacade(
    context: Context,
    private val scope: CoroutineScope,
    private val queueManager: MusicQueueManager,
    private val audioFocus: AudioFocusCoordinator? = null,
) {
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                    .setAllowCrossProtocolRedirects(true),
            ),
        )
        .build()

    var onEnded: (() -> Unit)? = null
    var onError: ((PlaybackException) -> Unit)? = null
    var onTrackChanged: ((Track?) -> Unit)? = null
    var urlResolver: (suspend (Track) -> Track?)? = null
    var radioReResolver: (suspend (Track) -> Track?)? = null

    private var currentMode: PlaybackMode = PlaybackMode.SINGLE
    private var lastTrack: Track? = null
    private var radioRetryCount = 0
    private var radioStationName: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    init {
        audioFocus?.onDuck = { scope.launch(Dispatchers.Main) { player.volume = 0.3f } }
        audioFocus?.onUnduck = { scope.launch(Dispatchers.Main) { player.volume = 1f } }
        audioFocus?.onPausePlayback = { scope.launch(Dispatchers.Main) { player.pause() } }
        audioFocus?.onResumePlayback = {
            scope.launch(Dispatchers.Main) {
                player.volume = 1f
                if (queueManager.currentTrack() != null) player.play()
            }
        }

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded?.invoke()
            }

            override fun onPlayerError(error: PlaybackException) {
                onError?.invoke(error)
                if (currentMode == PlaybackMode.RADIO && radioRetryCount < 3 && lastTrack != null) {
                    radioRetryCount++
                    val station = radioStationName ?: lastTrack!!.title
                    Log.w(TAG, "radio error, re-resolve station=$station attempt=$radioRetryCount")
                    scope.launch {
                        val resolved = radioReResolver?.invoke(
                            lastTrack!!.copy(title = station),
                        ) ?: lastTrack
                        if (resolved != null && resolved.url.isNotBlank()) {
                            playTrackInternal(resolved, PlaybackMode.RADIO, stopPrevious = false)
                        }
                    }
                }
            }
        })
    }

    fun playTrack(track: Track, mode: PlaybackMode, stopPrevious: Boolean = true) {
        scope.launch {
            playTrackAwait(track, mode, stopPrevious)
        }
    }

    /** 解析 URL 并开始播放；返回是否进入 READY 状态 */
    suspend fun playTrackAwait(track: Track, mode: PlaybackMode, stopPrevious: Boolean = true): Boolean {
        val resolved = resolveUrl(track) ?: run {
            Log.w(TAG, "playTrack failed: no url for ${track.title}")
            return false
        }
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY && player.playWhenReady) {
                            player.removeListener(this)
                            if (cont.isActive) cont.resume(true)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        player.removeListener(this)
                        if (cont.isActive) cont.resume(false)
                    }
                }
                player.addListener(listener)
                cont.invokeOnCancellation { player.removeListener(listener) }
                playTrackInternal(resolved, mode, stopPrevious)
            }
        }
    }

    private suspend fun resolveUrl(track: Track): Track? {
        if (track.url.isNotBlank()) return track
        return urlResolver?.invoke(track)?.takeIf { it.url.isNotBlank() }
    }

    private fun playTrackInternal(track: Track, mode: PlaybackMode, stopPrevious: Boolean) {
        if (stopPrevious && currentMode == PlaybackMode.RADIO && mode != PlaybackMode.RADIO) {
            player.stop()
        }
        if (stopPrevious && currentMode != PlaybackMode.RADIO && mode == PlaybackMode.RADIO) {
            player.stop()
        }
        currentMode = mode
        lastTrack = track
        if (mode == PlaybackMode.RADIO) {
            radioStationName = track.title
        } else {
            radioStationName = null
            radioRetryCount = 0
        }
        audioFocus?.requestForPlayback()
        player.setMediaItem(MediaItem.fromUri(normalizeStreamUrl(track.url)))
        player.prepare()
        player.volume = 1f
        player.play()
        onTrackChanged?.invoke(track)
        Log.i(TAG, "playing ${track.title} mode=$mode")
    }

    fun playQueue(startIndex: Int = 0) {
        val track = queueManager.tracks.value.getOrNull(startIndex) ?: return
        queueManager.jumpTo(startIndex)
        playTrack(track, queueManager.mode.value)
    }

    fun pause() = runOnMain { player.pause() }

    fun resume() = runOnMain {
        audioFocus?.requestForPlayback()
        player.play()
    }

    fun stop() = runOnMain {
        player.stop()
        audioFocus?.abandonPlaybackFocus()
    }

    fun isPlaying(): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return player.isPlaying
        return kotlinx.coroutines.runBlocking(Dispatchers.Main) { player.isPlaying }
    }
    fun currentMode(): PlaybackMode = currentMode

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun durationMs(): Long = player.duration.coerceAtLeast(0L)
    fun positionMs(): Long = player.currentPosition.coerceAtLeast(0L)

    fun release() {
        audioFocus?.abandonPlaybackFocus()
        player.release()
    }

    companion object {
        private const val TAG = "MediaPlayerFacade"

        /** Kuwo CDN 链接用 $ 代替 =，ExoPlayer 需规范化为标准 query */
        internal fun normalizeStreamUrl(url: String): String {
            if (url.isBlank()) return url
            if ("kuwo.cn" !in url && "$" !in url) return url
            return url.replace('$', '=')
        }
    }
}
