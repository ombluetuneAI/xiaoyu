package com.xiaoyu.core.voice.bind

import android.util.Log
import com.xiaoyu.core.voice.ota.OtaResponse
import com.xiaoyu.core.voice.ota.XiaozhiBindState
import com.xiaoyu.core.voice.ota.XiaozhiOtaClient
import com.xiaoyu.core.voice.ota.parseOtaBindState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class XiaozhiBindManager(
    private val otaClient: XiaozhiOtaClient,
    private val deviceMacProvider: () -> String,
    private val clientIdProvider: () -> String,
) {
    private val _state = MutableStateFlow(XiaozhiBindState.UNKNOWN)
    val state: StateFlow<XiaozhiBindState> = _state.asStateFlow()

    private val _activationCode = MutableStateFlow<String?>(null)
    val activationCode: StateFlow<String?> = _activationCode.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var lastResponse: OtaResponse? = null

    fun refresh(): XiaozhiBindState {
        return try {
            val mac = deviceMacProvider()
            XiaozhiOtaClient.validateMac(mac)
            val resp = otaClient.checkVersion(mac, clientIdProvider())
            lastResponse = resp
            val bindState = parseOtaBindState(resp)
            _state.value = bindState
            _activationCode.value = resp.activation?.code
            _lastError.value = null
            bindState
        } catch (e: Exception) {
            Log.w(TAG, "OTA refresh failed: ${e.message}", e)
            _lastError.value = e.message ?: "无法连接小智服务"
            _state.value = XiaozhiBindState.UNKNOWN
            XiaozhiBindState.UNKNOWN
        }
    }

    fun isVoiceAllowed(): Boolean = _state.value == XiaozhiBindState.BOUND

    fun websocketConfig(): OtaResponse? = lastResponse

    companion object {
        private const val TAG = "XiaozhiBindManager"
    }
}
