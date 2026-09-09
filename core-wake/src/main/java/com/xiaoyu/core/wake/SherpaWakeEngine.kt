package com.xiaoyu.core.wake

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Sherpa-ONNX KWS：assets 含 wenetspeech zipformer 模型时走 KeywordSpotter；
 * pause 时释放麦克风，避免与 Opus 上行抢麦。
 */
class SherpaWakeEngine(
    private val context: Context? = null,
) : WakeEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _wakeEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val wakeEvents: SharedFlow<String> = _wakeEvents.asSharedFlow()

    private var job: Job? = null
    @Volatile
    private var paused = false
    private var consecutiveHighFrames = 0
    private var lastWakeMs = 0L

    private var kws: KeywordSpotter? = null
    private var kwsStream: OnlineStream? = null

    override fun start() {
        if (job?.isActive == true) return
        val onnxReady = ensureSherpa()
        Log.i(TAG, "KWS start mode=${if (onnxReady) "sherpa-onnx" else "energy-fallback"}")
        job = scope.launch { captureLoop(onnxReady) }
    }

    override fun pause() {
        paused = true
        consecutiveHighFrames = 0
        Log.d(TAG, "KWS paused (mic will be released)")
    }

    override fun resume() {
        if (!paused) return
        paused = false
        consecutiveHighFrames = 0
        lastWakeMs = 0L
        Log.d(TAG, "KWS resumed")
    }

    override fun stop() {
        job?.cancel()
        job = null
        paused = false
        consecutiveHighFrames = 0
        kwsStream?.release()
        kwsStream = null
        kws?.release()
        kws = null
        Log.i(TAG, "KWS stopped")
    }

    /** 调试/通知栏手动触发唤醒 */
    fun triggerWake(wakeWord: String = WakeWords.DEFAULT) {
        if (paused) return
        Log.i(TAG, "manual triggerWake word=$wakeWord")
        _wakeEvents.tryEmit(wakeWord)
    }

    private fun ensureSherpa(): Boolean {
        if (kws != null) return true
        val ctx = context ?: return false
        if (!hasModelAssets(ctx)) return false

        val keywordsPath = "$MODEL_DIR/keywords.txt"
        if (WakeWords.hasUtf8Bom(ctx, keywordsPath)) {
            Log.e(TAG, "keywords.txt has UTF-8 BOM, skip Sherpa init to avoid native exit")
            return false
        }
        if (!WakeWords.loadFromAsset(ctx, keywordsPath)) {
            Log.e(TAG, "failed to parse wake words from $keywordsPath")
            return false
        }

        return try {
            val config = KeywordSpotterConfig(
                featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = kwsModelConfig(),
                keywordsFile = keywordsPath,
            )
            kws = KeywordSpotter(assetManager = ctx.assets, config = config)
            kwsStream = kws!!.createStream()
            Log.i(TAG, "Sherpa KeywordSpotter initialized words=${WakeWords.ALL}")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Sherpa init failed, fallback to energy", e)
            kwsStream?.release()
            kwsStream = null
            kws?.release()
            kws = null
            false
        }
    }

    private fun kwsModelConfig(): OnlineModelConfig {
        val prefix = MODEL_DIR
        return OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = "$prefix/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                decoder = "$prefix/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                joiner = "$prefix/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            ),
            tokens = "$prefix/tokens.txt",
            numThreads = 1,
            debug = false,
            provider = "cpu",
            modelType = "zipformer2",
        )
    }

    private fun hasModelAssets(ctx: Context): Boolean {
        return try {
            val files = ctx.assets.list(MODEL_DIR) ?: return false
            files.contains("encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx") &&
                files.contains("decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx") &&
                files.contains("joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx") &&
                files.contains("tokens.txt")
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun captureLoop(useOnnx: Boolean) {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (bufferSize <= 0) {
            Log.e(TAG, "AudioRecord bufferSize invalid")
            return
        }

        var recorder: AudioRecord? = null
        val buffer = ShortArray(bufferSize)

        fun releaseRecorder() {
            recorder?.let {
                try {
                    if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
                } catch (_: Exception) {
                }
                it.release()
            }
            recorder = null
        }

        fun ensureRecorder(): AudioRecord? {
            if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) return recorder
            releaseRecorder()
            val created = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2,
            )
            if (created.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord init failed")
                created.release()
                return null
            }
            created.startRecording()
            recorder = created
            Log.i(TAG, "AudioRecord started")
            return created
        }

        try {
            while (scope.isActive) {
                if (paused) {
                    consecutiveHighFrames = 0
                    releaseRecorder()
                    delay(PAUSE_POLL_MS)
                } else {
                    val activeRecorder = ensureRecorder()
                    if (activeRecorder == null) {
                        delay(PAUSE_POLL_MS)
                    } else {
                        val read = activeRecorder.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            if (useOnnx && kws != null && kwsStream != null) {
                                processOnnx(buffer, read)
                            } else {
                                processEnergy(buffer, read)
                            }
                        }
                        delay(FRAME_POLL_MS)
                    }
                }
            }
        } finally {
            releaseRecorder()
        }
    }

    private suspend fun processOnnx(buffer: ShortArray, read: Int): Boolean {
        val spotter = kws ?: return false
        val stream = kwsStream ?: return false
        val samples = FloatArray(read) { buffer[it] / 32768.0f }
        stream.acceptWaveform(samples, sampleRate = SAMPLE_RATE)
        while (spotter.isReady(stream)) {
            spotter.decode(stream)
            val keyword = spotter.getResult(stream).keyword
            if (keyword.isNotBlank()) {
                val now = System.currentTimeMillis()
                if (now - lastWakeMs >= DEBOUNCE_MS) {
                    lastWakeMs = now
                    val wakeWord = WakeWords.normalize(keyword)
                    Log.i(TAG, "Sherpa wake keyword=$keyword -> $wakeWord")
                    spotter.reset(stream)
                    _wakeEvents.emit(wakeWord)
                }
                return true
            }
        }
        return false
    }

    private suspend fun processEnergy(buffer: ShortArray, read: Int): Boolean {
        val rms = computeRms(buffer, read)
        if (rms > ENERGY_THRESHOLD) {
            consecutiveHighFrames++
            if (consecutiveHighFrames >= DEBOUNCE_FRAMES) {
                val now = System.currentTimeMillis()
                if (now - lastWakeMs >= DEBOUNCE_MS) {
                    lastWakeMs = now
                    consecutiveHighFrames = 0
                    Log.i(TAG, "energy wake rms=${rms.toInt()}")
                    _wakeEvents.emit(WakeWords.DEFAULT)
                    return true
                }
            }
        } else {
            consecutiveHighFrames = maxOf(0, consecutiveHighFrames - 1)
        }
        return false
    }

    private fun computeRms(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            val v = buffer[i].toDouble()
            sum += v * v
        }
        return kotlin.math.sqrt(sum / length)
    }

    companion object {
        private const val TAG = "SherpaWakeEngine"
        private const val SAMPLE_RATE = 16000
        private const val MODEL_DIR = "sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01"
        private const val ENERGY_THRESHOLD = 2500.0
        private const val DEBOUNCE_FRAMES = 4
        private const val DEBOUNCE_MS = 2000L
        private const val FRAME_POLL_MS = 20L
        private const val PAUSE_POLL_MS = 200L
    }
}
