package com.trispeech.app.transcription

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class NativeSpeechRecognizerEngine(private val context: Context) : TranscriptionEngine {
    override val kind = EngineKind.NATIVE_STT
    private var activeRecognizer: SpeechRecognizer? = null

    override suspend fun transcribe(request: TranscriptionRequest, onProgress: (Int) -> Unit): Result<TranscriptionResult> =
        listen(request.languageCode, onProgress = onProgress)

    suspend fun listen(
        languageCode: String,
        onPartial: (String) -> Unit = {},
        onAmplitude: (Float) -> Unit = {},
        onProgress: (Int) -> Unit = {}
    ): Result<TranscriptionResult> = suspendCancellableCoroutine { continuation ->
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            continuation.resume(Result.failure(IllegalStateException("Offline speech recognition is not available on this device.")))
            return@suspendCancellableCoroutine
        }

        fun begin(language: String?, mayRetryWithoutLanguage: Boolean) {
            val recognizer = if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else SpeechRecognizer.createSpeechRecognizer(context)
            activeRecognizer = recognizer
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                language?.let {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, it)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, it)
                }
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onProgress(10)
                override fun onBeginningOfSpeech() = onProgress(25)
                override fun onRmsChanged(rmsdB: Float) = onAmplitude(((rmsdB + 2f) / 12f).coerceIn(.03f, 1f))
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = onProgress(80)
                override fun onError(error: Int) {
                    recognizer.destroy()
                    if (error in setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) && mayRetryWithoutLanguage && continuation.isActive) {
                        begin(null, false)
                    } else if (continuation.isActive) {
                        continuation.resume(Result.failure(IllegalStateException(errorMessage(error, language))))
                    }
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                    recognizer.destroy(); activeRecognizer = null
                    if (continuation.isActive) {
                        if (text.isBlank()) continuation.resume(Result.failure(IllegalStateException("No speech was detected.")))
                        else continuation.resume(Result.success(TranscriptionResult(text, language ?: "auto", listOf(TranscriptSegment(0, 0, text)))))
                    }
                    onProgress(100)
                }
                override fun onPartialResults(results: Bundle?) {
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            recognizer.startListening(intent)
        }

        val requested = if (languageCode == "auto") Locale.getDefault().toLanguageTag() else languageCode
        continuation.invokeOnCancellation { activeRecognizer?.cancel(); activeRecognizer?.destroy(); activeRecognizer = null }
        begin(requested, true)
    }

    fun stopListening() = activeRecognizer?.stopListening()
    fun cancel() { activeRecognizer?.cancel(); activeRecognizer?.destroy(); activeRecognizer = null }

    private fun errorMessage(error: Int, language: String?): String = when (error) {
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "The offline speech pack for ${language ?: "the selected language"} is not installed. Install it in Android Settings → Languages → Speech recognition."
        SpeechRecognizer.ERROR_NO_MATCH -> "No clear speech was detected. Try speaking closer to the microphone."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was detected before the offline recognizer timed out."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for offline speech recognition."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The speech recognizer is busy. Wait a moment and try again."
        else -> "Offline speech recognition failed (code $error)."
    }
}
