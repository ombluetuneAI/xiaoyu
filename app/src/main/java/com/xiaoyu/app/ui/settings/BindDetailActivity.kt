package com.xiaoyu.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.xiaoyu.app.R
import com.xiaoyu.core.voice.ota.XiaozhiBindState
import com.xiaoyu.service.UserNotice
import com.xiaoyu.service.XiaoyuAppGraph
import com.xiaoyu.service.XiaoyuAssistantService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BindDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bind_detail)
        val graph = XiaoyuAppGraph.get(this)
        val status = findViewById<TextView>(R.id.bindStatus)
        val mac = findViewById<TextView>(R.id.bindMac)
        val wsStatus = findViewById<TextView>(R.id.wsStatus)
        val activationView = findViewById<TextView>(R.id.activationCode)
        val btnConsole = findViewById<Button>(R.id.btnOpenConsole)
        mac.text = "MAC (deriveMac): ${graph.deviceMac}"

        fun refreshUi(bindState: XiaozhiBindState) {
            status.text = when (bindState) {
                XiaozhiBindState.BOUND -> "已连接小智云端"
                XiaozhiBindState.NEEDS_ACTIVATION -> "待激活"
                else -> "未连接"
            }
            val ws = graph.bindManager.websocketConfig()?.websocket
            val connected = graph.voiceClient.isConnected()
            wsStatus.text = buildString {
                append("WebSocket: ")
                append(ws?.url ?: "—")
                append("\n状态: ")
                append(if (connected) "已连接" else if (bindState == XiaozhiBindState.BOUND) "未连接（服务将自动重连）" else "不可用")
            }
            val code = graph.bindManager.activationCode.value
            if (bindState == XiaozhiBindState.NEEDS_ACTIVATION && !code.isNullOrBlank()) {
                activationView.visibility = View.VISIBLE
                activationView.text = "激活码：$code"
                btnConsole.visibility = View.VISIBLE
            } else {
                activationView.visibility = View.GONE
                btnConsole.visibility = View.GONE
            }
        }

        btnConsole.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://xiaozhi.me/console")))
        }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            lifecycleScope.launch {
                val state = withContext(Dispatchers.IO) { graph.bindManager.refresh() }
                if (state == XiaozhiBindState.UNKNOWN) {
                    UserNotice.toast(this@BindDetailActivity, "无法连接小智服务")
                }
                refreshUi(state)
                if (state == XiaozhiBindState.BOUND) {
                    ContextCompat.startForegroundService(
                        this@BindDetailActivity,
                        Intent(this@BindDetailActivity, XiaoyuAssistantService::class.java)
                            .setAction(XiaoyuAssistantService.ACTION_CONNECT_VOICE),
                    )
                }
            }
        }

        lifecycleScope.launch {
            graph.bindManager.state.collectLatest { refreshUi(it) }
        }
        lifecycleScope.launch {
            graph.voiceClient.events.collectLatest {
                refreshUi(graph.bindManager.state.value)
            }
        }
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) { graph.bindManager.refresh() }
            refreshUi(state)
        }
    }
}
