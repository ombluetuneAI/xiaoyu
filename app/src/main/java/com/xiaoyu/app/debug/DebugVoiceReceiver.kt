package com.xiaoyu.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.xiaoyu.service.XiaoyuAssistantService

/** Debug-only：adb am broadcast -a com.xiaoyu.debug.WAKE -p com.xiaoyu.app */
class DebugVoiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_WAKE -> deliver(context, XiaoyuAssistantService.ACTION_WAKE_FROM_NOTIFICATION)
            ACTION_END -> deliver(context, XiaoyuAssistantService.ACTION_END_SESSION)
            ACTION_CONNECT -> deliver(context, XiaoyuAssistantService.ACTION_CONNECT_VOICE)
            ACTION_SIMULATE_KWS -> deliver(context, XiaoyuAssistantService.ACTION_DEBUG_SIMULATE_KWS)
            ACTION_PLAY_WAKE_TTS -> deliver(context, XiaoyuAssistantService.ACTION_DEBUG_PLAY_WAKE_TTS)
        }
    }

    /**
     * FGS 已在跑时用 [Context.startService] 投递指令，避免后台 Broadcast 触发
     * ForegroundServiceStartNotAllowedException。
     */
    private fun deliver(context: Context, action: String) {
        val serviceIntent = Intent(context, XiaoyuAssistantService::class.java).setAction(action)
        try {
            val cn = context.startService(serviceIntent)
            if (cn != null) return
        } catch (e: Exception) {
            Log.w(TAG, "startService failed action=$action: ${e.message}")
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "startForegroundService failed action=$action", e)
        }
    }

    companion object {
        private const val TAG = "DebugVoiceReceiver"
        const val ACTION_WAKE = "com.xiaoyu.debug.WAKE"
        const val ACTION_END = "com.xiaoyu.debug.END_SESSION"
        const val ACTION_CONNECT = "com.xiaoyu.debug.CONNECT_VOICE"
        const val ACTION_SIMULATE_KWS = "com.xiaoyu.debug.SIMULATE_KWS"
        const val ACTION_PLAY_WAKE_TTS = "com.xiaoyu.debug.PLAY_WAKE_TTS"
    }
}
