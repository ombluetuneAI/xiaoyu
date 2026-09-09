package com.xiaoyu.core.router

enum class MediaSource {
    XIAOYU,
    MUSICFREE,
    NONE,
}

class ActiveMediaSource {
    @Volatile
    var current: MediaSource = MediaSource.NONE
        private set

    @Volatile
    var capability: String? = null
        private set

    @Volatile
    private var lastActiveMs: Long = 0L

    fun set(source: MediaSource, capability: String? = null) {
        current = source
        this.capability = capability
        touch()
    }

    fun touch() {
        lastActiveMs = System.currentTimeMillis()
    }

    fun clear() {
        current = MediaSource.NONE
        capability = null
        lastActiveMs = 0L
    }

    /** 空闲超过 [maxIdleMs]（默认 30min）则重置为 NONE */
    fun clearIfIdle(maxIdleMs: Long = IDLE_RESET_MS): Boolean {
        if (current == MediaSource.NONE) return false
        if (lastActiveMs <= 0L) return false
        if (System.currentTimeMillis() - lastActiveMs > maxIdleMs) {
            clear()
            return true
        }
        return false
    }

    companion object {
        const val IDLE_RESET_MS = 30 * 60 * 1000L
    }
}
