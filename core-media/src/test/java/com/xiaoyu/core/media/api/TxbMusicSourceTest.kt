package com.xiaoyu.core.media.api

import org.junit.Assert.assertEquals
import org.junit.Test

class TxbMusicSourceTest {
    @Test
    fun normalize_defaultsToKw() {
        assertEquals("kw", TxbMusicSource.normalize(null))
        assertEquals("kw", TxbMusicSource.normalize(""))
        assertEquals("kw", TxbMusicSource.normalize("unknown"))
    }

    @Test
    fun normalize_acceptsAllowedSources() {
        assertEquals("kg", TxbMusicSource.normalize("kg"))
        assertEquals("wy", TxbMusicSource.normalize("WY"))
        assertEquals("qq", TxbMusicSource.normalize(" qq "))
    }
}
