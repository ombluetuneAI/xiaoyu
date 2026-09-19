package com.xiaoyu.core.voice.prefs

import android.content.Context
import com.xiaoyu.core.wake.KwsTuning
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

class XiaoyuPreferences(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "xiaoyu_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    val clientId: String
        get() = prefs.getString(KEY_CLIENT_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_CLIENT_ID, it).apply()
        }

    var txbApiBase: String
        get() = prefs.getString(KEY_TXB_BASE, DEFAULT_TXB) ?: DEFAULT_TXB
        set(value) = prefs.edit().putString(KEY_TXB_BASE, value).apply()

    var continuousDialog: Boolean
        get() = prefs.getBoolean(KEY_CONTINUOUS, true)
        set(value) = prefs.edit().putBoolean(KEY_CONTINUOUS, value).apply()

    var followUpTimeoutSec: Int
        get() = prefs.getInt(KEY_FOLLOW_UP, 10).coerceAtLeast(10)
        set(value) = prefs.edit().putInt(KEY_FOLLOW_UP, value.coerceAtLeast(10)).apply()

    var backgroundVoiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_BG_VOICE, true)
        set(value) = prefs.edit().putBoolean(KEY_BG_VOICE, value).apply()

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    var kwsKeywordsScore: Float
        get() = prefs.getFloat(KEY_KWS_SCORE, KwsTuning.DEFAULT.keywordsScore)
            .coerceIn(KwsTuning.MIN_SCORE, KwsTuning.MAX_SCORE)
        set(value) = prefs.edit()
            .putFloat(KEY_KWS_SCORE, value.coerceIn(KwsTuning.MIN_SCORE, KwsTuning.MAX_SCORE))
            .apply()

    var kwsKeywordsThreshold: Float
        get() = prefs.getFloat(KEY_KWS_THRESHOLD, KwsTuning.DEFAULT.keywordsThreshold)
            .coerceIn(KwsTuning.MIN_THRESHOLD, KwsTuning.MAX_THRESHOLD)
        set(value) = prefs.edit()
            .putFloat(KEY_KWS_THRESHOLD, value.coerceIn(KwsTuning.MIN_THRESHOLD, KwsTuning.MAX_THRESHOLD))
            .apply()

    fun kwsTuning(): KwsTuning = KwsTuning(
        keywordsScore = kwsKeywordsScore,
        keywordsThreshold = kwsKeywordsThreshold,
    )

    fun applyKwsPreset(preset: KwsTuning) {
        kwsKeywordsScore = preset.keywordsScore
        kwsKeywordsThreshold = preset.keywordsThreshold
    }

    fun resetKwsTuning() = applyKwsPreset(KwsTuning.DEFAULT)

    /** 曾保存过偏严阈值时，一次性提升到「嘈杂环境」预设并触发 KWS 重载 */
    fun migrateKwsSensitivityIfNeeded(): Boolean {
        if (prefs.getBoolean(KEY_KWS_MIGRATED_SENSITIVE, false)) return false
        val threshold = prefs.getFloat(KEY_KWS_THRESHOLD, KwsTuning.DEFAULT.keywordsThreshold)
        val score = prefs.getFloat(KEY_KWS_SCORE, KwsTuning.DEFAULT.keywordsScore)
        val strict = threshold >= 0.32f || score >= 2.8f
        if (strict) {
            applyKwsPreset(KwsTuning.NOISY_ENV)
        }
        prefs.edit().putBoolean(KEY_KWS_MIGRATED_SENSITIVE, true).apply()
        return strict
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_TXB_BASE = "txb_api_base"
        private const val KEY_CONTINUOUS = "continuous_dialog"
        private const val KEY_FOLLOW_UP = "follow_up_timeout_sec"
        private const val KEY_BG_VOICE = "background_voice"
        private const val KEY_ONBOARDING = "onboarding_completed"
        private const val KEY_KWS_SCORE = "kws_keywords_score"
        private const val KEY_KWS_THRESHOLD = "kws_keywords_threshold"
        private const val KEY_KWS_MIGRATED_SENSITIVE = "kws_migrated_sensitive_v1"
        const val DEFAULT_TXB = "http://192.168.10.138:10074/api"
    }
}
