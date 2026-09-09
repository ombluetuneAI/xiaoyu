package com.xiaoyu.app.ui.media

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xiaoyu.app.R
import com.xiaoyu.service.XiaoyuAppGraph
import kotlinx.coroutines.launch

class QueueActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_queue)
        val container = findViewById<LinearLayout>(R.id.queueContainer)
        val label = findViewById<TextView>(R.id.queueLabel)
        val graph = XiaoyuAppGraph.get(this)
        lifecycleScope.launch {
            graph.queueManager.queueLabel.collect { label.text = it ?: "" }
        }
        lifecycleScope.launch {
            graph.queueManager.tracks.collect { tracks ->
                val current = graph.queueManager.currentIndex.value
                container.removeAllViews()
                tracks.forEachIndexed { index, track ->
                    val row = TextView(this@QueueActivity).apply {
                        text = "${if (index == current) "▶ " else ""}${index + 1}. ${track.title} · ${track.artist}"
                        setTextColor(getColor(if (index == current) R.color.xiaoyu_primary else R.color.xiaoyu_text))
                        setPadding(0, 16, 0, 16)
                        setOnClickListener {
                            graph.queueManager.jumpTo(index)
                            graph.playerFacade.playQueue(index)
                            finish()
                        }
                    }
                    container.addView(row)
                }
            }
        }
    }
}
