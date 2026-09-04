package com.trispeech.app

import com.trispeech.app.transcription.OnlineModel
import org.junit.Assert.assertEquals
import org.junit.Test

class V1PolicyTest {
    @Test fun productionModelCatalogContainsOnlyMini() {
        assertEquals(listOf("gpt-4o-mini-transcribe"), OnlineModel.entries.map { it.apiId })
    }
}
