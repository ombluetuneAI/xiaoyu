package com.xiaoyu.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.xiaoyu.app.R
import com.xiaoyu.app.ui.VoiceHomeActivity
import com.xiaoyu.app.ui.activation.ActivationActivity
import com.xiaoyu.app.ui.settings.BackgroundCheckActivity
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
    private lateinit var secondaryBtn: Button
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
        secondaryBtn = findViewById(R.id.onboardingSecondary)
        actionBtn.setOnClickListener { onAction() }
        secondaryBtn.setOnClickListener { onSecondary() }

        step = intent.getIntExtra(EXTRA_START_STEP, savedInstanceState?.getInt(STATE_STEP) ?: 0)

        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) { graph.bindManager.refresh() }
            if (state == XiaozhiBindState.BOUND && graph.preferences.onboardingCompleted) {
                goVoiceHome()
                return@launch
            }
        }
        showStep()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_STEP, step)
    }

    override fun onResume() {
        super.onResume()
        if (step == 3) {
            lifecycleScope.launch {
                val state = withContext(Dispatchers.IO) { graph.bindManager.refresh() }
                if (state == XiaozhiBindState.BOUND) {
                    nextStep()
                }
            }
        }
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
            3 -> {
                startActivity(
                    Intent(this, ActivationActivity::class.java)
                        .putExtra(ActivationActivity.EXTRA_FROM_ONBOARDING, true),
                )
            }
            4 -> {
                startActivity(
                    Intent(this, BackgroundCheckActivity::class.java)
                        .putExtra(BackgroundCheckActivity.EXTRA_FINISH_TO_HOME, true),
                )
            }
            else -> finishToHome()
        }
    }

    private fun onSecondary() {
        when (step) {
            1 -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
            4 -> finishToHome()
        }
    }

    private fun finishToHome() {
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) { graph.bindManager.refresh() }
            if (state != XiaozhiBindState.BOUND) {
                Toast.makeText(this@OnboardingActivity, "请先完成小智绑定", Toast.LENGTH_SHORT).show()
                step = 3
                showStep()
                return@launch
            }
            graph.preferences.onboardingCompleted = true
            ContextCompat.startForegroundService(
                this@OnboardingActivity,
                Intent(this@OnboardingActivity, XiaoyuAssistantService::class.java),
            )
            goVoiceHome()
        }
    }

    private fun goVoiceHome() {
        startActivity(Intent(this, VoiceHomeActivity::class.java))
        finish()
    }

    private fun nextStep() {
        step++
        showStep()
    }

    private fun showStep() {
        secondaryBtn.visibility = View.GONE
        when (step) {
            0 -> {
                titleView.text = "小鱼同学"
                descView.text = "灭屏也能语音控制的智能助手"
                actionBtn.text = "开始设置"
            }
            1 -> {
                titleView.text = "需要麦克风权限"
                descView.text = "用于唤醒词「小鱼同学」和语音对话。拒绝后可在此页重试，不会跳过绑定。"
                actionBtn.text = "授权麦克风"
                secondaryBtn.visibility = View.VISIBLE
                secondaryBtn.text = "重试授权"
            }
            2 -> {
                titleView.text = "需要通知权限"
                descView.text = "用于前台服务常驻通知，显示语音与播放状态"
                actionBtn.text = "授权通知"
            }
            3 -> {
                titleView.text = "绑定小智设备"
                descView.text = "未完成绑定不可进入下一步。绑定失败请在本页重试。"
                actionBtn.text = "去绑定"
            }
            4 -> {
                titleView.text = "后台保活"
                descView.text = "电池无限制、自启动与麦克风前台服务，保障灭屏唤醒"
                actionBtn.text = "去检测"
                secondaryBtn.visibility = View.VISIBLE
                secondaryBtn.text = getString(R.string.keepalive_later)
            }
            else -> {
                titleView.text = "准备就绪"
                descView.text = "可以说「小鱼同学」唤醒，或点按首页「点按唤醒」"
                actionBtn.text = "进入首页"
            }
        }
    }

    companion object {
        const val EXTRA_START_STEP = "start_step"
        private const val STATE_STEP = "onboarding_step"
    }
}
