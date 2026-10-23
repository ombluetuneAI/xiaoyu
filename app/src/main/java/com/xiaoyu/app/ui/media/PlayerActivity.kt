package com.xiaoyu.app.ui.media

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.xiaoyu.app.R
import com.xiaoyu.app.ui.VoiceHomeActivity
import com.xiaoyu.core.media.PlaybackMode
import com.xiaoyu.core.media.QueueOrderMode
import com.xiaoyu.service.XiaoyuAppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerActivity : AppCompatActivity() {
    private lateinit var graph: XiaoyuAppGraph
    private var playbackError = false

    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var cover: ImageView
    private lateinit var progressBar: SeekBar
    private lateinit var positionText: TextView
    private lateinit var durationText: TextView
    private lateinit var progressSection: View
    private lateinit var transportBar: View
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnQueueOrRadio: ImageButton
    private lateinit var btnMode: ImageButton
    private lateinit var emptyStatePanel: View
    private lateinit var errorStatePanel: View
    private lateinit var errorMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        graph = XiaoyuAppGraph.get(this)
        bindViews()

        graph.playerFacade.onError = {
            playbackError = true
            runOnUiThread { refreshUi() }
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnEmptyHome).setOnClickListener {
            startActivity(Intent(this, VoiceHomeActivity::class.java))
            finish()
        }
        findViewById<TextView>(R.id.btnRetry).setOnClickListener {
            playbackError = false
            val track = graph.queueManager.currentTrack()
            if (track != null) {
                graph.playerFacade.playTrack(track, graph.queueManager.mode.value)
            }
            refreshUi()
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
        btnMode.setOnClickListener {
            graph.queueManager.cycleOrderMode()
            refreshUi()
        }
        btnQueueOrRadio.setOnClickListener {
            if (graph.queueManager.mode.value == PlaybackMode.RADIO) {
                switchRadioStation()
            } else {
                startActivity(Intent(this, QueueActivity::class.java))
            }
        }
        progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) graph.playerFacade.seekTo(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        lifecycleScope.launch {
            graph.queueManager.nowPlaying.collect { refreshUi() }
        }
        lifecycleScope.launch {
            graph.queueManager.orderMode.collect { refreshUi() }
        }
        lifecycleScope.launch {
            while (isActive) {
                val track = graph.queueManager.currentTrack()
                if (track != null && graph.queueManager.mode.value != PlaybackMode.RADIO) {
                    val duration = graph.playerFacade.durationMs().coerceAtLeast(1L)
                    progressBar.max = duration.toInt()
                    progressBar.progress = graph.playerFacade.positionMs().toInt()
                    positionText.text = formatMs(graph.playerFacade.positionMs())
                    durationText.text = formatMs(duration)
                }
                delay(500)
            }
        }
        refreshUi()
    }

    private fun bindViews() {
        title = findViewById(R.id.trackTitle)
        artist = findViewById(R.id.trackArtist)
        cover = findViewById(R.id.coverArt)
        progressBar = findViewById(R.id.progressBar)
        positionText = findViewById(R.id.positionText)
        durationText = findViewById(R.id.durationText)
        progressSection = findViewById(R.id.progressSection)
        transportBar = findViewById(R.id.transportBar)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        btnQueueOrRadio = findViewById(R.id.btnQueueOrRadio)
        btnMode = findViewById(R.id.btnMode)
        emptyStatePanel = findViewById(R.id.emptyStatePanel)
        errorStatePanel = findViewById(R.id.errorStatePanel)
        errorMessage = findViewById(R.id.errorMessage)
    }

    private fun switchRadioStation() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { graph.mediaResolver.resolveRadioRandom() }
            if (result.ok && result.tracks.isNotEmpty()) {
                playbackError = false
                graph.queueManager.setQueue(result.tracks, result.mode, result.queueLabel)
                graph.playerFacade.playTrack(result.tracks.first(), result.mode)
                graph.mediaSessionManager.updateNotification()
                refreshUi()
            }
        }
    }

    private fun refreshUi() {
        val track = graph.queueManager.currentTrack()
        val isRadio = graph.queueManager.mode.value == PlaybackMode.RADIO

        if (track == null) {
            emptyStatePanel.visibility = View.VISIBLE
            errorStatePanel.visibility = View.GONE
            cover.visibility = View.GONE
            cover.setImageDrawable(null)
            progressSection.visibility = View.GONE
            transportBar.visibility = View.GONE
            title.text = getString(R.string.player_empty)
            artist.text = ""
            progressBar.progress = 0
            positionText.text = ""
            durationText.text = ""
            return
        }

        emptyStatePanel.visibility = View.GONE
        cover.visibility = View.VISIBLE
        transportBar.visibility = View.VISIBLE

        title.text = track.title
        artist.text = track.artist
        val coverUrl = track.coverUrl
        if (!coverUrl.isNullOrBlank()) {
            cover.load(coverUrl) {
                crossfade(true)
                placeholder(android.R.drawable.ic_media_play)
                error(android.R.drawable.ic_media_play)
            }
        } else {
            cover.setImageResource(android.R.drawable.ic_media_play)
        }

        if (playbackError) {
            errorStatePanel.visibility = View.VISIBLE
            errorMessage.text = getString(R.string.player_error)
        } else {
            errorStatePanel.visibility = View.GONE
        }

        progressSection.visibility = if (isRadio) View.GONE else View.VISIBLE
        progressBar.isEnabled = !isRadio

        btnPlayPause.setImageResource(
            if (graph.playerFacade.isPlaying()) R.drawable.ic_pause else R.drawable.ic_play,
        )
        btnPlayPause.contentDescription = getString(
            if (graph.playerFacade.isPlaying()) R.string.player_pause else R.string.player_play,
        )

        if (isRadio) {
            btnMode.visibility = View.INVISIBLE
            btnMode.isEnabled = false
            btnMode.contentDescription = null
            btnQueueOrRadio.visibility = View.VISIBLE
            btnQueueOrRadio.isEnabled = true
            btnQueueOrRadio.setImageResource(R.drawable.ic_radio_tune)
            btnQueueOrRadio.contentDescription = getString(R.string.player_radio_tune)
        } else {
            btnMode.visibility = View.VISIBLE
            btnMode.isEnabled = true
            btnQueueOrRadio.visibility = View.VISIBLE
            btnQueueOrRadio.isEnabled = true
            btnQueueOrRadio.setImageResource(R.drawable.ic_queue)
            btnQueueOrRadio.contentDescription = getString(R.string.queue_title)
            when (graph.queueManager.orderMode.value) {
                QueueOrderMode.SEQUENTIAL -> {
                    btnMode.setImageResource(R.drawable.ic_sequential)
                    btnMode.contentDescription = getString(R.string.player_mode_sequential)
                }
                QueueOrderMode.SHUFFLE -> {
                    btnMode.setImageResource(R.drawable.ic_shuffle)
                    btnMode.contentDescription = getString(R.string.player_mode)
                }
                QueueOrderMode.LOOP_ONE -> {
                    btnMode.setImageResource(R.drawable.ic_repeat_one)
                    btnMode.contentDescription = getString(R.string.player_mode)
                }
            }
        }
    }

    private fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000).toInt()
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }
}
