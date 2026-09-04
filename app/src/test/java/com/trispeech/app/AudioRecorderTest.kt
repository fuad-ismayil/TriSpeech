package com.trispeech.app

import com.trispeech.app.audio.WaveFileWriter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRecorderTest {
    @Test fun writesCanonicalPcmWaveHeaderAndSamples() {
        val file = File.createTempFile("trispeech", ".wav")
        try {
            val rms = WaveFileWriter(file).use { it.write(shortArrayOf(0, 16_384, -16_384, 32_767)) }
            val bytes = file.readBytes()
            assertEquals("RIFF", bytes.copyOfRange(0, 4).decodeToString())
            assertEquals("WAVE", bytes.copyOfRange(8, 12).decodeToString())
            assertEquals("fmt ", bytes.copyOfRange(12, 16).decodeToString())
            assertEquals("data", bytes.copyOfRange(36, 40).decodeToString())
            assertEquals(16_000, ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int)
            assertEquals(8, ByteBuffer.wrap(bytes, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int)
            assertTrue(rms in .60f..61f)
        } finally { file.delete() }
    }

    @Test fun emptyBufferHasZeroRms() {
        val file = File.createTempFile("trispeech_empty", ".wav")
        try { WaveFileWriter(file).use { assertEquals(0f, it.write(shortArrayOf()), 0f) } } finally { file.delete() }
    }
}
