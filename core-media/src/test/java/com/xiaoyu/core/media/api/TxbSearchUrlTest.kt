package com.xiaoyu.core.media.api

import org.junit.Assert.assertTrue
import org.junit.Test

class TxbSearchUrlTest {
    @Test
    fun buildSearchUrl_includesKeywordLimitAndSrc() {
        val client = MusicApiClient(baseUrlProvider = { "http://192.168.10.138:10074/api" })
        val url = client.buildSearchUrl("晴天", 10, "kg")
        assertTrue(url.contains("/music/v1/search?"))
        assertTrue(url.contains("keyword="))
        assertTrue(url.contains("limit=10"))
        assertTrue(url.contains("src=kg"))
    }
}
