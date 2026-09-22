package com.xiaoyu.app.ui.media

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.xiaoyu.app.R
import com.xiaoyu.core.media.PlaybackMode
import com.xiaoyu.service.XiaoyuAppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        val graph = XiaoyuAppGraph.get(this)
        val title = findViewById<TextView>(R.id.trackTitle)
        val artist = findViewById<TextView>(R.id.trackArtist)
        val cover = findViewById<ImageView>(R.id.coverArt)
        val progressBar = findViewById<SeekBar>(R.id.progressBar)
        val progressText = findViewById<TextView>(R.id.progressText)
        val btnPlayPause = findViewById<Button>(R.id.btnPlayPause)
        val btnPrevious = findViewById<Button>(R.id.btnPrevious)
        val btnNext = findViewById<Button>(R.id.btnNext)
        val btnQueue = findViewById<Button>(R.id.btnQueue)
        val btnRadioSwitch = findViewById<Button>(R.id.btnRadioSwitch)

        fun refreshUi() {
            val track = graph.queueManager.currentTrack()
            title.text = track?.title ?: "—"
            artist.text = track?.artist ?: ""
            val mode = graph.queueManager.mode.value
            if (mode == PlaybackMode.RADIO) {
                progressBar.isEnabled = false
                progressText.text = "电台直播"
                btnRadioSwitch.visibility = View.VISIBLE
                btnQueue.visibility = View.GONE
            } else {
                progressBar.isEnabled = mode != PlaybackMode.RADIO
                btnRadioSwitch.visibility = View.GONE
                btnQueue.visibility = if (mode == PlaybackMode.SINGLE) View.GONE else View.VISIBLE
            }
            val coverUrl = track?.coverUrl
            if (!coverUrl.isNullOrBlank()) {
                cover.load(coverUrl) {
                    crossfade(true)
                    placeholder(android.R.drawable.ic_media_play)
                    error(android.R.drawable.ic_media_play)
                }
            } else {
                cover.setImageResource(android.R.drawable.ic_media_play)
            }
            btnPlayPause.text = if (graph.playerFacade.isPlaying()) "⏸" else "▶"
        }

        btnPlayPause.setOnClickListener {
            if (graph.playerFacade.isPlaying()) graph.playerFacade.pauseForUserRequest() else graph.playerFacade.resume()
            refreshUi()
        }
        btnPrevious.setOnClickListener {
            val prev = graph.queueManager.previous()
            if (prev != null) graph.playerFacade.playTrack(prev, graph.queueManager.mode.value)
            refreshUi()
        }
        btnNext.setOnClickListener {
            val next = graph.queueManager.next()
            if (next != null) graph.playerFacade.playTrack(next, graph.queueManager.mode.value)
            refreshUi()
        }
        btnRadioSwitch.setOnClickListener {
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { graph.mediaResolver.resolveRadioRandom() }
                if (result.ok && result.tracks.isNotEmpty()) {
                    graph.queueManager.setQueue(result.tracks, result.mode, result.queueLabel)
                    graph.playerFacade.playTrack(result.tracks.first(), result.mode)
                    graph.mediaSessionManager.updateNotification()
                }
                refreshUi()
            }
        }
        progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) graph.playerFacade.seekTo(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        btnQueue.setOnClickListener {
            startActivity(Intent(this, QueueActivity::class.java))
        }

        lifecycleScope.launch {
            graph.queueManager.nowPlaying.collect { refreshUi() }
        }
        lifecycleScope.launch {
            while (isActive) {
                val duration = graph.playerFacade.durationMs().coerceAtLeast(1L)
                progressBar.max = duration.toInt()
                progressBar.progress = graph.playerFacade.positionMs().toInt()
                val pos = formatMs(graph.playerFacade.positionMs())
                val dur = formatMs(duration)
                if (graph.queueManager.mode.value != PlaybackMode.RADIO) {
                    progressText.text = "$pos / $dur"
                }
                delay(500)
            }
        }
        refreshUi()
    }

    private fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000).toInt()
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }
}
