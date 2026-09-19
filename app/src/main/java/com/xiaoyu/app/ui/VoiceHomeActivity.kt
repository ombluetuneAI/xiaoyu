package com.xiaoyu.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.xiaoyu.app.R
import com.xiaoyu.app.ui.media.PlayerActivity
import com.xiaoyu.app.ui.settings.SettingsActivity
import com.xiaoyu.core.media.Track
import com.xiaoyu.core.session.VoiceSessionState
import com.xiaoyu.core.voice.client.VoiceEvent
import com.xiaoyu.core.voice.ota.XiaozhiBindState
import com.xiaoyu.service.XiaoyuAppGraph
import com.xiaoyu.service.XiaoyuAssistantService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 绑定完成后的主界面：展示语音/播放状态，不再直接 dump 到 Settings */
class VoiceHomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_home)
        val graph = XiaoyuAppGraph.get(this)

        ContextCompat.startForegroundService(
            this,
            Intent(this, XiaoyuAssistantService::class.java),
        )

        val bindStatus = findViewById<TextView>(R.id.bindStatus)
        val voiceStatus = findViewById<TextView>(R.id.voiceStatus)
        val sessionStatus = findViewById<TextView>(R.id.sessionStatus)
        val nowPlayingStatus = findViewById<TextView>(R.id.nowPlayingStatus)
        val statusSubtitle = findViewById<TextView>(R.id.statusSubtitle)

        val wsConnected = flow {
            emit(graph.voiceClient.isConnected())
            graph.voiceClient.events.collect { event ->
                when (event) {
                    is VoiceEvent.Connected,
                    is VoiceEvent.Disconnected,
                    -> emit(graph.voiceClient.isConnected())
                    else -> Unit
                }
            }
        }.distinctUntilChanged()

        lifecycleScope.launch {
            combine(
                graph.bindManager.state,
                graph.sessionState,
                graph.queueManager.nowPlaying,
                wsConnected,
            ) { bind, session, track, voiceWs ->
                VoiceHomeUiState(bind, session, track, voiceWs)
            }.collect { ui ->
                val bind = ui.bind
                val session = ui.session
                val track = ui.track
                bindStatus.text = when (bind) {
                    XiaozhiBindState.BOUND -> "小智绑定 · 已连接"
                    XiaozhiBindState.NEEDS_ACTIVATION -> "小智绑定 · 待激活"
                    else -> "小智绑定 · 未知"
                }
                voiceStatus.text = if (ui.voiceWsConnected) {
                    getString(R.string.voice_ws_connected)
                } else {
                    getString(R.string.voice_ws_disconnected)
                }
                sessionStatus.text = "会话 · ${sessionLabel(session)}"
                statusSubtitle.text = when (session) {
                    VoiceSessionState.LISTENING -> "正在听… 请说话"
                    VoiceSessionState.THINKING -> "思考中…"
                    VoiceSessionState.SPEAKING -> "正在播报…"
                    VoiceSessionState.FOLLOW_UP -> "连续对话中 · ${graph.preferences.followUpTimeoutSec}s 内无需唤醒词"
                    else -> getString(R.string.wake_hint)
                }
                if (track != null) {
                    nowPlayingStatus.visibility = View.VISIBLE
                    nowPlayingStatus.text = "正在播放 · ${track.title} · ${track.artist}"
                    nowPlayingStatus.setOnClickListener {
                        startActivity(Intent(this@VoiceHomeActivity, PlayerActivity::class.java))
                    }
                } else {
                    nowPlayingStatus.visibility = View.GONE
                }
            }
        }

        findViewById<TextView>(R.id.btnWake).setOnClickListener {
            ContextCompat.startForegroundService(
                this,
                Intent(this, XiaoyuAssistantService::class.java)
                    .setAction(XiaoyuAssistantService.ACTION_WAKE_FROM_NOTIFICATION),
            )
            Toast.makeText(this, "已触发唤醒", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.btnPlay).setOnClickListener {
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    graph.mediaResolver.resolveGeneral(limit = 10)
                }
                if (!result.ok || result.tracks.isEmpty()) {
                    Toast.makeText(
                        this@VoiceHomeActivity,
                        result.message ?: "播放失败，请检查 TXB 配置",
                        Toast.LENGTH_LONG,
                    ).show()
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
                    Toast.makeText(
                        this@VoiceHomeActivity,
                        "正在播放 ${result.tracks.first().title}",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(this@VoiceHomeActivity, "播放失败", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        if (intent.getBooleanExtra(EXTRA_AUTO_PLAY, false)) {
            findViewById<TextView>(R.id.btnPlay).performClick()
        }
    }

    private data class VoiceHomeUiState(
        val bind: XiaozhiBindState,
        val session: VoiceSessionState,
        val track: Track?,
        val voiceWsConnected: Boolean,
    )

    companion object {
        const val EXTRA_AUTO_PLAY = "auto_play"
    }

    private fun sessionLabel(state: VoiceSessionState): String = when (state) {
        VoiceSessionState.IDLE -> "待命中"
        VoiceSessionState.WAKE_DETECTED -> "唤醒检测"
        VoiceSessionState.LISTENING -> "聆听中"
        VoiceSessionState.THINKING -> "思考中"
        VoiceSessionState.SPEAKING -> "播报中"
        VoiceSessionState.FOLLOW_UP -> "连续对话"
    }
}
