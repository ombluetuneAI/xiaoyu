package com.xiaoyu.core.voice.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.WebSocket
import okio.ByteString.Companion.toByteString
import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusDecoder
import io.github.jaredmdobson.concentus.OpusEncoder
import io.github.jaredmdobson.concentus.OpusException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opus 音频上下行：PCM 16 kHz mono → Opus 上行；下行 Opus → PCM 播放（采样率以 server hello 为准）。
 */
class OpusVoicePipeline {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var webSocket: WebSocket? = null
    private val capturing = AtomicBoolean(false)
    private var audioTrack: AudioTrack? = null

    private val uplinkSampleRate = 16000
    private val frameDurationMs = 60
    private val uplinkFrameSamples = uplinkSampleRate * frameDurationMs / 1000 // 960

    private val encoder = OpusEncoder(uplinkSampleRate, 1, OpusApplication.OPUS_APPLICATION_VOIP).apply {
        setBitrate(32000)
    }

    @Volatile
    private var downlinkSampleRate = 24000

    @Volatile
    private var decoder: OpusDecoder = OpusDecoder(downlinkSampleRate, 1)

    fun configureDownlink(sampleRate: Int) {
        if (sampleRate <= 0 || sampleRate == downlinkSampleRate) return
        downlinkSampleRate = sampleRate
        decoder = OpusDecoder(sampleRate, 1)
        stopPlayback()
    }

    fun attach(webSocket: WebSocket) {
        this.webSocket = webSocket
    }

    fun detach() {
        stopCapture()
        stopPlayback()
        webSocket = null
    }

    @SuppressLint("MissingPermission")
    fun startCapture() {
        if (capturing.getAndSet(true)) return
        captureJob = scope.launch {
            val bufferSize = AudioRecord.getMinBufferSize(
                uplinkSampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (bufferSize <= 0) {
                capturing.set(false)
                return@launch
            }
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                uplinkSampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2,
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                capturing.set(false)
                return@launch
            }

            val pcmFrame = ShortArray(uplinkFrameSamples)
            var frameFill = 0
            val encodeBuf = ByteArray(1275)

            recorder.startRecording()
            try {
                val readBuf = ShortArray(bufferSize / 2)
                while (isActive && capturing.get()) {
                    val read = recorder.read(readBuf, 0, readBuf.size)
                    if (read <= 0) continue
                    var offset = 0
                    while (offset < read) {
                        val toCopy = minOf(read - offset, uplinkFrameSamples - frameFill)
                        readBuf.copyInto(pcmFrame, frameFill, offset, offset + toCopy)
                        frameFill += toCopy
                        offset += toCopy
                        if (frameFill >= uplinkFrameSamples) {
                            try {
                                val encodedLen = encoder.encode(
                                    pcmFrame, 0, uplinkFrameSamples,
                                    encodeBuf, 0, encodeBuf.size,
                                )
                                if (encodedLen > 0) {
                                    webSocket?.send(encodeBuf.toByteString(0, encodedLen))
                                }
                            } catch (_: OpusException) {
                                // skip bad frame
                            }
                            frameFill = 0
                        }
                    }
                }
            } finally {
                recorder.stop()
                recorder.release()
                capturing.set(false)
            }
        }
    }

    fun stopCapture() {
        capturing.set(false)
        captureJob?.cancel()
        captureJob = null
    }

    fun playDownlink(data: ByteArray) {
        if (data.isEmpty()) return
        val maxSamples = 5760
        val pcm = ShortArray(maxSamples)
        val samplesDecoded = try {
            decoder.decode(data, 0, data.size, pcm, 0, maxSamples, false)
        } catch (_: OpusException) {
            return
        } catch (_: Exception) {
            return
        }
        if (samplesDecoded <= 0) return
        ensurePlaybackTrack()
        val bytes = ShortArray(samplesDecoded).also { pcm.copyInto(it, 0, 0, samplesDecoded) }
        val out = ByteArray(samplesDecoded * 2)
        for (i in 0 until samplesDecoded) {
            val s = bytes[i]
            out[i * 2] = (s.toInt() and 0xFF).toByte()
            out[i * 2 + 1] = (s.toInt() shr 8 and 0xFF).toByte()
        }
        audioTrack?.write(out, 0, out.size)
    }

    fun stopPlayback() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    private fun ensurePlaybackTrack() {
        if (audioTrack != null) return
        val bufferSize = AudioTrack.getMinBufferSize(
            downlinkSampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(downlinkSampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()
    }

    fun shutdown() {
        detach()
        scope.cancel()
    }
}
