package com.xiaoyu.app.ui.activation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.xiaoyu.app.R
import com.xiaoyu.app.ui.settings.SettingsActivity
import com.xiaoyu.core.voice.ota.XiaozhiBindState
import com.xiaoyu.service.XiaoyuAppGraph
import com.xiaoyu.service.XiaoyuAssistantService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActivationActivity : AppCompatActivity() {
    private lateinit var graph: XiaoyuAppGraph
    private lateinit var codeView: TextView
    private lateinit var macView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activation)
        graph = XiaoyuAppGraph.get(this)
        codeView = findViewById(R.id.activationCode)
        macView = findViewById(R.id.activationMac)
        macView.text = graph.deviceMac

        findViewById<Button>(R.id.btnCopyCode).setOnClickListener { copy(graph.bindManager.activationCode.value ?: "") }
        findViewById<Button>(R.id.btnCopyMac).setOnClickListener { copy(graph.deviceMac) }
        findViewById<Button>(R.id.btnOpenConsole).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://xiaozhi.me")))
        }
        findViewById<Button>(R.id.btnBindDone).setOnClickListener { refreshBind() }

        lifecycleScope.launch {
            while (isActive) {
                refreshBind(silent = true)
                delay(5000)
            }
        }
    }

    private fun refreshBind(silent: Boolean = false) {
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) { graph.bindManager.refresh() }
            codeView.text = graph.bindManager.activationCode.value ?: "—"
            if (state == XiaozhiBindState.BOUND) {
                graph.preferences.onboardingCompleted = true
                Toast.makeText(this@ActivationActivity, "绑定成功", Toast.LENGTH_SHORT).show()
                ContextCompat.startForegroundService(
                    this@ActivationActivity,
                    Intent(this@ActivationActivity, XiaoyuAssistantService::class.java)
                        .setAction(XiaoyuAssistantService.ACTION_CONNECT_VOICE),
                )
                startActivity(Intent(this@ActivationActivity, SettingsActivity::class.java))
                finish()
            } else if (!silent) {
                Toast.makeText(this@ActivationActivity, "仍未绑定，请继续在智控台输入激活码", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copy(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("xiaoyu", text))
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
    }
}
