package com.xiaoyu.core.media

import com.xiaoyu.core.media.api.TxbResponseParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TxbJsonParseTest {
    @Test
    fun parseDataListFormat() {
        val json = JSONObject(
            """
            {"data":{"list":[
              {"id":"1","title":"晴天","artist":"周杰伦","url":"http://example/a.mp3"}
            ]}}
            """.trimIndent(),
        )
        val list = TxbResponseParser.extractTrackArray(json)
        assertEquals(1, list.length())
        assertEquals("晴天", list.getJSONObject(0).getString("title"))
    }
}
