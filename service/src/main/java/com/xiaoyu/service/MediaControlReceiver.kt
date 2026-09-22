package com.xiaoyu.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiaoyu.core.router.MediaSource

class MediaControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = XiaoyuAppGraph.get(context)
        when (intent.action) {
            ACTION_PREVIOUS -> handlePrevious(graph)
            ACTION_TOGGLE_PLAY -> handleToggle(graph)
            ACTION_NEXT -> handleNext(graph)
        }
    }

    private fun handleToggle(graph: XiaoyuAppGraph) {
        if (graph.activeMediaSource.current == MediaSource.XIAOYU) {
            if (graph.playerFacade.isPlaying()) graph.playerFacade.pauseForUserRequest() else graph.playerFacade.resume()
            graph.mediaSessionManager.updateNotification()
        }
    }

    private fun handleNext(graph: XiaoyuAppGraph) {
        if (graph.activeMediaSource.current != MediaSource.XIAOYU) return
        val next = graph.queueManager.next()
        if (next != null) {
            graph.playerFacade.playTrack(next, graph.queueManager.mode.value)
            graph.mediaSessionManager.updateNotification()
        }
    }

    private fun handlePrevious(graph: XiaoyuAppGraph) {
        if (graph.activeMediaSource.current != MediaSource.XIAOYU) return
        val prev = graph.queueManager.previous()
        if (prev != null) {
            graph.playerFacade.playTrack(prev, graph.queueManager.mode.value)
            graph.mediaSessionManager.updateNotification()
        }
    }

    companion object {
        const val ACTION_PREVIOUS = "com.xiaoyu.action.MEDIA_PREVIOUS"
        const val ACTION_TOGGLE_PLAY = "com.xiaoyu.action.MEDIA_TOGGLE"
        const val ACTION_NEXT = "com.xiaoyu.action.MEDIA_NEXT"
    }
}
