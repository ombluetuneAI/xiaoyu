package com.xiaoyu.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.xiaoyu.app.R
import com.xiaoyu.app.ui.activation.ActivationActivity
import com.xiaoyu.app.ui.settings.BackgroundCheckActivity
import com.xiaoyu.app.ui.settings.SettingsActivity
import com.xiaoyu.core.voice.ota.XiaozhiBindState
import com.xiaoyu.service.XiaoyuAppGraph
import com.xiaoyu.service.XiaoyuAssistantService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingActivity : AppCompatActivity() {
    private var step = 0
    private lateinit var titleView: TextView
    private lateinit var descView: TextView
    private lateinit var actionBtn: Button
    private lateinit var graph: XiaoyuAppGraph

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) nextStep() else showStep()
    }
    private val notifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        nextStep()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        graph = XiaoyuAppGraph.get(this)
        titleView = findViewById(R.id.onboardingTitle)
        descView = findViewById(R.id.onboardingDesc)
        actionBtn = findViewById(R.id.onboardingAction)
        actionBtn.setOnClickListener { onAction() }
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) { graph.bindManager.refresh() }
            if (state == XiaozhiBindState.BOUND) {
                graph.preferences.onboardingCompleted = true
                ContextCompat.startForegroundService(
                    this@OnboardingActivity,
                    Intent(this@OnboardingActivity, XiaoyuAssistantService::class.java)
                        .setAction(XiaoyuAssistantService.ACTION_CONNECT_VOICE),
                )
                startActivity(Intent(this@OnboardingActivity, SettingsActivity::class.java))
                finish()
            }
        }
        showStep()
    }

    private fun onAction() {
        when (step) {
            0 -> nextStep()
            1 -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
            2 -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else nextStep()
            }
            3 -> startActivity(Intent(this, ActivationActivity::class.java))
            4 -> completeOnboarding()
        }
    }

    private fun completeOnboarding() {
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) { graph.bindManager.refresh() }
            if (state != XiaozhiBindState.BOUND) {
                Toast.makeText(this@OnboardingActivity, "请先完成小智绑定", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@OnboardingActivity, ActivationActivity::class.java))
                return@launch
            }
            graph.preferences.onboardingCompleted = true
            ContextCompat.startForegroundService(
                this@OnboardingActivity,
                Intent(this@OnboardingActivity, XiaoyuAssistantService::class.java),
            )
            startActivity(Intent(this@OnboardingActivity, BackgroundCheckActivity::class.java))
            finish()
        }
    }

    private fun nextStep() {
        step++
        showStep()
    }

    private fun showStep() {
        when (step) {
            0 -> {
                titleView.text = "小鱼同学"
                descView.text = "灭屏也能语音控制的智能助手"
                actionBtn.text = "开始设置"
            }
            1 -> {
                titleView.text = "需要麦克风权限"
                descView.text = "用于唤醒词「小鱼同学」和语音对话"
                actionBtn.text = "授权麦克风"
            }
            2 -> {
                titleView.text = "需要通知权限"
                descView.text = "用于前台服务常驻通知，显示语音与播放状态"
                actionBtn.text = "授权通知"
            }
            3 -> {
                titleView.text = "绑定小智设备"
                descView.text = "未完成绑定不可进入下一步"
                actionBtn.text = "去绑定"
            }
            else -> {
                titleView.text = "后台能力检测"
                descView.text = "电池无限制、自启动、麦克风前台服务"
                actionBtn.text = "完成引导"
            }
        }
    }
}
