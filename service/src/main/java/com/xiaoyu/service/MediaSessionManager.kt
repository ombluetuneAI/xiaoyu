package com.xiaoyu.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.xiaoyu.core.media.player.MediaPlayerFacade
import com.xiaoyu.core.media.queue.MusicQueueManager

class MediaSessionManager(
    private val context: Context,
    private val playerFacade: MediaPlayerFacade,
    private val queueManager: MusicQueueManager,
) {
    val mediaSession: MediaSession = MediaSession.Builder(context, playerFacade.player)
        .setId("xiaoyu_media")
        .build()

    init {
        playerFacade.player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateNotification()
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                updateNotification()
            }
        })
        playerFacade.onTrackChanged = { updateNotification() }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun updateNotification() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateNotificationInternal() }
            return
        }
        updateNotificationInternal()
    }

    private fun updateNotificationInternal() {
        val track = queueManager.currentTrack()
        val title = track?.title ?: "小鱼助手"
        val artist = track?.artist ?: "暂无播放"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(MEDIA_NOTIFICATION_ID, buildMediaNotification(title, artist))
    }

    fun hideNotification() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancel(MEDIA_NOTIFICATION_ID)
    }

    private fun buildMediaNotification(title: String, artist: String): Notification {
        val playerIntent = PendingIntent.getActivity(
            context,
            2,
            Intent().setClassName(context.packageName, "com.xiaoyu.app.ui.media.PlayerActivity"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val prevIntent = mediaActionPendingIntent(MediaControlReceiver.ACTION_PREVIOUS, 10)
        val toggleIntent = mediaActionPendingIntent(MediaControlReceiver.ACTION_TOGGLE_PLAY, 11)
        val nextIntent = mediaActionPendingIntent(MediaControlReceiver.ACTION_NEXT, 12)
        return NotificationCompat.Builder(context, XiaoyuNotifications.CHANNEL_MEDIA_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(playerIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .addAction(android.R.drawable.ic_media_previous, "上一首", prevIntent)
            .addAction(
                if (playerFacade.isPlaying()) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playerFacade.isPlaying()) "暂停" else "播放",
                toggleIntent,
            )
            .addAction(android.R.drawable.ic_media_next, "下一首", nextIntent)
            .setOngoing(playerFacade.isPlaying())
            .build()
    }

    private fun mediaActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MediaControlReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun release() {
        hideNotification()
        mediaSession.release()
    }

    companion object {
        const val MEDIA_NOTIFICATION_ID = 1002
    }
}
