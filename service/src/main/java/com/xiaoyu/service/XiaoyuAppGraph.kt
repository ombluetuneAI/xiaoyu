package com.xiaoyu.service

import android.app.Application
import android.content.Context
import com.xiaoyu.core.link.BroadcastLinkDispatcher
import com.xiaoyu.core.mcp.DeviceMcpServer
import com.xiaoyu.core.media.AudioFocusCoordinator
import com.xiaoyu.core.media.MediaResolver
import com.xiaoyu.core.media.PlaybackMode
import com.xiaoyu.core.media.VolumeController
import com.xiaoyu.core.media.api.MusicApiClient
import com.xiaoyu.core.media.api.RadioApiClient
import com.xiaoyu.core.media.player.MediaPlayerFacade
import com.xiaoyu.core.media.queue.MusicQueueManager
import com.xiaoyu.core.media.queue.QueuePlaybackAdvancer
import com.xiaoyu.core.registry.AppAdapterRegistry
import com.xiaoyu.core.router.ActiveMediaSource
import com.xiaoyu.core.router.BroadcastLinkDispatcherAdapter
import com.xiaoyu.core.router.CommandRouter
import com.xiaoyu.core.router.MediaCommandHandler
import com.xiaoyu.core.session.ListenTrigger
import com.xiaoyu.core.session.SessionEndReason
import com.xiaoyu.core.session.VoiceSessionManager
import com.xiaoyu.core.session.VoiceSessionState
import com.xiaoyu.core.voice.bind.XiaozhiBindManager
import com.xiaoyu.core.voice.client.XiaozhiVoiceClient
import com.xiaoyu.core.voice.identity.DeviceIdentity
import com.xiaoyu.core.voice.ota.XiaozhiOtaClient
import com.xiaoyu.core.voice.prefs.XiaoyuPreferences
import com.xiaoyu.core.wake.SherpaWakeEngine
import com.xiaoyu.core.wake.WakeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class XiaoyuAppGraph private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val mediaScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val preferences = XiaoyuPreferences(appContext)
    val deviceMac: String = DeviceIdentity.deriveMac(preferences.deviceId)

    val otaClient = XiaozhiOtaClient()
    val bindManager = XiaozhiBindManager(
        otaClient = otaClient,
        deviceMacProvider = { deviceMac },
        clientIdProvider = { preferences.clientId },
    )

    val voiceClient = XiaozhiVoiceClient()
    val wakeEngine: WakeEngine = SherpaWakeEngine(appContext)

    private val _sessionState = MutableStateFlow(VoiceSessionState.IDLE)
    val sessionState: StateFlow<VoiceSessionState> = _sessionState.asStateFlow()

    val queueManager = MusicQueueManager()
    val activeMediaSource = ActiveMediaSource()
    val registry = AppAdapterRegistry().apply { scanInstalledApps(appContext) }

    val broadcastLinkDispatcher = BroadcastLinkDispatcher(
        context = appContext,
        deepLinkResolver = { appId -> registry.deepLinkScheme(appId) },
        targetPackageVerifier = { pkg -> registry.isTrustedDispatchTarget(appContext, pkg) },
    )
    val linkDispatcher = BroadcastLinkDispatcherAdapter(broadcastLinkDispatcher)

    lateinit var audioFocus: AudioFocusCoordinator
    lateinit var playerFacade: MediaPlayerFacade
    lateinit var mediaResolver: MediaResolver
    lateinit var mediaCommandHandler: MediaCommandHandler
    lateinit var commandRouter: CommandRouter
    lateinit var deviceMcpServer: DeviceMcpServer
    lateinit var sessionManager: VoiceSessionManager
    lateinit var mediaSessionManager: MediaSessionManager
    lateinit var wakeAckPlayer: WakeAckPlayer

    var onSpeak: ((String) -> Unit)? = null

    fun initMedia(context: Context) {
        val ctx = context.applicationContext
        audioFocus = AudioFocusCoordinator(ctx)
        mediaResolver = MediaResolver(
            musicApi = MusicApiClient(baseUrlProvider = { preferences.txbApiBase }),
            radioApi = RadioApiClient(baseUrlProvider = { preferences.txbApiBase }),
        )
        playerFacade = MediaPlayerFacade(ctx, mediaScope, queueManager, audioFocus).apply {
            urlResolver = { track -> mediaResolver.resolveTrackUrl(track) }
            radioReResolver = { track ->
                val result = mediaResolver.resolveRadio(track.title)
                result.tracks.firstOrNull()
            }
            onError = {
                onSpeak?.invoke("播放出现问题，正在重试")
            }
        }
        mediaSessionManager = MediaSessionManager(ctx, playerFacade, queueManager)
        val queueAdvancer = QueuePlaybackAdvancer(
            scope = mediaScope,
            queueManager = queueManager,
            mediaResolver = mediaResolver,
            playerFacade = playerFacade,
            onAdvanced = { mediaSessionManager.updateNotification() },
        )
        playerFacade.onEnded = {
            when (queueManager.mode.value) {
                PlaybackMode.QUEUE_LOOP -> queueAdvancer.onTrackEnded()
                PlaybackMode.SINGLE -> {
                    playerFacade.stop()
                    mediaSessionManager.hideNotification()
                }
                PlaybackMode.RADIO -> Unit
            }
        }
        mediaCommandHandler = MediaCommandHandler(
            mediaResolver = mediaResolver,
            queueManager = queueManager,
            playerFacade = playerFacade,
            activeMediaSource = activeMediaSource,
            linkDispatcher = linkDispatcher,
            registry = registry,
            volumeController = VolumeController(ctx),
            onPlaybackError = { message -> onSpeak?.invoke(message) },
            onPlaybackStarted = { mediaSessionManager.updateNotification() },
        )
        commandRouter = CommandRouter(registry, linkDispatcher)
        deviceMcpServer = DeviceMcpServer(mediaCommandHandler)
        voiceClient.mcpHandler = { payload -> deviceMcpServer.handleMcpPayload(payload) }
        voiceClient.onTtsStart = { audioFocus.duckForVoiceTts() }
        voiceClient.onTtsComplete = { audioFocus.unduckAfterVoiceTts() }

        wakeAckPlayer = WakeAckPlayer(ctx)

        sessionManager = VoiceSessionManager(
            continuousDialog = { preferences.continuousDialog },
            idleTimeoutSec = { preferences.followUpTimeoutSec },
            onStateChanged = { _sessionState.value = it },
            onPauseWake = { wakeEngine.pause() },
            onStartListening = { trigger, wakeWord ->
                sessionManager.transitionTo(VoiceSessionState.LISTENING)
                voiceClient.startListening(
                    fromWake = trigger == ListenTrigger.WAKE,
                    wakeWord = wakeWord.ifBlank { com.xiaoyu.core.wake.WakeWords.DEFAULT },
                )
            },
            onStopListening = { voiceClient.stopListening() },
            onResumeWake = { wakeEngine.resume() },
            onEndSession = { reason ->
                when (reason) {
                    SessionEndReason.IDLE_TIMEOUT ->
                        voiceClient.endSessionAndDisconnect("user_idle")
                    SessionEndReason.MANUAL ->
                        voiceClient.endSessionAndDisconnect("user_cancel")
                    SessionEndReason.DISCONNECT ->
                        voiceClient.endSessionAndDisconnect("disconnect")
                    SessionEndReason.GOODBYE -> {
                        voiceClient.stopListening()
                        voiceClient.disconnect(manual = true)
                    }
                    SessionEndReason.NORMAL -> {
                        voiceClient.stopListening()
                        voiceClient.disconnect(manual = true)
                    }
                }
            },
            onWakeBlocked = {
                val code = bindManager.activationCode.value
                onSpeak?.invoke("请先绑定小智设备，激活码 ${code ?: "请打开绑定页查看"}")
            },
        )
    }

    companion object {
        @Volatile
        private var instance: XiaoyuAppGraph? = null

        fun get(context: Context): XiaoyuAppGraph {
            return instance ?: synchronized(this) {
                instance ?: XiaoyuAppGraph(context.applicationContext).also {
                    it.initMedia(context.applicationContext)
                    instance = it
                }
            }
        }

        fun init(application: Application): XiaoyuAppGraph = get(application)
    }
}
