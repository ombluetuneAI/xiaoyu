package com.xiaoyu.core.media.queue

import com.xiaoyu.core.media.PlaybackMode
import com.xiaoyu.core.media.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicQueueManager {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _mode = MutableStateFlow(PlaybackMode.SINGLE)
    val mode: StateFlow<PlaybackMode> = _mode.asStateFlow()

    private val _queueLabel = MutableStateFlow<String?>(null)
    val queueLabel: StateFlow<String?> = _queueLabel.asStateFlow()

    private val _nowPlaying = MutableStateFlow<Track?>(null)
    val nowPlaying: StateFlow<Track?> = _nowPlaying.asStateFlow()

    fun setQueue(tracks: List<Track>, mode: PlaybackMode, label: String? = null) {
        _tracks.value = tracks
        _currentIndex.value = 0
        _mode.value = mode
        _queueLabel.value = label
        syncNowPlaying()
    }

    fun currentTrack(): Track? = _tracks.value.getOrNull(_currentIndex.value)

    /** 从当前位置向后看第 [offset] 首（1=下一首），不修改 index。 */
    fun peekAtOffset(offset: Int): Track? {
        if (offset <= 0) return currentTrack()
        val list = _tracks.value
        if (list.isEmpty() || _mode.value != PlaybackMode.QUEUE_LOOP) return null
        val index = (_currentIndex.value + offset) % list.size
        return list[index]
    }

    fun jumpTo(index: Int) {
        if (index in _tracks.value.indices) {
            _currentIndex.value = index
            syncNowPlaying()
        }
    }

    fun next(): Track? {
        val list = _tracks.value
        if (list.isEmpty()) return null
        val track = when (_mode.value) {
            PlaybackMode.SINGLE -> null
            PlaybackMode.QUEUE_LOOP -> {
                val next = (_currentIndex.value + 1) % list.size
                _currentIndex.value = next
                list[next]
            }
            PlaybackMode.RADIO -> list.firstOrNull()
        }
        syncNowPlaying()
        return track
    }

    fun previous(): Track? {
        val list = _tracks.value
        if (list.isEmpty()) return null
        if (_mode.value != PlaybackMode.QUEUE_LOOP) {
            syncNowPlaying()
            return currentTrack()
        }
        val prev = if (_currentIndex.value == 0) list.size - 1 else _currentIndex.value - 1
        _currentIndex.value = prev
        syncNowPlaying()
        return list[prev]
    }

    private fun syncNowPlaying() {
        _nowPlaying.value = currentTrack()
    }
}
