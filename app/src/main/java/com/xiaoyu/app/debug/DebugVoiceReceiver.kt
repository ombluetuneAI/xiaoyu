package com.xiaoyu.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.xiaoyu.service.XiaoyuAssistantService

/** Debug-only：adb am broadcast -a com.xiaoyu.debug.WAKE -p com.xiaoyu.app */
class DebugVoiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_WAKE -> ContextCompat.startForegroundService(
                context,
                Intent(context, XiaoyuAssistantService::class.java)
                    .setAction(XiaoyuAssistantService.ACTION_WAKE_FROM_NOTIFICATION),
            )
            ACTION_END -> ContextCompat.startForegroundService(
                context,
                Intent(context, XiaoyuAssistantService::class.java)
                    .setAction(XiaoyuAssistantService.ACTION_END_SESSION),
            )
            ACTION_CONNECT -> ContextCompat.startForegroundService(
                context,
                Intent(context, XiaoyuAssistantService::class.java)
                    .setAction(XiaoyuAssistantService.ACTION_CONNECT_VOICE),
            )
            ACTION_SIMULATE_KWS -> ContextCompat.startForegroundService(
                context,
                Intent(context, XiaoyuAssistantService::class.java)
                    .setAction(XiaoyuAssistantService.ACTION_DEBUG_SIMULATE_KWS),
            )
            ACTION_PLAY_WAKE_TTS -> ContextCompat.startForegroundService(
                context,
                Intent(context, XiaoyuAssistantService::class.java)
                    .setAction(XiaoyuAssistantService.ACTION_DEBUG_PLAY_WAKE_TTS),
            )
        }
    }

    companion object {
        const val ACTION_WAKE = "com.xiaoyu.debug.WAKE"
        const val ACTION_END = "com.xiaoyu.debug.END_SESSION"
        const val ACTION_CONNECT = "com.xiaoyu.debug.CONNECT_VOICE"
        const val ACTION_SIMULATE_KWS = "com.xiaoyu.debug.SIMULATE_KWS"
        const val ACTION_PLAY_WAKE_TTS = "com.xiaoyu.debug.PLAY_WAKE_TTS"
    }
}
