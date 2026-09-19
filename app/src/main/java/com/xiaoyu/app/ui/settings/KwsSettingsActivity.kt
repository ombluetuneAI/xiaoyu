package com.xiaoyu.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.xiaoyu.app.R
import com.xiaoyu.core.wake.KwsTuning
import com.xiaoyu.service.XiaoyuAppGraph
import com.xiaoyu.service.XiaoyuAssistantService
import kotlin.math.roundToInt

class KwsSettingsActivity : AppCompatActivity() {
    private lateinit var sensitivitySeek: SeekBar
    private lateinit var scoreSeek: SeekBar
    private lateinit var sensitivityLabel: TextView
    private lateinit var scoreLabel: TextView
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kws_settings)
        val prefs = XiaoyuAppGraph.get(this).preferences

        sensitivitySeek = findViewById(R.id.kwsSensitivitySeek)
        scoreSeek = findViewById(R.id.kwsScoreSeek)
        sensitivityLabel = findViewById(R.id.kwsSensitivityLabel)
        scoreLabel = findViewById(R.id.kwsScoreLabel)
        summary = findViewById(R.id.kwsSummary)

        loadFromPrefs(prefs.kwsTuning())

        val refreshLabels = {
            val threshold = progressToThreshold(sensitivitySeek.progress)
            val score = progressToScore(scoreSeek.progress)
            sensitivityLabel.text = getString(
                R.string.kws_sensitivity_label,
                sensitivitySeek.progress,
                threshold,
            )
            scoreLabel.text = getString(R.string.kws_score_label, score)
            summary.text = getString(R.string.kws_summary, score, threshold)
        }

        sensitivitySeek.setOnSeekBarChangeListener(simpleSeekListener(refreshLabels))
        scoreSeek.setOnSeekBarChangeListener(simpleSeekListener(refreshLabels))
        refreshLabels()

        findViewById<Button>(R.id.kwsPresetNoisy).setOnClickListener {
            loadFromPrefs(KwsTuning.NOISY_ENV)
            refreshLabels()
        }
        findViewById<Button>(R.id.kwsPresetDefault).setOnClickListener {
            loadFromPrefs(KwsTuning.DEFAULT)
            refreshLabels()
        }
        findViewById<Button>(R.id.kwsSave).setOnClickListener {
            prefs.kwsKeywordsThreshold = progressToThreshold(sensitivitySeek.progress)
            prefs.kwsKeywordsScore = progressToScore(scoreSeek.progress)
            ContextCompat.startForegroundService(
                this,
                Intent(this, XiaoyuAssistantService::class.java)
                    .setAction(XiaoyuAssistantService.ACTION_RELOAD_KWS),
            )
            Toast.makeText(this, R.string.kws_saved_toast, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadFromPrefs(tuning: KwsTuning) {
        sensitivitySeek.progress = thresholdToSensitivityProgress(tuning.keywordsThreshold)
        scoreSeek.progress = scoreToProgress(tuning.keywordsScore)
    }

    private fun simpleSeekListener(onChange: () -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onChange()
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    companion object {
        private fun progressToThreshold(progress: Int): Float {
            val ratio = progress.coerceIn(0, 100) / 100f
            val value = KwsTuning.MAX_THRESHOLD -
                ratio * (KwsTuning.MAX_THRESHOLD - KwsTuning.MIN_THRESHOLD)
            return (value * 100).roundToInt() / 100f
        }

        private fun thresholdToSensitivityProgress(threshold: Float): Int {
            val t = threshold.coerceIn(KwsTuning.MIN_THRESHOLD, KwsTuning.MAX_THRESHOLD)
            val ratio = (KwsTuning.MAX_THRESHOLD - t) / (KwsTuning.MAX_THRESHOLD - KwsTuning.MIN_THRESHOLD)
            return (ratio * 100).roundToInt().coerceIn(0, 100)
        }

        private fun progressToScore(progress: Int): Float {
            val raw = (progress.coerceIn(0, 30) + 10) / 10f
            return raw.coerceIn(KwsTuning.MIN_SCORE, KwsTuning.MAX_SCORE)
        }

        private fun scoreToProgress(score: Float): Int {
            val s = score.coerceIn(KwsTuning.MIN_SCORE, KwsTuning.MAX_SCORE)
            return ((s * 10).roundToInt() - 10).coerceIn(0, 30)
        }
    }
}
