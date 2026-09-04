package com.trispeech.app

import com.trispeech.app.models.DeviceMemoryProfiler
import com.trispeech.app.transcription.ModelTier
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryProfilerTest {
    @Test fun devicesBelowFourGiBUseMaximumSpeed() = assertEquals(ModelTier.MAXIMUM_SPEED, DeviceMemoryProfiler.recommend(3L * DeviceMemoryProfiler.GIB))
    @Test fun devicesFromFourThroughSixGiBUseBalance() {
        assertEquals(ModelTier.BEST_BALANCE, DeviceMemoryProfiler.recommend(4L * DeviceMemoryProfiler.GIB))
        assertEquals(ModelTier.BEST_BALANCE, DeviceMemoryProfiler.recommend(6L * DeviceMemoryProfiler.GIB))
    }
    @Test fun devicesAboveSixGiBUseMaximumQuality() = assertEquals(ModelTier.MAXIMUM_QUALITY, DeviceMemoryProfiler.recommend(6L * DeviceMemoryProfiler.GIB + 1))
}
