package com.xiaoyu.core.router

import android.util.Log
import org.json.JSONObject

/**
 * 串行化播放入队，并在短时间内忽略重复的 MCP 播放指令（小智常会连发 play_general）。
 */
internal class PlaybackCommandGate(
    private val dedupeWindowMs: Long = 4000L,
) {
    @Volatile
    private var lastSignature: String? = null
    @Volatile
    private var lastAtMs: Long = 0L

    fun shouldSkip(name: String, args: JSONObject): Boolean {
        if (name !in PLAY_TOOL_NAMES) return false
        val now = System.currentTimeMillis()
        if (lastAtMs > 0L && now - lastAtMs < dedupeWindowMs) {
            Log.i(TAG, "skip play within ${dedupeWindowMs}ms: $name (last=$lastSignature)")
            return true
        }
        return false
    }

    fun record(name: String, args: JSONObject) {
        if (name !in PLAY_TOOL_NAMES) return
        lastSignature = signature(name, args)
        lastAtMs = System.currentTimeMillis()
    }

    private fun signature(name: String, args: JSONObject): String {
        val keys = args.keys().asSequence().toList().sorted()
        val body = keys.joinToString(",") { key -> "$key=${args.opt(key)}" }
        return "$name|$body"
    }

    companion object {
        private const val TAG = "PlaybackCommandGate"
        val PLAY_TOOLS = setOf(
            "self.xiaoyu.play_general",
            "self.xiaoyu.play_collection",
            "self.xiaoyu.radio_play",
            "self.xiaoyu.play_musicfree",
        )
        val PLAY_TOOL_NAMES = PLAY_TOOLS + "self.xiaoyu.play_track"
    }
}
