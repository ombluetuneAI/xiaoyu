package com.xiaoyu.core.wake

import android.content.Context

object WakeWords {
    const val PRIMARY = "小鱼同学"
    const val DEFAULT: String = PRIMARY

    @Volatile
    private var configured: List<String> = listOf(PRIMARY)

    val ALL: List<String>
        get() = configured

    fun configure(words: List<String>) {
        val cleaned = words.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleaned.isNotEmpty()) {
            configured = cleaned
        }
    }

    /** 从 Sherpa keywords.txt 解析 @ 后缀中文唤醒词 */
    fun parseFromKeywordsFile(content: String): List<String> {
        return content.lineSequence()
            .map { it.trim().trimStart('\uFEFF') }
            .filter { it.isNotEmpty() && it.contains("@") }
            .mapNotNull { line ->
                line.substringAfterLast("@").trim().takeIf { it.isNotEmpty() }
            }
            .distinct()
            .toList()
    }

    fun loadFromAsset(context: Context, assetPath: String): Boolean {
        return try {
            val text = context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val words = parseFromKeywordsFile(text)
            OfflineKwsCommands.configureFromKeywords(words)
            val wakeOnly = words.filter { !OfflineKwsCommands.isCommandPhrase(it) }
            if (wakeOnly.isNotEmpty()) configure(wakeOnly)
            wakeOnly.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun hasUtf8Bom(context: Context, assetPath: String): Boolean {
        return try {
            context.assets.open(assetPath).use { input ->
                val header = ByteArray(3)
                input.read(header) == 3 &&
                    header[0] == 0xEF.toByte() &&
                    header[1] == 0xBB.toByte() &&
                    header[2] == 0xBF.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Sherpa getResult().keyword 可能带 : / @ 前缀，或仅为中文唤醒词 */
    fun normalize(raw: String): String {
        val trimmed = raw.trim().removePrefix(":").removePrefix("@")
        ALL.firstOrNull { word -> trimmed == word || trimmed.endsWith(word) }?.let { return it }
        return DEFAULT
    }

    fun isEcho(text: String): Boolean {
        val normalized = text.trim().trimEnd('?', '？', '。', '.', ' ', '\uFFFD')
            .replace("，", "")
            .replace(",", "")
        for (phrase in LOCAL_WAKE_ACK_ECHO) {
            val p = phrase.replace("，", "")
            if (normalized == p || (normalized.contains(p) && normalized.length <= p.length + 4)) {
                return true
            }
        }
        for (word in ALL) {
            if (normalized == word.replace("，", "")) return true
            if (normalized.startsWith(word.replace("，", "")) &&
                normalized.length <= word.length + 2
            ) {
                return true
            }
            if (isHomophoneWakeEcho(normalized, word)) return true
        }
        return false
    }

    /** ASR 常把唤醒词听成同音字，如 小艺小艺 -> 小易小艺 */
    private fun isHomophoneWakeEcho(stt: String, wake: String): Boolean {
        if (wake.length != 4 || wake[0] != '小' || wake[2] != '小' || wake[1] != wake[3]) {
            return false
        }
        if (stt.length !in 4..5) return false
        val core = stt.trimEnd('。', '.')
        if (core.length != 4) return false
        if (core[0] != '小' || core[2] != '小') return false
        val yiLike = "艺易意忆译毅伊倚"
        return core[1] in yiLike && core[3] in yiLike && wake[1] == '艺'
    }

    private val LOCAL_WAKE_ACK_ECHO = listOf(
        "在呢",
        "我在",
        "你说",
        "我在，你说",
        "我在你说",
    )
}
