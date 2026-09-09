package com.xiaoyu.core.voice.client

import android.util.Log
import com.xiaoyu.core.mcp.McpToolCatalog
import com.xiaoyu.core.voice.audio.OpusVoicePipeline
import com.xiaoyu.core.wake.WakeWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class XiaozhiVoiceClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        // 小智 WS 不响应 OkHttp ping/pong，30s ping 会导致每 2 分钟误判断连
        .pingInterval(0, TimeUnit.SECONDS)
        .build(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private val connected = AtomicBoolean(false)
    private val protocolVersion = AtomicInteger(1)
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    private var lastUrl: String? = null
    private var lastToken: String? = null
    private var lastDeviceMac: String? = null
    private var lastClientId: String? = null
    private var autoReconnect = true
    private val listening = AtomicBoolean(false)

    @Volatile
    private var acceptServerTts = true

    val audioPipeline = OpusVoicePipeline()

    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<VoiceEvent> = _events.asSharedFlow()

    var mcpHandler: ((JSONObject) -> JSONObject)? = null
    var onTtsStart: (() -> Unit)? = null
    var onTtsComplete: (() -> Unit)? = null

    fun connect(url: String, token: String, deviceMac: String, clientId: String, wsProtocolVersion: Int = 1) {
        disconnect(manual = false)
        lastUrl = url
        lastToken = token
        lastDeviceMac = deviceMac
        lastClientId = clientId
        protocolVersion.set(wsProtocolVersion)
        autoReconnect = true
        reconnectAttempt = 0
        openSocket(url, token, deviceMac, clientId, wsProtocolVersion)
    }

    private fun openSocket(url: String, token: String, deviceMac: String, clientId: String, wsProtocolVersion: Int) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Device-Id", deviceMac)
            .header("Client-Id", clientId)
            .header("Protocol-Version", wsProtocolVersion.toString())
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected.set(true)
                reconnectAttempt = 0
                audioPipeline.attach(webSocket)
                sendHello()
                _events.tryEmit(VoiceEvent.Connected)
                Log.i(TAG, "WS connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (!listening.get()) {
                    if (!acceptServerTts) return
                    audioPipeline.playDownlink(bytes.toByteArray())
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onDisconnected(reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _events.tryEmit(VoiceEvent.Error(t.message ?: "ws failure"))
                onDisconnected(t.message ?: "ws failure")
            }
        })
    }

    private fun onDisconnected(reason: String) {
        connected.set(false)
        listening.set(false)
        audioPipeline.detach()
        _events.tryEmit(VoiceEvent.Disconnected(reason))
        Log.w(TAG, "WS disconnected: $reason")
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (!autoReconnect) return
        val url = lastUrl ?: return
        val token = lastToken ?: return
        val mac = lastDeviceMac ?: return
        val clientId = lastClientId ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = (1000L * (1 shl reconnectAttempt.coerceAtMost(4))).coerceAtMost(30000L)
            delay(delayMs)
            if (!isActive || connected.get()) return@launch
            reconnectAttempt++
            openSocket(url, token, mac, clientId, protocolVersion.get())
        }
    }

    fun disconnect(manual: Boolean = true) {
        if (manual) autoReconnect = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "bye")
        webSocket = null
        connected.set(false)
        listening.set(false)
        audioPipeline.detach()
    }

    fun isConnected(): Boolean = connected.get()
    fun isListening(): Boolean = listening.get()

    fun sendHello() {
        val hello = JSONObject()
            .put("type", "hello")
            .put("version", 1)
            .put("features", JSONObject().put("mcp", true))
            .put("transport", "websocket")
            .put("audio_params", JSONObject()
                .put("format", "opus")
                .put("sample_rate", 16000)
                .put("channels", 1)
                .put("frame_duration", 60))
        sendRaw(hello)
    }

    fun startListening(fromWake: Boolean = false, wakeWord: String = DEFAULT_WAKE_WORD) {
        if (!connected.get()) {
            Log.w(TAG, "startListening skipped: WS not connected")
            return
        }
        acceptServerTts = true
        if (fromWake) {
            sendRaw(
                JSONObject()
                    .put("session_id", "")
                    .put("type", "listen")
                    .put("state", "detect")
                    .put("text", wakeWord),
            )
        }
        sendRaw(
            JSONObject()
                .put("session_id", "")
                .put("type", "listen")
                .put("state", "start")
                .put("mode", "auto"),
        )
        audioPipeline.startCapture()
        listening.set(true)
        _events.tryEmit(VoiceEvent.ListeningStarted)
        Log.i(TAG, "listen/start mode=auto fromWake=$fromWake wakeWord=$wakeWord")
    }

    fun stopListening() {
        if (!listening.getAndSet(false)) {
            audioPipeline.stopCapture()
            return
        }
        audioPipeline.stopCapture()
        if (connected.get()) {
            sendRaw(
                JSONObject()
                    .put("session_id", "")
                    .put("type", "listen")
                    .put("state", "stop"),
            )
        }
        _events.tryEmit(VoiceEvent.ListeningStopped)
        Log.i(TAG, "listen/stop")
    }

    /** 空闲/手动退出：abort 会话并丢弃后续服务端 TTS，避免「再见」类播报 */
    fun endSessionQuietly(reason: String = "user_idle") {
        acceptServerTts = false
        audioPipeline.stopPlayback()
        if (connected.get()) {
            sendRaw(
                JSONObject()
                    .put("session_id", "")
                    .put("type", "abort")
                    .put("reason", reason),
            )
        }
        stopListening()
        Log.i(TAG, "session ended quietly: $reason")
    }

    private fun handleMessage(text: String) {
        val json = JSONObject(text)
        when (json.optString("type")) {
            "hello" -> {
                val audioParams = json.optJSONObject("audio_params")
                val sampleRate = audioParams?.optInt("sample_rate", 24000) ?: 24000
                audioPipeline.configureDownlink(sampleRate)
                Log.i(TAG, "server hello audio sample_rate=$sampleRate")
            }
            "mcp" -> handleMcp(json.optJSONObject("payload") ?: JSONObject())
            "listen" -> {
                when (json.optString("state")) {
                    "stop" -> {
                        audioPipeline.stopCapture()
                        listening.set(false)
                        _events.tryEmit(VoiceEvent.VadEnd)
                        Log.i(TAG, "server listen/stop (VAD end)")
                    }
                }
            }
            "tts" -> {
                val state = json.optString("state")
                if (state == "start") {
                    if (!acceptServerTts) {
                        Log.i(TAG, "ignore tts/start: session inactive")
                        return
                    }
                    // 协议：listening 期间忽略下行音频；收到 tts/start 须先停麦再播 TTS
                    audioPipeline.stopCapture()
                    listening.set(false)
                    onTtsStart?.invoke()
                    _events.tryEmit(VoiceEvent.TtsStart)
                    Log.i(TAG, "tts/start -> capture stopped, downlink enabled")
                }
                if (state == "stop") {
                    if (!acceptServerTts) {
                        Log.i(TAG, "ignore tts/stop: session inactive")
                        return
                    }
                    audioPipeline.stopPlayback()
                    onTtsComplete?.invoke()
                    _events.tryEmit(VoiceEvent.TtsComplete)
                }
            }
            "stt" -> {
                val sttText = json.optString("text").trim()
                _events.tryEmit(VoiceEvent.SttResult(sttText))
                // listen/detect 后服务端/外放 TTS 可能 echo 唤醒词 STT，不应结束本轮聆听
                if (isWakeWordEcho(sttText)) {
                    Log.i(TAG, "ignore wake-word echo STT: $sttText")
                    return
                }
                if (listening.get()) {
                    audioPipeline.stopCapture()
                    listening.set(false)
                    _events.tryEmit(VoiceEvent.VadEnd)
                    Log.i(TAG, "stt received, VAD end: $sttText")
                }
            }
            else -> _events.tryEmit(VoiceEvent.Raw(json))
        }
    }

    private fun handleMcp(payload: JSONObject) {
        val method = payload.optString("method")
        when (method) {
            "tools/list" -> replyMcp(payload, buildToolsListResult())
            "tools/call" -> {
                val handler = mcpHandler
                val result = handler?.invoke(payload) ?: buildErrorResult(payload, "handler missing")
                replyMcp(payload, result)
            }
            else -> replyMcp(payload, JSONObject().put("error", JSONObject().put("message", "unknown method")))
        }
    }

    private fun replyMcp(request: JSONObject, result: JSONObject) {
        val id = request.opt("id")
        val response = JSONObject()
            .put("type", "mcp")
            .put("payload", JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("result", result))
        sendRaw(response)
    }

    private fun buildErrorResult(request: JSONObject, message: String): JSONObject {
        return JSONObject()
            .put("content", org.json.JSONArray().put(JSONObject().put("type", "text").put("text", message)))
            .put("isError", true)
    }

    private fun buildToolsListResult(): JSONObject {
        return JSONObject().put("tools", McpToolCatalog.buildToolsJsonArray())
    }

    private fun sendRaw(json: JSONObject) {
        webSocket?.send(json.toString())
    }

    fun shutdown() {
        disconnect(manual = true)
        audioPipeline.shutdown()
        scope.cancel()
    }

    private fun isWakeWordEcho(text: String): Boolean = WakeWords.isEcho(text)

    companion object {
        private const val TAG = "XiaozhiVoiceClient"
        const val DEFAULT_WAKE_WORD = WakeWords.DEFAULT
    }
}

sealed class VoiceEvent {
    data object Connected : VoiceEvent()
    data class Disconnected(val reason: String) : VoiceEvent()
    data class Error(val message: String) : VoiceEvent()
    data object ListeningStarted : VoiceEvent()
    data object ListeningStopped : VoiceEvent()
    data object VadEnd : VoiceEvent()
    data object TtsStart : VoiceEvent()
    data object TtsComplete : VoiceEvent()
    data class SttResult(val text: String) : VoiceEvent()
    data class Raw(val json: JSONObject) : VoiceEvent()
}
