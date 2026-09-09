package com.xiaoyu.core.voice.ota

data class OtaActivation(
    val code: String,
    val message: String? = null,
    val challenge: String? = null,
)

data class OtaWebSocket(
    val url: String,
    val token: String,
    val version: String? = null,
)

data class OtaResponse(
    val websocket: OtaWebSocket?,
    val activation: OtaActivation?,
)

enum class XiaozhiBindState {
    UNKNOWN,
    NEEDS_ACTIVATION,
    BOUND,
}

fun parseOtaBindState(response: OtaResponse): XiaozhiBindState {
    if (response.activation != null) return XiaozhiBindState.NEEDS_ACTIVATION
    if (!response.websocket?.token.isNullOrBlank()) return XiaozhiBindState.BOUND
    return XiaozhiBindState.UNKNOWN
}
