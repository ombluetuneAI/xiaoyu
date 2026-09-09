package com.xiaoyu.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object XiaoyuNotifications {
    const val CHANNEL_ID = "xiaoyu_voice"
    const val CHANNEL_MEDIA_ID = "xiaoyu_media"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "小鱼语音服务",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "前台语音与对话状态" },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MEDIA_ID,
                "小鱼媒体播放",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "MediaSession 播控通知" },
        )
    }
}
