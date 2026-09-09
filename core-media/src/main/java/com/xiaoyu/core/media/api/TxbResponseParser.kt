package com.xiaoyu.core.media.api

import com.xiaoyu.core.media.Track
import org.json.JSONArray
import org.json.JSONObject

/** TXB API 响应解析（实测字段：name / artists / cover / url / src / id） */
internal object TxbResponseParser {
    fun extractTrackArray(json: JSONObject): JSONArray {
        json.optJSONArray("tracks")?.takeIf { it.length() > 0 }?.let { return it }
        json.optJSONArray("list")?.takeIf { it.length() > 0 }?.let { return it }
        json.optJSONObject("data")?.let { data ->
            data.optJSONArray("list")?.takeIf { it.length() > 0 }?.let { return it }
            data.optJSONArray("tracks")?.takeIf { it.length() > 0 }?.let { return it }
            data.optJSONObject("track")?.let { return JSONArray().put(it) }
        }
        json.optJSONObject("track")?.let { return JSONArray().put(it) }
        json.optJSONArray("data")?.takeIf { it.length() > 0 }?.let { return it }
        return JSONArray()
    }

    fun parseTrack(obj: JSONObject?, allowMissingUrl: Boolean = false): Track? {
        if (obj == null) return null
        val id = obj.str("id").ifBlank { obj.str("track_id") }
        val url = obj.str("url").ifBlank { obj.str("play_url") }
        if (url.isBlank() && id.isBlank()) return null
        if (url.isBlank() && !allowMissingUrl) return null

        val durationRaw = when {
            obj.has("duration_ms") -> obj.optLong("duration_ms", 0L)
            obj.has("duration") -> {
                val d = obj.opt("duration")
                when (d) {
                    is Number -> (d.toLong() * if (d.toLong() < 1000) 1000 else 1)
                    else -> 0L
                }
            }
            else -> 0L
        }

        val artist = obj.str("artist")
            .ifBlank { obj.str("artists") }
            .ifBlank { obj.str("singer") }

        val cover = obj.str("cover_url")
            .ifBlank { obj.str("cover") }
            .ifBlank { obj.str("pic") }
            .ifBlank { null }

        return Track(
            id = id.ifBlank { url },
            title = obj.str("title").ifBlank { obj.str("name").ifBlank { "未知" } },
            artist = artist,
            url = url,
            durationMs = durationRaw,
            coverUrl = cover,
            src = obj.str("src").ifBlank { null },
            mid = obj.str("mid").ifBlank { null },
        )
    }

    private fun JSONObject.str(key: String): String = optString(key, "") ?: ""

    fun parseTracks(array: JSONArray, allowMissingUrl: Boolean = false): List<Track> {
        val list = mutableListOf<Track>()
        for (i in 0 until array.length()) {
            parseTrack(array.optJSONObject(i), allowMissingUrl)?.let { list.add(it) }
        }
        return list
    }
}
