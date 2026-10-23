package com.xiaoyu.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.xiaoyu.app.R
import com.xiaoyu.app.ui.media.PlayerActivity
import com.xiaoyu.core.router.MediaSource
import com.xiaoyu.service.XiaoyuAppGraph
import com.xiaoyu.service.XiaoyuAssistantService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsDebugActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_debug)
        val graph = XiaoyuAppGraph.get(this)

        findViewById<TextView>(R.id.rowKws).setOnClickListener {
            startActivity(Intent(this, KwsSettingsActivity::class.java))
        }
        findViewById<TextView>(R.id.rowAdapters).setOnClickListener {
            startActivity(Intent(this, AdapterAppsActivity::class.java))
        }
        findViewById<TextView>(R.id.rowStartService).setOnClickListener {
            ContextCompat.startForegroundService(
                this,
                Intent(this, XiaoyuAssistantService::class.java),
            )
            Toast.makeText(this, "已启动语音前台服务，请查看通知栏", Toast.LENGTH_SHORT).show()
        }
        findViewById<TextView>(R.id.rowTestPlay).setOnClickListener {
            debugPlay(graph, toastLabel = "测试播放")
        }
        findViewById<TextView>(R.id.rowPlayMusic).setOnClickListener {
            debugPlay(graph, toastLabel = "播放音乐")
        }
    }

    private fun debugPlay(graph: XiaoyuAppGraph, toastLabel: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                graph.mediaResolver.resolveGeneral(limit = 5)
            }
            if (!result.ok || result.tracks.isEmpty()) {
                Toast.makeText(
                    this@SettingsDebugActivity,
                    result.message ?: "播放失败，请先配置 TXB 地址",
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            graph.queueManager.setQueue(result.tracks, result.mode, result.queueLabel)
            val ok = graph.playerFacade.playTrackAwait(result.tracks.first(), result.mode)
            if (ok) {
                graph.activeMediaSource.set(MediaSource.XIAOYU, "music.play")
                graph.mediaSessionManager.updateNotification()
                Toast.makeText(
                    this@SettingsDebugActivity,
                    "$toastLabel：${result.tracks.first().title}",
                    Toast.LENGTH_SHORT,
                ).show()
                startActivity(Intent(this@SettingsDebugActivity, PlayerActivity::class.java))
            } else {
                Toast.makeText(this@SettingsDebugActivity, "播放失败", Toast.LENGTH_LONG).show()
            }
        }
    }
}
