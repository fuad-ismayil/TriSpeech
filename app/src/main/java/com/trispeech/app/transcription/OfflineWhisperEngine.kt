package com.trispeech.app.transcription

import com.trispeech.app.audio.AudioFileConverter
import com.trispeech.app.nativebridge.WhisperJniBridge
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OfflineWhisperEngine(private val modelDirectory: File) : TranscriptionEngine {
    override val kind = EngineKind.OFFLINE_WHISPER
    override suspend fun transcribe(request: TranscriptionRequest, onProgress: (Int) -> Unit): Result<TranscriptionResult> = withContext(Dispatchers.Default) {
        runCatching {
            val model = File(modelDirectory, request.modelTier.fileName)
            require(model.isFile) { "Model ${request.modelTier.fileName} is not downloaded" }
            check(WhisperJniBridge.isAvailable()) { WhisperJniBridge.unavailableReason() }
            onProgress(5)
            val converted = File(request.audioFile.parentFile ?: modelDirectory, "whisper_${System.nanoTime()}.wav")
            val whisperInput = AudioFileConverter.prepareForWhisper(request.audioFile, converted)
            try {
                onProgress(15)
                val json = JSONObjectCompat(WhisperJniBridge.transcribe(model.absolutePath, whisperInput.absolutePath, request.languageCode))
                onProgress(100)
                TranscriptionResult(json.text, json.language, json.segments)
            } finally {
                if (whisperInput.absolutePath != request.audioFile.absolutePath) whisperInput.delete()
            }
        }
    }
}

private class JSONObjectCompat(raw: String) {
    private val json = org.json.JSONObject(raw)
    val text: String = json.getString("text")
    val language: String = json.optString("language", "auto")
    val segments: List<TranscriptSegment> = buildList {
        val array = json.optJSONArray("segments") ?: return@buildList
        for (i in 0 until array.length()) array.getJSONObject(i).let {
            add(TranscriptSegment(it.optLong("start_ms"), it.optLong("end_ms"), it.optString("text")))
        }
    }
}
