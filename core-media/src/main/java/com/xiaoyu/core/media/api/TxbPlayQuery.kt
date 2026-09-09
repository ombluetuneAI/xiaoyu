package com.xiaoyu.core.media.api

import com.xiaoyu.core.media.Track
import java.net.URLEncoder

/** TXB GET /music/v1/play 查询参数：keyword={name-artists} + 可选 id/mid/src + source=auto */
internal object TxbPlayQuery {
    fun build(
        baseUrl: String,
        title: String = "",
        artist: String = "",
        id: String? = null,
        mid: String? = null,
        src: String? = null,
        keywordOverride: String? = null,
    ): String {
        val params = mutableListOf<String>()

        val keyword = keywordOverride?.trim().orEmpty().ifBlank { buildKeyword(title, artist) }
        require(keyword.isNotBlank()) { "play query requires keyword" }
        params.add("keyword=${encode(keyword)}")

        id?.trim()?.takeIf { it.isNotBlank() && !looksLikeUrl(it) }
            ?.let { params.add("id=${encode(it)}") }
        mid?.trim()?.takeIf { it.isNotBlank() }
            ?.let { params.add("mid=${encode(it)}") }
        src?.trim()?.takeIf { it.isNotBlank() }
            ?.let { params.add("src=${encode(it)}") }

        params.add("source=auto")
        return "${baseUrl.trimEnd('/')}/music/v1/play?${params.joinToString("&")}"
    }

    fun build(baseUrl: String, track: Track): String = build(
        baseUrl = baseUrl,
        title = track.title,
        artist = track.artist,
        id = track.id,
        mid = track.mid,
        src = track.src,
    )

    fun buildKeyword(title: String, artist: String): String {
        val name = title.trim()
        val artists = artist.trim()
        return when {
            name.isNotBlank() && artists.isNotBlank() -> "$name-$artists"
            name.isNotBlank() -> name
            artists.isNotBlank() -> artists
            else -> ""
        }
    }

    private fun looksLikeUrl(value: String): Boolean {
        val v = value.trim()
        return v.startsWith("http://", ignoreCase = true) ||
            v.startsWith("https://", ignoreCase = true)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
