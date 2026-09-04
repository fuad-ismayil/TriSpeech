package com.trispeech.app.transcription

import java.io.File

enum class EngineKind { ONLINE_WHISPER, OFFLINE_WHISPER, NATIVE_STT }
enum class OnlineModel(val apiId: String) { MINI("gpt-4o-mini-transcribe") }
enum class ModelTier(val fileName: String, val downloadBytes: Long) {
    MAXIMUM_SPEED("ggml-tiny.bin", 75L * 1024 * 1024),
    BEST_BALANCE("ggml-base.bin", 142L * 1024 * 1024),
    MAXIMUM_QUALITY("ggml-small.bin", 466L * 1024 * 1024)
}

data class TranscriptSegment(val startMs: Long, val endMs: Long, val text: String)
data class TranscriptionResult(val text: String, val language: String, val segments: List<TranscriptSegment>)
data class TranscriptionRequest(
    val audioFile: File,
    val languageCode: String = "auto",
    val modelTier: ModelTier = ModelTier.BEST_BALANCE,
    val onlineModel: OnlineModel = OnlineModel.MINI,
    val operationId: String? = null
)

interface TranscriptionEngine {
    val kind: EngineKind
    suspend fun transcribe(request: TranscriptionRequest, onProgress: (Int) -> Unit = {}): Result<TranscriptionResult>
}

class TranscriptionRepository(private val engines: Map<EngineKind, TranscriptionEngine>) {
    suspend fun transcribe(kind: EngineKind, request: TranscriptionRequest, progress: (Int) -> Unit = {}): Result<TranscriptionResult> =
        engines[kind]?.transcribe(request, progress) ?: Result.failure(IllegalStateException("Engine $kind is unavailable"))
}
