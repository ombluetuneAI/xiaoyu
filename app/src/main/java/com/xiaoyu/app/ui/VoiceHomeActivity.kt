package com.xiaoyu.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.xiaoyu.app.R
import com.xiaoyu.app.ui.activation.ActivationActivity
import com.xiaoyu.app.ui.media.PlayerActivity
import com.xiaoyu.app.ui.settings.BackgroundCheckActivity
import com.xiaoyu.app.ui.settings.SettingsActivity
import com.xiaoyu.core.session.VoiceSessionState
import com.xiaoyu.core.voice.client.VoiceEvent
import com.xiaoyu.core.voice.ota.XiaozhiBindState
import com.xiaoyu.service.XiaoyuAppGraph
import com.xiaoyu.service.XiaoyuAssistantService
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/** 绑定完成后的主界面：状态球 + 点按唤醒；播放音乐仅调试入口 */
class VoiceHomeActivity : AppCompatActivity() {
    private var lastVoiceError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_home)
        val graph = XiaoyuAppGraph.get(this)

        ContextCompat.startForegroundService(
            this,
            Intent(this, XiaoyuAssistantService::class.java),
        )

        val keepaliveBanner = findViewById<TextView>(R.id.keepaliveBanner)
        val chipBind = findViewById<TextView>(R.id.chipBind)
        val chipWs = findViewById<TextView>(R.id.chipWs)
        val statusBall = findViewById<TextView>(R.id.statusBall)
        val statusSubtitle = findViewById<TextView>(R.id.statusSubtitle)
        val miniPlayerBar = findViewById<LinearLayout>(R.id.miniPlayerBar)
        val miniPlayerText = findViewById<TextView>(R.id.miniPlayerText)
        val bottomRecoveryBar = findViewById<TextView>(R.id.bottomRecoveryBar)
        val unboundOverlay = findViewById<View>(R.id.unboundOverlay)

        keepaliveBanner.setOnClickListener {
            startActivity(Intent(this, BackgroundCheckActivity::class.java))
        }

        findViewById<TextView>(R.id.btnGoBind).setOnClickListener {
            startActivity(Intent(this, ActivationActivity::class.java))
        }

        findViewById<TextView>(R.id.btnWake).setOnClickListener {
            lastVoiceError = null
            ContextCompat.startForegroundService(
                this,
                Intent(this, XiaoyuAssistantService::class.java)
                    .setAction(XiaoyuAssistantService.ACTION_WAKE_FROM_NOTIFICATION),
            )
            Toast.makeText(this, "已触发唤醒", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        miniPlayerBar.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java))
        }

        bottomRecoveryBar.setOnClickListener {
            findViewById<TextView>(R.id.btnWake).performClick()
        }

        lifecycleScope.launch {
            graph.voiceClient.events.collect { event ->
                if (event is VoiceEvent.Error) {
                    lastVoiceError = event.message
                }
            }
        }

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
                refreshKeepaliveBanner(keepaliveBanner, graph)

                val bound = ui.bind == XiaozhiBindState.BOUND
                unboundOverlay.visibility = if (bound) View.GONE else View.VISIBLE

                chipBind.text = when (ui.bind) {
                    XiaozhiBindState.BOUND -> "绑定 · 已连接"
                    XiaozhiBindState.NEEDS_ACTIVATION -> "绑定 · 待激活"
                    else -> "绑定 · 未知"
                }
                chipBind.setTextColor(
                    getColor(
                        if (ui.bind == XiaozhiBindState.BOUND) R.color.xiaoyu_primary else R.color.xiaoyu_warn,
                    ),
                )

                chipWs.text = if (ui.voiceWsConnected) {
                    getString(R.string.voice_ws_connected)
                } else {
                    getString(R.string.voice_ws_disconnected)
                }
                chipWs.setTextColor(
                    getColor(
                        if (ui.voiceWsConnected) R.color.xiaoyu_primary else R.color.xiaoyu_danger,
                    ),
                )

                val ballLabel = when {
                    ui.track != null && graph.playerFacade.isPlaying() -> getString(R.string.state_playing)
                    ui.session == VoiceSessionState.LISTENING -> getString(R.string.state_listening)
                    ui.session == VoiceSessionState.THINKING -> getString(R.string.state_thinking)
                    ui.session == VoiceSessionState.SPEAKING -> getString(R.string.state_speaking)
                    ui.session == VoiceSessionState.FOLLOW_UP -> getString(R.string.state_follow_up)
                    else -> getString(R.string.state_idle)
                }
                statusBall.text = ballLabel

                statusSubtitle.text = when (ui.session) {
                    VoiceSessionState.LISTENING -> "正在听… 请说话"
                    VoiceSessionState.THINKING -> "思考中…"
                    VoiceSessionState.SPEAKING -> "正在播报…"
                    VoiceSessionState.FOLLOW_UP ->
                        "连续对话中 · ${graph.preferences.followUpTimeoutSec}s 内无需唤醒词"
                    else -> getString(R.string.wake_hint)
                }

                if (ui.track != null) {
                    miniPlayerBar.visibility = View.VISIBLE
                    miniPlayerText.text = "${ui.track.title} · ${ui.track.artist}"
                } else {
                    miniPlayerBar.visibility = View.GONE
                }

                when {
                    bound && !ui.voiceWsConnected -> {
                        bottomRecoveryBar.visibility = View.VISIBLE
                        bottomRecoveryBar.text = getString(R.string.recovery_ws)
                    }
                    bound && lastVoiceError != null -> {
                        bottomRecoveryBar.visibility = View.VISIBLE
                        bottomRecoveryBar.text = getString(R.string.recovery_listen)
                    }
                    else -> bottomRecoveryBar.visibility = View.GONE
                }

            }
        }
    }

    override fun onResume() {
        super.onResume()
        val graph = XiaoyuAppGraph.get(this)
        refreshKeepaliveBanner(findViewById(R.id.keepaliveBanner), graph)
    }

    private fun refreshKeepaliveBanner(banner: TextView, graph: XiaoyuAppGraph) {
        banner.visibility = if (graph.preferences.shouldShowKeepaliveBanner()) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private data class VoiceHomeUiState(
        val bind: XiaozhiBindState,
        val session: VoiceSessionState,
        val track: com.xiaoyu.core.media.Track?,
        val voiceWsConnected: Boolean,
    )
}
