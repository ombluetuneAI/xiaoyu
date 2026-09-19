package com.xiaoyu.core.wake

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface WakeEngine {
    fun start()
    fun pause()
    fun resume()
    fun stop()
    /** 设置变更后重新加载 Sherpa（运行中可调用） */
    fun reloadConfiguration() {}
    /** 检测到唤醒词时 emit 中文唤醒词（如「小鱼同学」「小鱼小鱼」） */
    val wakeEvents: SharedFlow<String>
}

/** Sherpa-ONNX KWS 占位：调试时可手动 triggerWake() */
class StubWakeEngine : WakeEngine {
    private val _wakeEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val wakeEvents: SharedFlow<String> = _wakeEvents.asSharedFlow()

    private var running = false

    override fun start() {
        running = true
    }

    override fun pause() {
        running = false
    }

    override fun resume() {
        running = true
    }

    override fun stop() {
        running = false
    }

    fun triggerWake(wakeWord: String = WakeWords.DEFAULT) {
        if (running) _wakeEvents.tryEmit(wakeWord)
    }
}
