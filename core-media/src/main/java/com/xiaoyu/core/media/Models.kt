package com.xiaoyu.core.media

enum class PlaybackMode {
    SINGLE,
    QUEUE_LOOP,
    RADIO,
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val url: String,
    val durationMs: Long = 0L,
    val coverUrl: String? = null,
    /** TXB 占位项 src，lazy resolve 时传给 play */
    val src: String? = null,
    val mid: String? = null,
)

fun Track.toJson(): org.json.JSONObject = org.json.JSONObject()
    .put("id", id)
    .put("title", title)
    .put("artist", artist)
    .put("url", url)
    .put("duration_ms", durationMs)
    .also { json ->
        coverUrl?.let { json.put("cover_url", it) }
        src?.let { json.put("src", it) }
        mid?.let { json.put("mid", it) }
    }

fun parseTrackFromJson(obj: org.json.JSONObject): Track? {
    val id = obj.optString("id").ifBlank { obj.optString("track_id") }
    val url = obj.optString("url").ifBlank { obj.optString("play_url") }
    if (id.isBlank() && url.isBlank()) return null
    val artist = obj.optString("artist")
        .ifBlank { obj.optString("artists") }
        .ifBlank { obj.optString("singer") }
    return Track(
        id = id.ifBlank { url },
        title = obj.optString("title").ifBlank { obj.optString("name", "未知") },
        artist = artist,
        url = url,
        durationMs = obj.optLong("duration_ms", 0L),
        coverUrl = obj.optString("cover_url")
            .ifBlank { obj.optString("cover") }
            .ifBlank { obj.optString("pic") }
            .ifBlank { null },
        src = obj.optString("src").ifBlank { null },
        mid = obj.optString("mid").ifBlank { null },
    )
}

data class ResolveResult(
    val ok: Boolean,
    val tracks: List<Track> = emptyList(),
    val mode: PlaybackMode = PlaybackMode.SINGLE,
    val queueLabel: String? = null,
    val code: String? = null,
    val message: String? = null,
) {
    companion object {
        fun success(tracks: List<Track>, mode: PlaybackMode, label: String? = null) =
            ResolveResult(ok = true, tracks = tracks, mode = mode, queueLabel = label)

        fun failure(code: String, message: String) =
            ResolveResult(ok = false, code = code, message = message)
    }
}
