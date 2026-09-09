package com.xiaoyu.core.voice.ota

import org.junit.Assert.assertEquals
import org.junit.Test

class OtaBindStateTest {
    @Test
    fun activationPresent_meansNeedsActivation() {
        val state = parseOtaBindState(
            OtaResponse(
                websocket = OtaWebSocket("wss://x", "token"),
                activation = OtaActivation("123456"),
            ),
        )
        assertEquals(XiaozhiBindState.NEEDS_ACTIVATION, state)
    }

    @Test
    fun tokenWithoutActivation_meansBound() {
        val state = parseOtaBindState(
            OtaResponse(
                websocket = OtaWebSocket("wss://x", "token"),
                activation = null,
            ),
        )
        assertEquals(XiaozhiBindState.BOUND, state)
    }
}
