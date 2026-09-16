package com.xiaoyu.core.mcp

import org.json.JSONArray
import org.json.JSONObject

data class McpToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JSONObject,
)

object McpToolCatalog {
    val TOOL_DEFINITIONS: List<McpToolDefinition> = listOf(
        McpToolDefinition(
            name = "self.xiaoyu.play_general",
            description = "随便放一首或播放推荐歌单（TXB playlists）；可选 limit、播放源 src",
            inputSchema = objSchema(
                "limit" to intProp("返回曲目数量上限，默认 30"),
                "src" to strProp("播放源：kw 酷我(默认)、kg 酷狗、wy 网易云、qq QQ音乐"),
            ),
        ),
        McpToolDefinition(
            name = "self.xiaoyu.play_collection",
            description = "按主题、歌手或歌单名搜索（TXB search）并依列表顺序连播",
            inputSchema = objSchema(
                "query" to strProp("搜索关键词，如「周杰伦的歌」", required = true),
                "limit" to intProp("候选曲目数量，默认 30"),
                "src" to strProp("播放源：kw(默认)、kg、wy、qq"),
            ),
        ),
        McpToolDefinition(
            name = "self.xiaoyu.play_track",
            description = "播放指定歌曲：调用 TXB search 返回列表，按列表顺序连播；也可传 url/track 直链",
            inputSchema = objSchema(
                "keyword" to strProp("歌曲名或「歌手 歌名」"),
                "title" to strProp("歌曲标题（keyword 别名）"),
                "limit" to intProp("search 列表条数，默认 30"),
                "src" to strProp("播放源：kw(默认)、kg、wy、qq"),
                "url" to strProp("可直接播放的音频 URL"),
                "track" to objProp("完整曲目对象：id/title/artist/url 等"),
            ),
        ),
        McpToolDefinition(
            name = "self.xiaoyu.radio_play",
            description = "播放电台；可指定电台名或随机换台",
            inputSchema = objSchema(
                "name" to strProp("电台名称"),
                "random" to boolProp("为 true 时随机电台"),
            ),
        ),
        McpToolDefinition(
            name = "self.xiaoyu.play_musicfree",
            description = "通过 MusicFree 应用播放，参数为搜索词",
            inputSchema = objSchema(
                "query" to strProp("搜索关键词", required = true),
            ),
        ),
        McpToolDefinition(
            name = "self.xiaoyu.pause",
            description = "暂停当前活跃播放器（内置或 MusicFree）",
            inputSchema = emptyObjectSchema(),
        ),
        McpToolDefinition(
            name = "self.xiaoyu.resume",
            description = "继续播放当前活跃播放器",
            inputSchema = emptyObjectSchema(),
        ),
        McpToolDefinition(
            name = "self.xiaoyu.next",
            description = "下一首",
            inputSchema = emptyObjectSchema(),
        ),
        McpToolDefinition(
            name = "self.xiaoyu.previous",
            description = "上一首",
            inputSchema = emptyObjectSchema(),
        ),
        McpToolDefinition(
            name = "self.xiaoyu.now_playing",
            description = "返回当前正在播放的曲目 JSON",
            inputSchema = emptyObjectSchema(),
        ),
        McpToolDefinition(
            name = "self.xiaoyu.volume_up",
            description = "调高系统媒体音量",
            inputSchema = objSchema(
                "step" to intProp("增幅百分比，默认 10"),
            ),
        ),
        McpToolDefinition(
            name = "self.xiaoyu.volume_down",
            description = "调低系统媒体音量",
            inputSchema = objSchema(
                "step" to intProp("减幅百分比，默认 10"),
            ),
        ),
        McpToolDefinition(
            name = "self.xiaoyu.volume_set",
            description = "将系统媒体音量设为指定百分比",
            inputSchema = objSchema(
                "level" to intProp("目标音量 0–100"),
                required = listOf("level"),
            ),
        ),
        McpToolDefinition(
            name = "self.xiaoyu.volume_mute",
            description = "静音或取消静音",
            inputSchema = objSchema(
                "mute" to boolProp("true 静音，false 取消静音；默认 true"),
            ),
        ),
        McpToolDefinition(
            name = "self.xiaoyu.volume_get",
            description = "查询当前系统媒体音量百分比",
            inputSchema = emptyObjectSchema(),
        ),
        McpToolDefinition(
            name = "self.xiaoyu.link_dispatch",
            description = "高级：投递完整 XiaoyuLink JSON 到已注册第三方 App",
            inputSchema = objSchema(
                "appId" to strProp("目标 appId", required = true),
                "capability" to strProp("能力名，如 music.play", required = true),
                "requestId" to strProp("请求 ID"),
                "payload" to objProp("附加参数 JSON 对象"),
            ),
        ),
    )

    val TOOL_NAMES: List<String> = TOOL_DEFINITIONS.map { it.name }

    fun buildToolsJsonArray(): JSONArray {
        val tools = JSONArray()
        TOOL_DEFINITIONS.forEach { def ->
            tools.put(
                JSONObject()
                    .put("name", def.name)
                    .put("description", def.description)
                    .put("inputSchema", def.inputSchema),
            )
        }
        return tools
    }

    private fun emptyObjectSchema(): JSONObject = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject())

    private fun objSchema(vararg props: Pair<String, JSONObject>, required: List<String> = emptyList()): JSONObject {
        val properties = JSONObject()
        props.forEach { (key, schema) -> properties.put(key, schema) }
        val schema = JSONObject()
            .put("type", "object")
            .put("properties", properties)
        if (required.isNotEmpty()) {
            schema.put("required", JSONArray(required))
        } else {
            val req = props.filter { it.second.optBoolean("_required") }.map { it.first }
            if (req.isNotEmpty()) schema.put("required", JSONArray(req))
        }
        return schema
    }

    private fun strProp(description: String, required: Boolean = false): JSONObject =
        JSONObject().put("type", "string").put("description", description)
            .also { if (required) it.put("_required", true) }

    private fun intProp(description: String): JSONObject =
        JSONObject().put("type", "integer").put("description", description)

    private fun boolProp(description: String): JSONObject =
        JSONObject().put("type", "boolean").put("description", description)

    private fun objProp(description: String): JSONObject =
        JSONObject().put("type", "object").put("description", description)
}
