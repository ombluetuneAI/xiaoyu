package com.xiaoyu.core.voice.ota

import com.xiaoyu.core.voice.identity.DeviceIdentity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class XiaozhiOtaClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val otaUrl: String = DEFAULT_OTA_URL,
) {
    fun checkVersion(
        deviceMac: String,
        clientId: String,
        appVersion: String = "1.0.0",
    ): OtaResponse {
        val body = JSONObject()
            .put("application", JSONObject().put("name", "xiaoyu-assistant").put("version", appVersion))
            .put("board", JSONObject().put("type", "xiaoyu-assistant").put("mac", deviceMac))
            .put("mac_address", deviceMac)
            .put("uuid", clientId)

        val request = Request.Builder()
            .url(otaUrl)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")
            .header("Activation-Version", "1")
            .header("Device-Id", deviceMac)
            .header("Client-Id", clientId)
            .header("User-Agent", "xiaoyu-assistant/$appVersion")
            .header("Accept-Language", "zh-CN")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw OtaException("OTA HTTP ${response.code}")
            }
            val json = JSONObject(response.body?.string() ?: "{}")
            return parseOtaResponse(json)
        }
    }

    private fun parseOtaResponse(json: JSONObject): OtaResponse {
        val wsJson = json.optJSONObject("websocket")
        val websocket = wsJson?.let {
            OtaWebSocket(
                url = it.optString("url"),
                token = it.optString("token"),
                version = it.optString("version").ifBlank { null },
            )
        }
        val actJson = json.optJSONObject("activation")
        val activation = actJson?.let {
            OtaActivation(
                code = it.optString("code"),
                message = it.optString("message", null),
                challenge = it.optString("challenge", null),
            )
        }
        return OtaResponse(websocket = websocket, activation = activation)
    }

    companion object {
        const val DEFAULT_OTA_URL = "https://api.tenclass.net/xiaozhi/ota/"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun validateMac(mac: String) {
            require(DeviceIdentity.isValidMac(mac)) { "Invalid MAC: $mac" }
            require(mac != "02:00:00:00:00:00") { "Placeholder MAC not allowed" }
        }
    }
}

class OtaException(message: String) : Exception(message)
