package com.xiaoyu.core.voice.identity

import java.security.MessageDigest

object DeviceIdentity {
    fun deriveMac(deviceId: String): String {
        val normalized = deviceId.trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        val b0 = (digest[0].toInt() and 0xFC) or 0x02
        return listOf(b0, digest[1], digest[2], digest[3], digest[4], digest[5])
            .joinToString(":") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    fun isValidMac(mac: String): Boolean {
        return mac.matches(Regex("^([0-9a-f]{2}:){5}[0-9a-f]{2}$"))
    }
}
