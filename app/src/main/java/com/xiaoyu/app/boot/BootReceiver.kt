package com.xiaoyu.app.boot

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import com.xiaoyu.service.XiaoyuAssistantService
import com.xiaoyu.service.XiaoyuAppGraph

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val graph = XiaoyuAppGraph.get(context)
        if (!graph.preferences.backgroundVoiceEnabled) return
        context.startForegroundService(Intent(context, XiaoyuAssistantService::class.java))
    }
}
