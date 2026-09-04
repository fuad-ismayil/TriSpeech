package com.trispeech.app.transcription

import com.trispeech.app.network.BackendClient

class OnlineWhisperEngine(private val backend: BackendClient) : TranscriptionEngine {
    override val kind = EngineKind.ONLINE_WHISPER

    override suspend fun transcribe(request: TranscriptionRequest, onProgress: (Int) -> Unit): Result<TranscriptionResult> =
        runCatching { backend.transcribe(request, onProgress) }
}
