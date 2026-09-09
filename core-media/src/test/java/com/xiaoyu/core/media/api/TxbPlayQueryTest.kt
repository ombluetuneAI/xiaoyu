package com.xiaoyu.core.media.api

import com.xiaoyu.core.media.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TxbPlayQueryTest {
    @Test
    fun buildFullTrackQuery() {
        val url = TxbPlayQuery.build(
            baseUrl = "http://192.168.10.138:10074/api",
            title = "恋人",
            artist = "李荣浩",
            id = "123",
            mid = "456",
            src = "txb",
        )
        assertTrue(url.contains("/music/v1/play?"))
        assertTrue(url.contains("keyword="))
        assertTrue(url.contains("%E6%81%8B%E4%BA%BA-%E6%9D%8E%E8%8D%A3%E6%B5%A9")) // 恋人-李荣浩
        assertTrue(url.contains("id=123"))
        assertTrue(url.contains("mid=456"))
        assertTrue(url.contains("src=txb"))
        assertTrue(url.endsWith("source=auto"))
    }

    @Test
    fun buildFromTrackSkipsUrlLikeId() {
        val url = TxbPlayQuery.build(
            baseUrl = "http://host/api",
            track = Track(
                id = "http://stream/url.mp3",
                title = "歌",
                artist = "歌手",
                url = "",
                src = "netease",
                mid = "mid1",
            ),
        )
        assertFalse(url.contains("id=http"))
        assertTrue(url.contains("mid=mid1"))
        assertTrue(url.contains("src=netease"))
        assertTrue(url.contains("source=auto"))
    }

    @Test
    fun buildKeywordOnlyQuery() {
        val url = TxbPlayQuery.build(
            baseUrl = "http://host/api",
            keywordOverride = "晴天",
        )
        assertTrue(url.contains("keyword="))
        assertTrue(url.contains("source=auto"))
        assertFalse(url.contains("id="))
    }

    @Test
    fun buildKeywordNameArtists() {
        assertEquals("恋人-李荣浩", TxbPlayQuery.buildKeyword("恋人", "李荣浩"))
        assertEquals("晴天", TxbPlayQuery.buildKeyword("晴天", ""))
    }
}
