package com.xiaoyu.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.xiaoyu.app.ui.activation.ActivationActivity
import com.xiaoyu.app.ui.onboarding.OnboardingActivity
import com.xiaoyu.app.ui.settings.SettingsActivity
import com.xiaoyu.core.voice.ota.XiaozhiBindState
import com.xiaoyu.service.UserNotice
import com.xiaoyu.service.XiaoyuAppGraph
import com.xiaoyu.service.XiaoyuAssistantService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class LauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = XiaoyuAppGraph.get(this)
        val bindState = runBlocking(Dispatchers.IO) { graph.bindManager.refresh() }
        if (bindState == XiaozhiBindState.UNKNOWN) {
            UserNotice.toast(this, "无法连接小智服务")
        }

        if (bindState == XiaozhiBindState.BOUND) {
            if (!graph.preferences.onboardingCompleted) {
                graph.preferences.onboardingCompleted = true
            }
            Log.i(TAG, "BOUND user: starting FGS + opening VoiceHome")
            ContextCompat.startForegroundService(
                this,
                Intent(this, XiaoyuAssistantService::class.java),
            )
            startActivity(Intent(this, VoiceHomeActivity::class.java))
            if (intent.getBooleanExtra(EXTRA_TEST_PLAY, false)) {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, XiaoyuAssistantService::class.java)
                        .setAction(XiaoyuAssistantService.ACTION_TEST_PLAY),
                )
            }
            finish()
            return
        }

        val target = when {
            !graph.preferences.onboardingCompleted -> OnboardingActivity::class.java
            bindState == XiaozhiBindState.NEEDS_ACTIVATION -> ActivationActivity::class.java
            else -> SettingsActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
    }

    companion object {
        private const val TAG = "LauncherActivity"
        const val EXTRA_TEST_PLAY = "test_play"
    }
}
