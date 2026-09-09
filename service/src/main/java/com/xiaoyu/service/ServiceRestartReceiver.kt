package com.xiaoyu.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 服务被杀后 15 分钟尝试拉活 FGS（S-07 保活辅助）。
 */
class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RESTART) return
        Log.i(TAG, "AlarmManager restart FGS")
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, XiaoyuAssistantService::class.java),
            )
        } catch (e: Exception) {
            Log.w(TAG, "restart FGS failed: ${e.message}")
        }
        ServiceRestartScheduler.schedule(context)
    }

    companion object {
        private const val TAG = "ServiceRestartReceiver"
        const val ACTION_RESTART = "com.xiaoyu.action.SERVICE_RESTART"
    }
}

object ServiceRestartScheduler {
    private const val INTERVAL_MS = 15 * 60 * 1000L

    fun schedule(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ServiceRestartReceiver::class.java).setAction(ServiceRestartReceiver.ACTION_RESTART)
        val pi = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + INTERVAL_MS,
            pi,
        )
    }
}
