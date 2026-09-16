package com.xiaoyu.core.router



import android.util.Log

import com.xiaoyu.core.link.XiaoyuLinkMessage

import com.xiaoyu.core.media.ResolveResult

import com.xiaoyu.core.media.MediaResolver

import com.xiaoyu.core.media.PlaybackMode

import com.xiaoyu.core.media.Track

import com.xiaoyu.core.media.VolumeController

import com.xiaoyu.core.media.VolumeState

import com.xiaoyu.core.media.api.TxbMusicSource

import com.xiaoyu.core.media.parseTrackFromJson

import com.xiaoyu.core.media.player.MediaPlayerFacade

import com.xiaoyu.core.media.queue.MusicQueueManager

import com.xiaoyu.core.media.toJson

import com.xiaoyu.core.registry.AppAdapterRegistry

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.sync.Mutex

import kotlinx.coroutines.sync.withLock

import kotlinx.coroutines.withContext

import org.json.JSONObject

import java.util.UUID



class MediaCommandHandler(

    private val mediaResolver: MediaResolver,

    private val queueManager: MusicQueueManager,

    private val playerFacade: MediaPlayerFacade,

    private val activeMediaSource: ActiveMediaSource,

    private val linkDispatcher: LinkDispatcher,

    private val registry: AppAdapterRegistry,

    private val volumeController: VolumeController,

    var onPlaybackError: ((String) -> Unit)? = null,

    var onPlaybackStarted: (() -> Unit)? = null,

) {

    private val playbackGate = PlaybackCommandGate()

    private val playbackMutex = Mutex()



    suspend fun handleTool(name: String, arguments: JSONObject): JSONObject = withContext(Dispatchers.IO) {

        Log.i(TAG, "handleTool name=$name args=$arguments")

        val isPlayTool = name in PlaybackCommandGate.PLAY_TOOL_NAMES

        if (isPlayTool && playbackGate.shouldSkip(name, arguments)) {

            return@withContext duplicatePlayOk()

        }

        if (isPlayTool) {

            playbackMutex.withLock {

                if (playbackGate.shouldSkip(name, arguments)) {

                    return@withLock duplicatePlayOk()

                }

                val result = dispatchTool(name, arguments)

                if (!result.optBoolean("isError")) {

                    playbackGate.record(name, arguments)

                }

                result

            }

        } else {

            dispatchTool(name, arguments)

        }

    }



    private suspend fun dispatchTool(name: String, arguments: JSONObject): JSONObject = when (name) {

        "self.xiaoyu.play_general" -> playResolve(
            mediaResolver.resolveGeneral(
                arguments.optInt("limit", 30),
                TxbMusicSource.normalize(arguments.optString("src")),
            ),
        )

        "self.xiaoyu.play_collection" -> {

            val query = arguments.optString("query")

            playResolve(
                mediaResolver.resolveCollection(
                    query,
                    arguments.optInt("limit", 30),
                    TxbMusicSource.normalize(arguments.optString("src")),
                ),
            )

        }

        "self.xiaoyu.play_track" -> playTrack(arguments)

        "self.xiaoyu.radio_play" -> {

            val result = if (arguments.optBoolean("random")) mediaResolver.resolveRadioRandom()

            else mediaResolver.resolveRadio(arguments.optString("name").ifBlank { null })

            playResolve(result, capability = "radio.play")

        }

        "self.xiaoyu.play_musicfree" -> dispatchThirdPartyPlay("musicfree", arguments.optString("query"))

        "self.xiaoyu.pause" -> routePlaybackControl("pause", "music.pause")

        "self.xiaoyu.resume" -> routePlaybackControl("resume", "music.resume")

        "self.xiaoyu.next" -> routePlaybackControl("next", "music.next")

        "self.xiaoyu.previous" -> routePlaybackControl("previous", "music.previous")

        "self.xiaoyu.now_playing" -> nowPlaying()

        "self.xiaoyu.volume_up" -> volumeUp(arguments)

        "self.xiaoyu.volume_down" -> volumeDown(arguments)

        "self.xiaoyu.volume_set" -> volumeSet(arguments)

        "self.xiaoyu.volume_mute" -> volumeMute(arguments)

        "self.xiaoyu.volume_get" -> volumeGet()

        "self.xiaoyu.link_dispatch" -> {

            val msg = XiaoyuLinkMessage.fromJson(arguments)

            val adapter = registry.getAdapter(msg.appId)

            when {

                adapter == null -> fail("invalid_args", "应用未注册")

                !adapter.userEnabled -> fail("voice_disabled", "该 App 未授权")

                else -> linkResult(adapter.packageName, msg)

            }

        }

        else -> fail("invalid_args", "未知工具 $name")

    }



    private fun duplicatePlayOk(): JSONObject {

        val track = queueManager.currentTrack()

        return if (track != null) ok("已在播放 ${track.title}", track) else ok("已在处理播放")

    }



    private suspend fun playTrack(arguments: JSONObject): JSONObject {

        val trackObj = arguments.optJSONObject("track")

        if (trackObj != null) {

            val track = parseTrackFromJson(trackObj)

                ?: return fail("invalid_args", "track 对象无效")

            return playDirectTrack(track)

        }

        val url = arguments.optString("url")

        if (url.isNotBlank()) {

            val track = Track(

                id = arguments.optString("id", url),

                title = arguments.optString("title", arguments.optString("keyword", "未知")),

                artist = arguments.optString("artist", ""),

                url = url,

                coverUrl = arguments.optString("cover_url").ifBlank { null },

            )

            return playDirectTrack(track)

        }

        val keyword = arguments.optString("keyword").ifBlank { arguments.optString("title") }

        if (keyword.isBlank()) return fail("invalid_args", "缺少 keyword")

        return playResolve(
            mediaResolver.resolveTrack(
                keyword,
                arguments.optInt("limit", 30),
                TxbMusicSource.normalize(arguments.optString("src")),
            ),
        )

    }



    private suspend fun playDirectTrack(track: Track): JSONObject {

        val resolved = mediaResolver.resolveTrackUrl(track) ?: track

        if (resolved.url.isBlank()) return fail("no_match", "无法获取播放地址")

        queueManager.setQueue(listOf(resolved), PlaybackMode.SINGLE, resolved.title)

        if (!playerFacade.playTrackAwait(resolved, PlaybackMode.SINGLE)) {

            return fail("playback_error", "播放失败")

        }

        activeMediaSource.set(MediaSource.XIAOYU, "music.play")

        onPlaybackStarted?.invoke()

        return ok("正在播放 ${resolved.title}", resolved)

    }



    private suspend fun dispatchThirdPartyPlay(appId: String, query: String): JSONObject {

        if (query.isBlank()) return fail("invalid_args", "缺少 query")

        val adapter = registry.getAdapter(appId) ?: return fail("invalid_args", "应用未注册")

        if (!adapter.userEnabled) return fail("voice_disabled", "该 App 未授权")

        val msg = XiaoyuLinkMessage(

            appId = appId,

            capability = "music.play",

            requestId = UUID.randomUUID().toString(),

            payload = JSONObject().put("query", query),

        )

        val payload = linkDispatcher.dispatch(adapter.packageName, msg)

        if (payload.optBoolean("ok")) {

            activeMediaSource.set(MediaSource.MUSICFREE, "music.play")

        }

        return linkMcpResult(payload)

    }



    private suspend fun routePlaybackControl(action: String, thirdPartyCapability: String): JSONObject {

        activeMediaSource.touch()

        return when (activeMediaSource.current) {

            MediaSource.MUSICFREE -> {

                val adapter = registry.getAdapter("musicfree")

                    ?: return fail("invalid_args", "MusicFree 未注册")

                val msg = XiaoyuLinkMessage(

                    appId = "musicfree",

                    capability = thirdPartyCapability,

                    requestId = UUID.randomUUID().toString(),

                )

                linkMcpResult(linkDispatcher.dispatch(adapter.packageName, msg))

            }

            MediaSource.XIAOYU -> when (action) {

                "pause" -> ok("已暂停").also { playerFacade.pause() }

                "resume" -> ok("继续播放").also { playerFacade.resume() }

                "next" -> playAdjacentTrack(queueManager.next(), "下一首")

                "previous" -> playAdjacentTrack(queueManager.previous(), "上一首")

                else -> fail("invalid_args", "未知播控")

            }

            MediaSource.NONE -> fail("no_match", "当前没有播放")

        }

    }



    private suspend fun playAdjacentTrack(track: Track?, label: String): JSONObject {

        if (track == null) {

            return ok(if (label == "下一首") "已是最后一首" else "已是第一首")

        }

        val resolved = mediaResolver.resolveTrackUrl(track) ?: track

        if (resolved.url.isBlank()) return fail("no_match", "${label}无法播放")

        if (!playerFacade.playTrackAwait(resolved, queueManager.mode.value)) {

            return fail("playback_error", "播放失败")

        }

        return ok("$label：${resolved.title}", resolved)

    }



    private suspend fun nowPlaying(): JSONObject {

        if (activeMediaSource.current == MediaSource.MUSICFREE) {

            val adapter = registry.getAdapter("musicfree") ?: return fail("invalid_args", "MusicFree 未注册")

            val msg = XiaoyuLinkMessage(

                appId = "musicfree",

                capability = "music.now_playing",

                requestId = UUID.randomUUID().toString(),

            )

            return linkMcpResult(linkDispatcher.dispatch(adapter.packageName, msg))

        }

        val track = queueManager.currentTrack()

        if (track == null) return fail("no_match", "当前没有播放")

        return ok("正在播放 ${track.title} - ${track.artist}", track)

    }



    private fun volumeUp(arguments: JSONObject): JSONObject {

        val step = arguments.optInt("step", 10).coerceIn(1, 100)

        val state = volumeController.adjustPercent(step)

        return ok(volumeMessage(state))

    }



    private fun volumeDown(arguments: JSONObject): JSONObject {

        val step = arguments.optInt("step", 10).coerceIn(1, 100)

        val state = volumeController.adjustPercent(-step)

        return ok(volumeMessage(state))

    }



    private fun volumeSet(arguments: JSONObject): JSONObject {

        if (!arguments.has("level")) return fail("invalid_args", "缺少 level")

        val level = arguments.optInt("level").coerceIn(0, 100)

        val state = volumeController.setPercent(level)

        return ok(volumeMessage(state))

    }



    private fun volumeMute(arguments: JSONObject): JSONObject {

        val mute = if (arguments.has("mute")) arguments.optBoolean("mute") else true

        val state = volumeController.setMuted(mute)

        return ok(if (state.muted) "已静音" else volumeMessage(state))

    }



    private fun volumeGet(): JSONObject {

        val state = volumeController.currentState()

        return ok(if (state.muted) "当前已静音" else "当前音量 ${state.percent}%")

    }



    private fun volumeMessage(state: VolumeState): String =

        "音量已调至 ${state.percent}%"



    private suspend fun linkResult(targetPackage: String, msg: XiaoyuLinkMessage): JSONObject {

        return linkMcpResult(linkDispatcher.dispatch(targetPackage, msg))

    }



    private suspend fun playResolve(result: ResolveResult, capability: String = "music.play"): JSONObject {

        if (!result.ok || result.tracks.isEmpty()) {

            val code = result.code ?: "no_match"

            val message = result.message ?: "播放失败"

            if (code == "txb_unreachable") onPlaybackError?.invoke(message)

            return fail(code, message)

        }

        Log.i(TAG, "playResolve: label=${result.queueLabel} tracks=${result.tracks.size} first=${result.tracks.first().title}")

        queueManager.setQueue(result.tracks, result.mode, result.queueLabel)

        val first = result.tracks.first()

        if (!playerFacade.playTrackAwait(first, result.mode)) {

            return fail("playback_error", "播放失败")

        }

        activeMediaSource.set(MediaSource.XIAOYU, capability)

        onPlaybackStarted?.invoke()

        return ok("正在播放 ${first.title}", first)

    }



    private fun linkMcpResult(payload: JSONObject): JSONObject {

        val ok = payload.optBoolean("ok")

        val message = payload.optString("message", if (ok) "已执行" else "执行失败")

        return if (ok) ok(message) else fail(payload.optString("code", "error"), message)

    }



    private fun ok(message: String, track: Track? = null): JSONObject = mcpResult(true, message, track = track)

    private fun fail(code: String, message: String): JSONObject = mcpResult(false, message, code)



    private fun mcpResult(ok: Boolean, message: String, code: String? = null, track: Track? = null): JSONObject {

        val inner = JSONObject().put("ok", ok).put("message", message)

        if (code != null) inner.put("code", code)

        track?.let { inner.put("track", it.toJson()) }

        return JSONObject()

            .put("content", org.json.JSONArray().put(JSONObject().put("type", "text").put("text", inner.toString())))

            .put("isError", !ok)

    }



    companion object {

        private const val TAG = "MediaCommandHandler"

    }

}



class CommandRouter(

    private val registry: AppAdapterRegistry,

    private val linkDispatcher: LinkDispatcher,

) {

    suspend fun dispatch(message: XiaoyuLinkMessage): JSONObject {

        if (message.appId == "xiaoyu") {

            return JSONObject().put("ok", false).put("code", "USE_PLAY_TOOLS")

        }

        val adapter = registry.getAdapter(message.appId)

            ?: return failJson("APP_NOT_REGISTERED", "应用未注册")

        if (!adapter.userEnabled) return failJson("VOICE_DISABLED", "该 App 未授权")

        if (!adapter.capabilities.contains(message.capability)) {

            return failJson("CAPABILITY_NOT_SUPPORTED", "不支持的能力")

        }

        return linkDispatcher.dispatch(adapter.packageName, message)

    }



    private fun failJson(code: String, message: String): JSONObject =

        JSONObject().put("ok", false).put("code", code).put("message", message)

}



interface LinkDispatcher {

    suspend fun dispatch(targetPackage: String, message: XiaoyuLinkMessage): JSONObject

}



class BroadcastLinkDispatcherAdapter(

    private val dispatcher: com.xiaoyu.core.link.BroadcastLinkDispatcher,

) : LinkDispatcher {

    override suspend fun dispatch(targetPackage: String, message: XiaoyuLinkMessage): JSONObject {

        return dispatcher.dispatch(targetPackage, message)

    }

}


