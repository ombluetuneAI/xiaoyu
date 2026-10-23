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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        graph = XiaoyuAppGraph.get(this)

        val title = findViewById<TextView>(R.id.trackTitle)
        val artist = findViewById<TextView>(R.id.trackArtist)
        val cover = findViewById<ImageView>(R.id.coverArt)
        val progressBar = findViewById<SeekBar>(R.id.progressBar)
        val positionText = findViewById<TextView>(R.id.positionText)
        val durationText = findViewById<TextView>(R.id.durationText)
        val progressSection = findViewById<View>(R.id.progressSection)
        val btnPlayPause = findViewById<ImageButton>(R.id.btnPlayPause)
        val btnPrevious = findViewById<ImageButton>(R.id.btnPrevious)
        val btnNext = findViewById<ImageButton>(R.id.btnNext)
        val btnQueueOrRadio = findViewById<ImageButton>(R.id.btnQueueOrRadio)
        val btnMode = findViewById<ImageButton>(R.id.btnMode)
        val emptyStatePanel = findViewById<View>(R.id.emptyStatePanel)
        val errorStatePanel = findViewById<View>(R.id.errorStatePanel)
        val errorMessage = findViewById<TextView>(R.id.errorMessage)

        graph.playerFacade.onError = {
            playbackError = true
            refreshUi(
                title, artist, cover, progressBar, positionText, durationText, progressSection,
                btnPlayPause, btnPrevious, btnNext, btnQueueOrRadio, btnMode,
                emptyStatePanel, errorStatePanel, errorMessage,
            )
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
            refreshUi(
                title, artist, cover, progressBar, positionText, durationText, progressSection,
                btnPlayPause, btnPrevious, btnNext, btnQueueOrRadio, btnMode,
                emptyStatePanel, errorStatePanel, errorMessage,
            )
        }

        btnPlayPause.setOnClickListener {
            if (graph.playerFacade.isPlaying()) graph.playerFacade.pauseForUserRequest() else graph.playerFacade.resume()
            refreshUi(
                title, artist, cover, progressBar, positionText, durationText, progressSection,
                btnPlayPause, btnPrevious, btnNext, btnQueueOrRadio, btnMode,
                emptyStatePanel, errorStatePanel, errorMessage,
            )
        }
        btnPrevious.setOnClickListener {
            val prev = graph.queueManager.previous()
            if (prev != null) graph.playerFacade.playTrack(prev, graph.queueManager.mode.value)
            refreshUi(
                title, artist, cover, progressBar, positionText, durationText, progressSection,
                btnPlayPause, btnPrevious, btnNext, btnQueueOrRadio, btnMode,
                emptyStatePanel, errorStatePanel, errorMessage,
            )
        }
        btnNext.setOnClickListener {
            val next = graph.queueManager.next()
            if (next != null) graph.playerFacade.playTrack(next, graph.queueManager.mode.value)
            refreshUi(
                title, artist, cover, progressBar, positionText, durationText, progressSection,
                btnPlayPause, btnPrevious, btnNext, btnQueueOrRadio, btnMode,
                emptyStatePanel, errorStatePanel, errorMessage,
            )
        }
        btnMode.setOnClickListener {
            val mode = graph.queueManager.mode.value
            if (mode == PlaybackMode.RADIO) {
                switchRadioStation()
            } else {
                graph.queueManager.cycleOrderMode()
                refreshUi(
                    title, artist, cover, progressBar, positionText, durationText, progressSection,
                    btnPlayPause, btnPrevious, btnNext, btnQueueOrRadio, btnMode,
                    emptyStatePanel, errorStatePanel, errorMessage,
                )
            }
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
            graph.queueManager.nowPlaying.collect {
                refreshUi(
                    title, artist, cover, progressBar, positionText, durationText, progressSection,
                    btnPlayPause, btnPrevious, btnNext, btnQueueOrRadio, btnMode,
                    emptyStatePanel, errorStatePanel, errorMessage,
                )
            }
        }
        lifecycleScope.launch {
            graph.queueManager.orderMode.collect {
                refreshUi(
                    title, artist, cover, progressBar, positionText, durationText, progressSection,
                    btnPlayPause, btnPrevious, btnNext, btnQueueOrRadio, btnMode,
                    emptyStatePanel, errorStatePanel, errorMessage,
                )
            }
        }
        lifecycleScope.launch {
            while (isActive) {
                val duration = graph.playerFacade.durationMs().coerceAtLeast(1L)
                if (graph.queueManager.mode.value != PlaybackMode.RADIO) {
                    progressBar.max = duration.toInt()
                    progressBar.progress = graph.playerFacade.positionMs().toInt()
                    positionText.text = formatMs(graph.playerFacade.positionMs())
                    durationText.text = formatMs(duration)
                }
                delay(500)
            }
        }
        refreshUi(
            title, artist, cover, progressBar, positionText, durationText, progressSection,
            btnPlayPause, btnPrevious, btnNext, btnQueueOrRadio, btnMode,
            emptyStatePanel, errorStatePanel, errorMessage,
        )
    }

    private fun switchRadioStation() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { graph.mediaResolver.resolveRadioRandom() }
            if (result.ok && result.tracks.isNotEmpty()) {
                playbackError = false
                graph.queueManager.setQueue(result.tracks, result.mode, result.queueLabel)
                graph.playerFacade.playTrack(result.tracks.first(), result.mode)
                graph.mediaSessionManager.updateNotification()
            }
        }
    }

    private fun refreshUi(
        title: TextView,
        artist: TextView,
        cover: ImageView,
        progressBar: SeekBar,
        positionText: TextView,
        durationText: TextView,
        progressSection: View,
        btnPlayPause: ImageButton,
        btnPrevious: ImageButton,
        btnNext: ImageButton,
        btnQueueOrRadio: ImageButton,
        btnMode: ImageButton,
        emptyStatePanel: View,
        errorStatePanel: View,
        errorMessage: TextView,
    ) {
        val track = graph.queueManager.currentTrack()
        val mode = graph.queueManager.mode.value
        val isRadio = mode == PlaybackMode.RADIO

        if (track == null) {
            emptyStatePanel.visibility = View.VISIBLE
            errorStatePanel.visibility = View.GONE
            title.text = ""
            artist.text = ""
            return
        }
        emptyStatePanel.visibility = View.GONE

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
            btnMode.setImageResource(R.drawable.ic_radio_tune)
            btnQueueOrRadio.setImageResource(R.drawable.ic_radio_tune)
            btnQueueOrRadio.contentDescription = "换台"
        } else {
            btnQueueOrRadio.setImageResource(R.drawable.ic_queue)
            btnQueueOrRadio.contentDescription = getString(R.string.queue_title)
            when (graph.queueManager.orderMode.value) {
                QueueOrderMode.SEQUENTIAL -> btnMode.setImageResource(R.drawable.ic_repeat)
                QueueOrderMode.SHUFFLE -> btnMode.setImageResource(R.drawable.ic_shuffle)
                QueueOrderMode.LOOP_ONE -> btnMode.setImageResource(R.drawable.ic_repeat_one)
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
