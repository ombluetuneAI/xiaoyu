package com.xiaoyu.core.wake

/** Sherpa KeywordSpotter 全局灵敏度（设置页可调）；keywords.txt 不写行内阈值。 */
data class KwsTuning(
    val keywordsScore: Float,
    val keywordsThreshold: Float,
) {
    companion object {
        val DEFAULT = KwsTuning(keywordsScore = 2.0f, keywordsThreshold = 0.20f)
        /** 外放音乐、环境噪声较大时可试 */
        val NOISY_ENV = KwsTuning(keywordsScore = 2.4f, keywordsThreshold = 0.15f)

        const val MIN_SCORE = 1.0f
        const val MAX_SCORE = 4.0f
        const val MIN_THRESHOLD = 0.05f
        const val MAX_THRESHOLD = 0.40f
    }
}
