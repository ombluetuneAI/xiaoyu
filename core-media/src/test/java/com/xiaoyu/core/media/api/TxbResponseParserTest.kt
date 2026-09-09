package com.xiaoyu.core.media.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TxbResponseParserTest {
    @Test
    fun parsePlaylistsDataList() {
        val json = JSONObject(
            """
            {"code":200,"data":{"list":[
              {"id":"1","name":"恋人","artists":"李荣浩","cover":"http://c/1.jpg","url":"http://u/1.mp3","src":"txb"}
            ]}}
            """.trimIndent(),
        )
        val tracks = TxbResponseParser.parseTracks(TxbResponseParser.extractTrackArray(json), allowMissingUrl = true)
        assertEquals(1, tracks.size)
        val t = tracks.first()
        assertEquals("恋人", t.title)
        assertEquals("李荣浩", t.artist)
        assertEquals("http://u/1.mp3", t.url)
        assertEquals("http://c/1.jpg", t.coverUrl)
    }

    @Test
    fun parseTrackWithArtistsField() {
        val track = TxbResponseParser.parseTrack(
            JSONObject("""{"id":"x","name":"测试","artists":"歌手A","url":"http://play"}"""),
        )
        assertNotNull(track)
        assertEquals("歌手A", track!!.artist)
    }

    @Test
    fun extractFromNestedDataTrack() {
        val json = JSONObject("""{"data":{"track":{"id":"1","name":"歌","url":"http://u"}}}""")
        val arr = TxbResponseParser.extractTrackArray(json)
        assertTrue(arr.length() >= 1)
    }
}
