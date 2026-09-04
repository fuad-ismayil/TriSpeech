package com.trispeech.app

import com.trispeech.app.data.TranscriptEntity
import com.trispeech.app.transcription.WhisperLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomAndNativeEngineTest {
    @Test fun transcriptEntityPreservesEngineMetadata() {
        val value = TranscriptEntity(title = "Interview", text = "Hello", languageCode = "en", engine = "NATIVE_STT", model = "platform", createdAtEpochMs = 42, durationMs = 5_000)
        assertEquals("NATIVE_STT", value.engine)
        assertEquals(5_000, value.durationMs)
    }

    @Test fun whisperCatalogueStartsWithAutoAndContainsCoreLocales() {
        assertEquals("auto", WhisperLanguages.all.first().code)
        assertTrue(WhisperLanguages.all.size >= 99)
        assertTrue(setOf("en", "az", "ru", "tr").all { code -> WhisperLanguages.all.any { it.code == code } })
        assertEquals(WhisperLanguages.all.size, WhisperLanguages.all.map { it.code }.toSet().size)
    }
}
