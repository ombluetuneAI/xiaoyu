package com.xiaoyu.core.media.api

/** TXB 音乐播放源：search / playlists 的 src 参数 */
object TxbMusicSource {
    const val DEFAULT = "kw"

    private val ALLOWED = setOf("kw", "kg", "wy", "qq")

    fun normalize(raw: String?): String {
        val v = raw?.trim()?.lowercase().orEmpty()
        return if (v in ALLOWED) v else DEFAULT
    }
}
