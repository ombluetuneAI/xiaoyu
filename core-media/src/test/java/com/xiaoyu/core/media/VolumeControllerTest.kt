package com.xiaoyu.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeControllerTest {
    @Test
    fun volumeIndexToPercent_roundsCorrectly() {
        assertEquals(0, VolumeController.volumeIndexToPercent(0, 10))
        assertEquals(50, VolumeController.volumeIndexToPercent(5, 10))
        assertEquals(100, VolumeController.volumeIndexToPercent(10, 10))
    }

    @Test
    fun percentToVolumeIndex_roundsCorrectly() {
        assertEquals(0, VolumeController.percentToVolumeIndex(0, 10))
        assertEquals(5, VolumeController.percentToVolumeIndex(50, 10))
        assertEquals(10, VolumeController.percentToVolumeIndex(100, 10))
    }
}
