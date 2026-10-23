package com.xiaoyu.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.xiaoyu.app.R
import com.xiaoyu.app.ui.activation.ActivationActivity
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

        lifecycleScope.launch {
            graph.bindManager.state.collect { state ->
                bindSummary.text = when (state) {
                    XiaozhiBindState.BOUND -> "已连接 · ${graph.deviceMac}"
                    XiaozhiBindState.NEEDS_ACTIVATION -> "待激活 · ${graph.bindManager.activationCode.value}"
                    else -> "未知"
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
        findViewById<TextView>(R.id.rowAdvanced).setOnClickListener {
            startActivity(Intent(this, SettingsDebugActivity::class.java))
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
                Intent(this, XiaoyuAssistantService::class.java),
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
