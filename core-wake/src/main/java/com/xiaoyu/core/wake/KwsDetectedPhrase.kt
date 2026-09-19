package com.xiaoyu.core.wake

/** Sherpa 一次命中后的语义：唤醒词或离线播控短语 */
sealed class KwsDetectedPhrase {
    data class Wake(val word: String) : KwsDetectedPhrase()
    data class OfflinePlayback(val phrase: String, val action: String) : KwsDetectedPhrase()
}

object KwsPhraseResolver {
    fun resolve(rawKeyword: String): KwsDetectedPhrase? {
        val offline = OfflineKwsCommands.phraseFromRawKeyword(rawKeyword)
        if (offline != null) {
            val action = OfflineKwsCommands.playbackAction(offline)
            if (action != null) {
                return KwsDetectedPhrase.OfflinePlayback(offline, action)
            }
        }
        val wake = matchWakeWord(rawKeyword) ?: return null
        return KwsDetectedPhrase.Wake(wake)
    }

    private fun matchWakeWord(raw: String): String? {
        val trimmed = raw.trim().trimStart('\uFEFF').removePrefix(":").removePrefix("@")
        if (trimmed.contains("@")) {
            val phrase = trimmed.substringAfterLast("@").trim()
            if (WakeWords.ALL.any { it == phrase }) return phrase
        }
        WakeWords.ALL.firstOrNull { word -> trimmed == word || trimmed.endsWith(word) }?.let { return it }
        return null
    }
}
