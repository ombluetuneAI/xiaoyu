package com.xiaoyu.core.wake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineKwsCommandsTest {
    @Test
    fun playbackAction_next() {
        assertEquals("next", OfflineKwsCommands.playbackAction("下一首"))
    }

    @Test
    fun phraseFromRawKeyword_atSuffix() {
        assertEquals("下一首", OfflineKwsCommands.phraseFromRawKeyword("x ià y ī sh ǒu @下一首"))
    }

    @Test
    fun kwsResolver_offlinePlayback() {
        val r = KwsPhraseResolver.resolve("@下一首")
        assertEquals(KwsDetectedPhrase.OfflinePlayback("下一首", "next"), r)
    }

    @Test
    fun kwsResolver_wakeWord() {
        OfflineKwsCommands.configureFromKeywords(listOf("下一首"))
        val r = KwsPhraseResolver.resolve("@小鱼同学")
        assertEquals(KwsDetectedPhrase.Wake("小鱼同学"), r)
    }

    @Test
    fun wakeWordsExcludeCommands() {
        OfflineKwsCommands.configureFromKeywords(listOf("下一首", "小鱼同学"))
        assertEquals(false, OfflineKwsCommands.isCommandPhrase("小鱼同学"))
        assertEquals(true, OfflineKwsCommands.isCommandPhrase("下一首"))
    }

    @Test
    fun unknownKeyword_returnsNull() {
        assertNull(KwsPhraseResolver.resolve("random noise"))
    }
}
