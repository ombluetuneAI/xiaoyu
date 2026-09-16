package com.xiaoyu.core.media.api

import com.xiaoyu.core.media.ResolveResult
import com.xiaoyu.core.media.Track
import com.xiaoyu.core.media.PlaybackMode
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MusicApiClient(
    private val baseUrlProvider: () -> String,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    /** 只拉歌单元数据；播放链接由 [MediaResolver] 按需 lazy resolve，避免 TXB 一次解析 limit 条。 */
    fun fetchPlaylists(limit: Int = 30, src: String = TxbMusicSource.DEFAULT): ResolveResult = try {
        val source = TxbMusicSource.normalize(src)
        val url = "${baseUrlProvider().trimEnd('/')}/music/v1/playlists?limit=$limit&src=${encode(source)}"
        val json = getJson(url)
        val tracks = TxbResponseParser.parseTracks(TxbResponseParser.extractTrackArray(json), allowMissingUrl = true)
        if (tracks.isEmpty()) {
            ResolveResult.failure("no_match", "播放列表为空")
        } else {
            ResolveResult.success(tracks, PlaybackMode.QUEUE_LOOP, "泛播列表")
        }
    } catch (e: Exception) {
        ResolveResult.failure("txb_unreachable", "暂时无法连接音乐服务")
    }

    /** GET /music/v1/search — 返回列表按 API 顺序连播；[src] 默认 kw */
    fun search(
        query: String,
        limit: Int = 30,
        src: String = TxbMusicSource.DEFAULT,
    ): ResolveResult {
        if (query.isBlank()) return ResolveResult.failure("invalid_args", "缺少 query")
        return searchRequest(query, limit, TxbMusicSource.normalize(src))
            ?: ResolveResult.failure("no_match", "暂时找不到这首歌")
    }

    private fun searchRequest(query: String, limit: Int, src: String): ResolveResult? = try {
        val url = buildSearchUrl(query, limit, src)
        val json = getJson(url)
        val tracks = TxbResponseParser.parseTracks(TxbResponseParser.extractTrackArray(json), allowMissingUrl = true)
        if (tracks.isEmpty()) null
        else ResolveResult.success(tracks, PlaybackMode.QUEUE_LOOP, "$query · ${tracks.size} 首")
    } catch (e: Exception) {
        ResolveResult.failure("txb_unreachable", "暂时无法连接音乐服务")
    }

    internal fun buildSearchUrl(query: String, limit: Int, src: String): String {
        val base = baseUrlProvider().trimEnd('/')
        return "$base/music/v1/search?keyword=${encode(query)}&limit=$limit&src=${encode(TxbMusicSource.normalize(src))}"
    }

    /** lazy resolve：统一 play 参数 keyword={name-artists} + 可选 id/mid/src + source=auto */
    fun resolvePlayUrl(track: Track): Track? {
        if (track.url.isNotBlank()) return track
        return try {
            val url = TxbPlayQuery.build(baseUrlProvider(), track)
            requestPlayTrack(url)
        } catch (_: Exception) {
            null
        }
    }

    private fun requestPlayTrack(url: String): Track? =
        parsePlayResponse(getJson(url))?.takeIf { it.url.isNotBlank() }

    private fun parsePlayResponse(json: JSONObject): Track? = parseTrack(
        json.optJSONObject("data")?.optJSONObject("track")
            ?: json.optJSONObject("track")
            ?: json.optJSONObject("data")
            ?: json,
    )

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            return JSONObject(resp.body?.string() ?: "{}")
        }
    }

    /** TXB 实测格式：{"data":{"list":[…]}} 或 {"tracks":[…]} 或 {"data":[…]} */
    private fun extractTrackArray(json: JSONObject): JSONArray =
        TxbResponseParser.extractTrackArray(json)

    private fun parseTrack(obj: JSONObject?, allowMissingUrl: Boolean = false): Track? =
        TxbResponseParser.parseTrack(obj, allowMissingUrl)

    private fun parseTracks(array: JSONArray, allowMissingUrl: Boolean = false): List<Track> =
        TxbResponseParser.parseTracks(array, allowMissingUrl)

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}

class RadioApiClient(
    private val baseUrlProvider: () -> String,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    fun play(name: String?): ResolveResult {
        if (name.isNullOrBlank()) return playRandomWithFallback()
        return playPath("play?name=${encode(name)}", name)
    }

    private fun playRandomWithFallback(): ResolveResult {
        val random = playPath("random", null)
        if (random.ok) return random
        if (random.code == "no_match" || random.code == "txb_unreachable") {
            return playFavorites()
        }
        return random
    }

    private fun playFavorites(): ResolveResult {
        return try {
            val url = "${baseUrlProvider().trimEnd('/')}/radio/v1/favorites"
            val json = getJson(url)
            val favorites = json.optJSONArray("favorites") ?: json.optJSONArray("data") ?: JSONArray()
            if (favorites.length() == 0) {
                return ResolveResult.failure("no_match", "找不到电台")
            }
            val pick = favorites.optJSONObject(0)
            val stationName = pick?.optString("name") ?: pick?.optString("title")
            if (!stationName.isNullOrBlank()) {
                playPath("play?name=${encode(stationName)}", stationName)
            } else {
                parseStreamResponse(json, "音乐电台")
            }
        } catch (e: Exception) {
            ResolveResult.failure("txb_unreachable", "暂时无法连接电台服务")
        }
    }

    private fun playPath(path: String, displayName: String?): ResolveResult {
        return try {
            val url = "${baseUrlProvider().trimEnd('/')}/radio/v1/$path"
            val json = getJson(url)
            parseStreamResponse(json, displayName ?: "音乐电台")
        } catch (e: Exception) {
            if (e.message?.contains("HTTP 404") == true) {
                ResolveResult.failure("no_match", "找不到电台")
            } else {
                ResolveResult.failure("txb_unreachable", "暂时无法连接电台服务")
            }
        }
    }

    private fun parseStreamResponse(json: JSONObject, defaultName: String): ResolveResult {
        val streamUrl = json.optString("url").ifBlank {
            json.optJSONObject("track")?.optString("url") ?: ""
        }
        if (streamUrl.isBlank()) {
            return ResolveResult.failure("no_match", "找不到电台")
        }
        val track = Track(
            id = json.optString("id", streamUrl),
            title = json.optString("name", defaultName),
            artist = "TXB Radio",
            url = streamUrl,
        )
        return ResolveResult.success(listOf(track), PlaybackMode.RADIO, track.title)
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            return JSONObject(resp.body?.string() ?: "{}")
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
