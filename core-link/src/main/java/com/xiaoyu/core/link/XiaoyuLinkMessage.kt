package com.xiaoyu.core.link

import org.json.JSONObject

data class XiaoyuLinkMessage(
    val appId: String,
    val capability: String,
    val requestId: String,
    val payload: JSONObject = JSONObject(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("appId", appId)
        .put("capability", capability)
        .put("requestId", requestId)
        .put("payload", payload)

    companion object {
        fun fromJson(json: JSONObject): XiaoyuLinkMessage = XiaoyuLinkMessage(
            appId = json.optString("appId"),
            capability = json.optString("capability"),
            requestId = json.optString("requestId"),
            payload = json.optJSONObject("payload") ?: JSONObject(),
        )
    }
}

object XiaoyuLinkCodec {
    fun encode(message: XiaoyuLinkMessage): String = message.toJson().toString()

    fun decode(raw: String): XiaoyuLinkMessage = fromJson(JSONObject(raw))

    fun fromJson(json: JSONObject): XiaoyuLinkMessage = XiaoyuLinkMessage.fromJson(json)
}
