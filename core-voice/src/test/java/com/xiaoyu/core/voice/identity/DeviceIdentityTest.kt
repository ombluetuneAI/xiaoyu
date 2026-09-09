package com.xiaoyu.core.voice.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentityTest {
    @Test
    fun deriveMac_matches_probe_sample() {
        val deviceId = "550e8400-e29b-41d4-a716-446655440000"
        assertEquals("a2:a9:e1:ed:97:32", DeviceIdentity.deriveMac(deviceId))
    }

    @Test
    fun isValidMac_accepts_derived() {
        val mac = DeviceIdentity.deriveMac("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(DeviceIdentity.isValidMac(mac))
    }
}
