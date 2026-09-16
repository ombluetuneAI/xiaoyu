package com.xiaoyu.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.xiaoyu.app.R
import com.xiaoyu.app.ui.activation.ActivationActivity
import com.xiaoyu.app.ui.media.PlayerActivity
import com.xiaoyu.core.voice.ota.XiaozhiBindState
import com.xiaoyu.service.UserNotice
import com.xiaoyu.service.XiaoyuAppGraph
import com.xiaoyu.service.XiaoyuAssistantService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val graph = XiaoyuAppGraph.get(this)
        val bindSummary = findViewById<TextView>(R.id.bindSummary)
        val nowPlaying = findViewById<TextView>(R.id.nowPlayingRow)

        lifecycleScope.launch {
            graph.bindManager.state.collect { state ->
                bindSummary.text = when (state) {
                    XiaozhiBindState.BOUND -> "已连接 · ${graph.deviceMac}"
                    XiaozhiBindState.NEEDS_ACTIVATION -> "待激活 · ${graph.bindManager.activationCode.value}"
                    else -> "未知"
                }
            }
        }

        lifecycleScope.launch {
            graph.queueManager.nowPlaying.collect { track ->
                if (track != null) {
                    nowPlaying.text = "正在播放 · ${track.title} · ${track.artist}"
                    nowPlaying.visibility = android.view.View.VISIBLE
                    nowPlaying.setOnClickListener {
                        startActivity(Intent(this@SettingsActivity, PlayerActivity::class.java))
                    }
                } else {
                    nowPlaying.visibility = android.view.View.GONE
                }
            }
        }

        findViewById<TextView>(R.id.rowBind).setOnClickListener {
            startActivity(Intent(this, BindDetailActivity::class.java))
        }
        findViewById<TextView>(R.id.rowTxb).setOnClickListener {
            startActivity(Intent(this, TxbSettingsActivity::class.java))
        }
        findViewById<TextView>(R.id.rowBackground).setOnClickListener {
            startActivity(Intent(this, BackgroundCheckActivity::class.java))
        }
        findViewById<TextView>(R.id.rowAdapters).setOnClickListener {
            startActivity(Intent(this, AdapterAppsActivity::class.java))
        }
        findViewById<TextView>(R.id.rowStartService).setOnClickListener {
            ContextCompat.startForegroundService(
                this,
                Intent(this, XiaoyuAssistantService::class.java)
                    .setAction(XiaoyuAssistantService.ACTION_CONNECT_VOICE),
            )
            Toast.makeText(this, "已启动语音前台服务，请查看通知栏", Toast.LENGTH_SHORT).show()
        }
        findViewById<TextView>(R.id.rowTestPlay).setOnClickListener {
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    graph.mediaResolver.resolveGeneral(limit = 5)
                }
                if (!result.ok || result.tracks.isEmpty()) {
                    Toast.makeText(
                        this@SettingsActivity,
                        result.message ?: "播放失败，请先配置 TXB 地址",
                        Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
                graph.queueManager.setQueue(result.tracks, result.mode, result.queueLabel)
                val ok = graph.playerFacade.playTrackAwait(result.tracks.first(), result.mode)
                if (ok) {
                    graph.mediaSessionManager.updateNotification()
                    Toast.makeText(this@SettingsActivity, "测试播放：${result.tracks.first().title}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SettingsActivity, "播放失败", Toast.LENGTH_LONG).show()
                }
            }
        }

        val bgSwitch = findViewById<Switch>(R.id.switchBackgroundVoice)
        val contSwitch = findViewById<Switch>(R.id.switchContinuous)
        bgSwitch.isChecked = graph.preferences.backgroundVoiceEnabled
        contSwitch.isChecked = graph.preferences.continuousDialog
        bgSwitch.setOnCheckedChangeListener { _, checked -> graph.preferences.backgroundVoiceEnabled = checked }
        contSwitch.setOnCheckedChangeListener { _, checked -> graph.preferences.continuousDialog = checked }

        if (graph.bindManager.state.value == XiaozhiBindState.NEEDS_ACTIVATION) {
            startActivity(Intent(this, ActivationActivity::class.java))
        } else if (
            graph.preferences.onboardingCompleted &&
            graph.bindManager.state.value == XiaozhiBindState.BOUND
        ) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, XiaoyuAssistantService::class.java)
                    .setAction(XiaoyuAssistantService.ACTION_CONNECT_VOICE),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val graph = XiaoyuAppGraph.get(this)
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) { graph.bindManager.refresh() }
            if (state == XiaozhiBindState.UNKNOWN) {
                UserNotice.toast(this@SettingsActivity, "无法连接小智服务")
            }
        }
    }
}
