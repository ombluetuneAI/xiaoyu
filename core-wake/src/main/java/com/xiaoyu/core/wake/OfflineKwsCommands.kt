package com.xiaoyu.core.wake

/**
 * Sherpa KWS 离线播控短语（小智 WS 不可用时由端侧直接执行）。
 * 与 [WakeWords] 共用 keywords.txt，通过 @ 后缀中文区分。
 */
object OfflineKwsCommands {
    private val phraseToAction: Map<String, String> = mapOf(
        "下一首" to "next",
        "上一首" to "previous",
        "暂停" to "pause",
        "继续" to "resume",
        "继续播放" to "resume",
    )

    @Volatile
    private var configuredPhrases: Set<String> = phraseToAction.keys

    val phrases: Set<String>
        get() = configuredPhrases

    fun configureFromKeywords(phrases: List<String>) {
        val known = phrases.filter { phraseToAction.containsKey(it) }.toSet()
        if (known.isNotEmpty()) {
            configuredPhrases = known
        }
    }

    fun isCommandPhrase(phrase: String): Boolean = phrase in configuredPhrases

    fun playbackAction(phrase: String): String? = phraseToAction[phrase]

    /** 解析 Sherpa 返回的 keyword 字段为中文短语 */
    fun phraseFromRawKeyword(raw: String): String? {
        val trimmed = raw.trim().trimStart('\uFEFF')
        if (trimmed.isEmpty()) return null
        if (trimmed.contains("@")) {
            return trimmed.substringAfterLast("@").trim().takeIf { it.isNotEmpty() }
        }
        configuredPhrases.firstOrNull { trimmed == it || trimmed.endsWith(it) }?.let { return it }
        return null
    }
}
