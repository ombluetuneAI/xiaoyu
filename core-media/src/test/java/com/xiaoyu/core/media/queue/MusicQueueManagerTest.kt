package com.xiaoyu.core.media.queue

import com.xiaoyu.core.media.PlaybackMode
import com.xiaoyu.core.media.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicQueueManagerTest {
    @Test
    fun peekAtOffset_doesNotAdvanceIndex() {
        val queue = MusicQueueManager()
        val tracks = listOf(
            Track(id = "1", title = "A", artist = "", url = "u1"),
            Track(id = "2", title = "B", artist = "", url = "u2"),
            Track(id = "3", title = "C", artist = "", url = "u3"),
        )
        queue.setQueue(tracks, PlaybackMode.QUEUE_LOOP, "test")

        assertEquals("B", queue.peekAtOffset(1)?.title)
        assertEquals(0, queue.currentIndex.value)
        assertEquals("A", queue.currentTrack()?.title)

        queue.next()
        assertEquals(1, queue.currentIndex.value)
        assertEquals("C", queue.peekAtOffset(1)?.title)
    }

    @Test
    fun peekAtOffset_returnsNullForSingleMode() {
        val queue = MusicQueueManager()
        queue.setQueue(
            listOf(Track(id = "1", title = "A", artist = "", url = "u1")),
            PlaybackMode.SINGLE,
        )
        assertNull(queue.peekAtOffset(1))
    }
}
