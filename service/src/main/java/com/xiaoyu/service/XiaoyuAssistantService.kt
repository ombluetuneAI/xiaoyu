package com.xiaoyu.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xiaoyu.core.media.Track
import com.xiaoyu.core.router.MediaSource
import com.xiaoyu.core.session.VoiceSessionState
import com.xiaoyu.core.voice.client.VoiceEvent
import com.xiaoyu.core.voice.ota.XiaozhiBindState
import com.xiaoyu.core.wake.OfflineKwsCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class XiaoyuAssistantService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var graph: XiaoyuAppGraph
    private var voiceWakeLock: PowerManager.WakeLock? = null
    private val wakeSessionMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        graph = XiaoyuAppGraph.get(this)
        graph.onSpeak = { message -> UserNotice.toast(this, message) }
        graph.wakeAckPlayer.onBeforePlay = {
            graph.audioFocus.requestForTts { graph.wakeAckPlayer.stop() }
        }
        graph.wakeAckPlayer.onPlayingChanged = { speaking ->
            if (!speaking) graph.audioFocus.abandonTtsFocus()
        }
        XiaoyuNotifications.ensureChannels(this)
        startForeground(NOTIFICATION_ID, buildNotification("小鱼同学待命中", "说「小鱼同学」唤醒"))
        Log.i(TAG, "FGS onCreate: startForeground OK, wakeEngine starting")
        scope.launch(Dispatchers.IO) {
            val state = graph.bindManager.refresh()
            if (state == XiaozhiBindState.UNKNOWN) {
                withContext(Dispatchers.Main) {
                    graph.onSpeak?.invoke("无法连接小智服务")
                }
            }
        }
        flushPendingLinkQueue()
        ServiceRestartScheduler.schedule(this)
        graph.wakeEngine.start()
        observeVoice()
        observeWake()
        scope.launch { pollIdleTimeout() }
        scope.launch { pollActiveMediaIdle() }
    }

    private fun flushPendingLinkQueue() {
        scope.launch(Dispatchers.IO) {
            graph.broadcastLinkDispatcher.flushPendingQueue()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_WAKE_FROM_NOTIFICATION -> {
                Log.i(TAG, "notification wake tapped")
                scope.launch {
                    runWakeSession(trigger = "notification")
                }
            }
            ACTION_END_SESSION -> graph.sessionManager.endSession()
            ACTION_REFRESH_BIND -> scope.launch(Dispatchers.IO) {
                val state = graph.bindManager.refresh()
                if (state == XiaozhiBindState.UNKNOWN) {
                    withContext(Dispatchers.Main) {
                        graph.onSpeak?.invoke("无法连接小智服务")
                    }
                }
            }
            ACTION_CONNECT_VOICE -> {
                Log.i(TAG, "ACTION_CONNECT_VOICE: ensureVoiceConnected")
                ensureVoiceConnected()
            }
            ACTION_DEBUG_SIMULATE_KWS -> scope.launch {
                runWakeSession(trigger = "debug_kws", wakeWord = "小鱼小鱼")
            }
            ACTION_DEBUG_PLAY_WAKE_TTS -> scope.launch {
                graph.wakeAckPlayer.playRandom(onComplete = {})
            }
            ACTION_RELOAD_KWS -> {
                graph.wakeEngine.reloadConfiguration()
                Log.i(TAG, "ACTION_RELOAD_KWS: wake engine reloaded")
            }
            ACTION_TEST_PLAY -> scope.launch(Dispatchers.IO) {
                val result = graph.mediaResolver.resolveGeneral(limit = 10)
                if (!result.ok || result.tracks.isEmpty()) {
                    Log.w(TAG, "TEST_PLAY failed: ${result.message}")
                    return@launch
                }
                graph.queueManager.setQueue(result.tracks, result.mode, result.queueLabel)
                val ok = graph.playerFacade.playTrackAwait(result.tracks.first(), result.mode)
                if (ok) {
                    graph.activeMediaSource.set(
                        com.xiaoyu.core.router.MediaSource.XIAOYU,
                        "music.play",
                    )
                    graph.mediaSessionManager.updateNotification()
                    Log.i(TAG, "TEST_PLAY ok: ${result.tracks.first().title}")
                } else {
                    Log.w(TAG, "TEST_PLAY playback failed")
                }
            }
            else -> Log.i(TAG, "onStartCommand action=${intent?.action ?: "null"}")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restart = Intent(applicationContext, XiaoyuAssistantService::class.java)
        startForegroundService(restart)
        ServiceRestartScheduler.schedule(this)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseVoiceWakeLock()
        scope.cancel()
        graph.voiceClient.disconnect()
        graph.wakeEngine.stop()
        graph.mediaSessionManager.release()
        graph.wakeAckPlayer.stop()
        super.onDestroy()
    }

    private fun observeWake() {
        scope.launch {
            graph.wakeEngine.wakeEvents.collectLatest { phrase ->
                val offlineAction = OfflineKwsCommands.playbackAction(phrase)
                if (offlineAction != null) {
                    if (graph.sessionState.value == VoiceSessionState.IDLE) {
                        runOfflineKwsPlayback(offlineAction, phrase)
                    } else {
                        Log.i(TAG, "offline KWS「$phrase」ignored: session=${graph.sessionState.value}")
                        graph.onSpeak?.invoke("请先结束语音对话，再说播控指令")
                    }
                    return@collectLatest
                }
                runWakeSession(trigger = "kws", wakeWord = phrase)
            }
        }
    }

    private suspend fun runOfflineKwsPlayback(action: String, phrase: String) {
        Log.i(TAG, "offline KWS「$phrase」action=$action")
        if (graph.queueManager.tracks.value.isEmpty()) {
            graph.onSpeak?.invoke("当前没有播放列表，请先播放音乐")
            return
        }
        graph.activeMediaSource.set(MediaSource.XIAOYU, "offline.kws")
        when (action) {
            "next" -> {
                if (graph.playerFacade.blocksOfflineNextWhileUserPaused()) {
                    Log.i(TAG, "offline KWS「$phrase」ignored: user sticky pause")
                    graph.onSpeak?.invoke("当前已暂停，请先说继续播放")
                    return
                }
                playOfflineAdjacent(resolveOfflineNext(), "下一首")
            }
            "previous" -> playOfflineAdjacent(graph.queueManager.previous(), "上一首")
            "pause" -> {
                graph.playerFacade.pauseForUserRequest()
                graph.mediaSessionManager.updateNotification()
            }
            "resume" -> {
                graph.playerFacade.resume()
                graph.mediaSessionManager.updateNotification()
            }
            else -> Unit
        }
    }

    private fun resolveOfflineNext(): Track? {
        val qm = graph.queueManager
        val next = qm.next()
        if (next != null) return next
        val list = qm.tracks.value
        if (list.size <= 1) return null
        val idx = (qm.currentIndex.value + 1) % list.size
        qm.jumpTo(idx)
        return qm.currentTrack()
    }

    private suspend fun playOfflineAdjacent(track: Track?, label: String) {
        if (track == null) {
            graph.onSpeak?.invoke(if (label == "下一首") "没有下一首了" else "没有上一首了")
            return
        }
        val resolved = withContext(Dispatchers.IO) {
            graph.mediaResolver.resolveTrackUrl(track)
        } ?: track
        if (resolved.url.isBlank()) {
            graph.onSpeak?.invoke("$label 无法播放")
            return
        }
        val ok = graph.playerFacade.playTrackAwait(resolved, graph.queueManager.mode.value)
        if (ok) {
            graph.mediaSessionManager.updateNotification()
            Log.i(TAG, "offline KWS $label -> ${resolved.title}")
        } else {
            graph.onSpeak?.invoke("播放失败")
        }
    }

    private suspend fun runWakeSession(trigger: String, wakeWord: String = com.xiaoyu.core.wake.WakeWords.DEFAULT) {
        wakeSessionMutex.withLock {
            if (!graph.bindManager.isVoiceAllowed()) {
                val code = graph.bindManager.activationCode.value ?: "—"
                updateNotification("待绑定小智", "激活码 $code")
                graph.sessionManager.onWakeWordDetected(false)
                return
            }
            val playLocalWakeAck = trigger == "kws" || trigger == "debug_kws"
            Log.i(TAG, "wake word detected: $wakeWord trigger=$trigger")
            graph.playerFacade.pauseMusicForVoiceOutput()
            graph.sessionManager.onWakeWordDetected(true, wakeWord)

            coroutineScope {
                val voiceReady = async(Dispatchers.IO) { prepareVoiceForWake() }
                if (playLocalWakeAck) {
                    launch(Dispatchers.Main) {
                        graph.wakeAckPlayer.playRandom {
                            graph.audioFocus.abandonTtsFocus()
                        }
                    }
                }
                if (!voiceReady.await()) {
                    Log.w(TAG, "wake aborted: voice not ready")
                    graph.wakeAckPlayer.stop()
                    graph.sessionManager.endSession()
                    graph.onSpeak?.invoke("语音服务未连接，请稍后再试")
                    return@coroutineScope
                }
                Log.i(TAG, "voice ready -> startListening (wake ack may still be playing)")
                graph.sessionManager.startListeningAfterWakeAck(wakeWord)
            }
        }
    }

    private fun observeVoice() {
        scope.launch {
            graph.voiceClient.events.collectLatest { event ->
                when (event) {
                    is VoiceEvent.Connected -> {
                        if (graph.sessionState.value == VoiceSessionState.IDLE) {
                            updateNotification("小鱼同学待命中", "说「小鱼同学」唤醒")
                        } else {
                            updateNotification("小鱼同学", "语音会话已连接")
                        }
                        Log.i(TAG, "voice WS connected session=${graph.sessionState.value}")
                    }
                    is VoiceEvent.Disconnected -> {
                        val session = graph.sessionState.value
                        if (session == VoiceSessionState.WAKE_DETECTED) {
                            Log.i(TAG, "WS disconnected during wake prep, keep session")
                            return@collectLatest
                        }
                        updateNotification("小鱼同学待命中", "说唤醒词开始对话")
                        if (session != VoiceSessionState.IDLE) {
                            Log.i(TAG, "voice disconnected during session -> end quietly")
                            graph.sessionManager.endSession(com.xiaoyu.core.session.SessionEndReason.DISCONNECT)
                        }
                    }
                    is VoiceEvent.ListeningStarted -> updateNotification("正在听…", "请说话")
                    is VoiceEvent.VadEnd -> {
                        Log.i(TAG, "VAD end -> THINKING")
                        graph.sessionManager.onVadEnd()
                    }
                    is VoiceEvent.TtsStart -> graph.sessionManager.onTtsStart()
                    is VoiceEvent.TtsComplete -> graph.sessionManager.onTtsComplete()
                    is VoiceEvent.SttResult -> {
                        graph.sessionManager.onUserActivity()
                        val stt = event.text
                        if (stt.contains("暂停") || stt.contains("停一下") || stt.contains("别播")) {
                            graph.playerFacade.pauseForUserRequest()
                            graph.mediaSessionManager.updateNotification()
                        }
                        if (stt.contains("再见")) {
                            graph.playerFacade.discardResumeAfterVoiceSession()
                            graph.sessionManager.onGoodbye()
                        }
                    }
                    is VoiceEvent.Error -> Log.w(TAG, "voice error: ${event.message}")
                    else -> Unit
                }
            }
        }
        scope.launch {
            graph.sessionState.collectLatest { state ->
                when (state) {
                    VoiceSessionState.IDLE -> {
                        releaseVoiceWakeLock()
                        graph.playerFacade.tryResumeMusicAfterVoiceSession()
                        ensureVoiceDisconnectedWhileIdle()
                        updateNotification("小鱼同学待命中", "说「小鱼同学」唤醒")
                    }
                    VoiceSessionState.WAKE_DETECTED -> updateNotification("小鱼同学", "应答中…")
                    VoiceSessionState.LISTENING -> {
                        acquireVoiceWakeLock()
                        val sec = graph.preferences.followUpTimeoutSec
                        updateNotification("正在听…", "${sec}s 无说话将自动退出")
                    }
                    VoiceSessionState.THINKING -> {
                        graph.playerFacade.pauseMusicForVoiceOutput()
                        updateNotification("思考中…", "等待小智")
                    }
                    VoiceSessionState.SPEAKING -> {
                        acquireVoiceWakeLock()
                        graph.playerFacade.pauseMusicForVoiceOutput()
                        updateNotification("正在说…", "TTS 播报中")
                    }
                    VoiceSessionState.FOLLOW_UP -> {
                        acquireVoiceWakeLock()
                        graph.playerFacade.tryResumeMusicAfterVoiceSession()
                        val sec = graph.preferences.followUpTimeoutSec
                        updateNotification("连续对话中…", "${sec}s 内无需唤醒词")
                    }
                    else -> Unit
                }
            }
        }
        scope.launch {
            graph.queueManager.tracks.collectLatest {
                if (graph.queueManager.currentTrack() != null) {
                    graph.activeMediaSource.touch()
                    graph.mediaSessionManager.updateNotification()
                }
            }
        }
    }

    private suspend fun pollIdleTimeout() {
        while (scope.isActive) {
            delay(500)
            if (graph.sessionManager.isIdleExpired()) {
                Log.i(TAG, "idle timeout -> end session quietly")
                graph.sessionManager.onIdleTimeout()
            }
        }
    }

    private suspend fun pollActiveMediaIdle() {
        while (scope.isActive) {
            delay(60_000)
            if (graph.activeMediaSource.clearIfIdle()) {
                Log.i(TAG, "activeMediaSource cleared after 30min idle")
            }
        }
    }

    /** 待命态不保持语音 WS；仅唤醒/会话期间连接 */
    private fun ensureVoiceDisconnectedWhileIdle() {
        if (!graph.voiceClient.isConnected()) return
        graph.voiceClient.disconnect(manual = true, emitDisconnected = true)
        Log.i(TAG, "voice WS closed: session IDLE")
    }

    private fun ensureVoiceConnected() {
        if (graph.voiceClient.isConnected()) return
        val bind = graph.bindManager.websocketConfig() ?: return
        if (graph.bindManager.state.value != XiaozhiBindState.BOUND) return
        val ws = bind.websocket ?: return
        val version = bind.websocket?.version?.toIntOrNull() ?: 1
        graph.voiceClient.connect(ws.url, ws.token, graph.deviceMac, graph.preferences.clientId, version)
    }

    private suspend fun prepareVoiceForWake(): Boolean {
        graph.voiceClient.disconnect(manual = true, emitDisconnected = false)
        if (!ensureVoiceConnectedAwait(timeoutMs = 12_000L)) {
            Log.w(TAG, "prepareVoiceForWake: WS connect timeout")
            return false
        }
        return graph.voiceClient.awaitServerHello(timeoutMs = 12_000L)
    }

    private suspend fun ensureVoiceConnectedAwait(timeoutMs: Long = 8000L): Boolean {
        if (graph.voiceClient.isConnected()) return true
        ensureVoiceConnected()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (graph.voiceClient.isConnected()) return true
            delay(100)
        }
        return graph.voiceClient.isConnected()
    }

    private fun acquireVoiceWakeLock() {
        if (voiceWakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        voiceWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xiaoyu:voice_session").apply {
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseVoiceWakeLock() {
        voiceWakeLock?.let {
            if (it.isHeld) it.release()
        }
        voiceWakeLock = null
    }

    private fun buildNotification(title: String, text: String): Notification {
        val wakeIntent = PendingIntent.getService(
            this, 0,
            Intent(this, XiaoyuAssistantService::class.java).setAction(ACTION_WAKE_FROM_NOTIFICATION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val endIntent = PendingIntent.getService(
            this, 1,
            Intent(this, XiaoyuAssistantService::class.java).setAction(ACTION_END_SESSION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, XiaoyuNotifications.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(wakeIntent)
            .addAction(0, "点击唤醒", wakeIntent)
            .addAction(1, "结束对话", endIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    companion object {
        private const val TAG = "XiaoyuAssistantService"
        const val NOTIFICATION_ID = 1001
        const val ACTION_WAKE_FROM_NOTIFICATION = "com.xiaoyu.action.WAKE"
        const val ACTION_END_SESSION = "com.xiaoyu.action.END_SESSION"
        const val ACTION_REFRESH_BIND = "com.xiaoyu.action.REFRESH_BIND"
        const val ACTION_CONNECT_VOICE = "com.xiaoyu.action.CONNECT_VOICE"
        const val ACTION_TEST_PLAY = "com.xiaoyu.action.TEST_PLAY"
        const val ACTION_DEBUG_SIMULATE_KWS = "com.xiaoyu.action.DEBUG_SIMULATE_KWS"
        const val ACTION_DEBUG_PLAY_WAKE_TTS = "com.xiaoyu.action.DEBUG_PLAY_WAKE_TTS"
        const val ACTION_RELOAD_KWS = "com.xiaoyu.action.RELOAD_KWS"
    }
}
