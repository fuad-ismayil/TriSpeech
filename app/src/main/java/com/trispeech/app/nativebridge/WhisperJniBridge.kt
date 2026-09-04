package com.trispeech.app.nativebridge

object WhisperJniBridge {
    private val loadResult = runCatching {
        listOf("omp", "ggml", "ggml-base", "ggml-cpu", "whisper", "trispeech_whisper").forEach(System::loadLibrary)
    }
    private val loaded = loadResult.isSuccess
    fun isAvailable(): Boolean = loaded && nativeCoreAvailable()
    fun unavailableReason(): String = loadResult.exceptionOrNull()?.message ?: "Native Whisper loaded."
    fun transcribe(modelPath: String, wavPath: String, language: String): String {
        check(loaded) { "Native transcription library could not be loaded" }
        return nativeTranscribe(modelPath, wavPath, language)
    }
    private external fun nativeCoreAvailable(): Boolean
    private external fun nativeTranscribe(modelPath: String, wavPath: String, language: String): String
}
