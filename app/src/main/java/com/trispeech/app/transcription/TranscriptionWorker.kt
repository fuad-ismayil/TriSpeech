package com.trispeech.app.transcription

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaMetadataRetriever
import androidx.core.app.NotificationCompat
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.trispeech.app.R
import com.trispeech.app.TriSpeechApplication
import com.trispeech.app.network.BackendException
import com.trispeech.app.data.AudioMetadataEntity
import com.trispeech.app.data.TranscriptEntity
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TranscriptionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val app = context.applicationContext as TriSpeechApplication
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    override suspend fun doWork(): Result {
        setForeground(foregroundInfo(0))
        val operationId = id.toString()
        app.database.transcriptDao().getByOperationId(operationId)?.let { existing ->
            return Result.success(workDataOf(OUTPUT_TRANSCRIPT_ID to existing.id))
        }

        val file = File(inputData.getString(INPUT_FILE_PATH) ?: return failure("Missing source audio."))
        if (!file.isFile) return failure("The source recording is unavailable.")
        val title = inputData.getString(INPUT_TITLE)?.ifBlank { "Recording" } ?: "Recording"
        val engineKind = enumValueOrNull<EngineKind>(inputData.getString(INPUT_ENGINE)) ?: return failure("Invalid transcription method.")
        val modelTier = enumValueOrNull<ModelTier>(inputData.getString(INPUT_MODEL_TIER)) ?: ModelTier.BEST_BALANCE
        val language = inputData.getString(INPUT_LANGUAGE) ?: "auto"
        val engine: TranscriptionEngine = when (engineKind) {
            EngineKind.ONLINE_WHISPER -> OnlineWhisperEngine(app.backend)
            EngineKind.OFFLINE_WHISPER -> OfflineWhisperEngine(File(applicationContext.filesDir, "models"))
            EngineKind.NATIVE_STT -> return failure("Native STT can only transcribe live audio.")
        }

        val request = TranscriptionRequest(
            audioFile = file,
            languageCode = language,
            modelTier = modelTier,
            onlineModel = OnlineModel.MINI,
            operationId = operationId
        )
        return coroutineScope {
            val latestProgress = AtomicInteger(0)
            val progressJob = launch {
                while (isActive) {
                    setProgress(workDataOf(PROGRESS to latestProgress.get()))
                    delay(250)
                }
            }
            val result = try {
                engine.transcribe(request) { progress ->
                    val safeProgress = progress.coerceIn(0, 100)
                    latestProgress.set(safeProgress)
                    notificationManager.notify(NOTIFICATION_ID, notification(safeProgress))
                }
            } finally {
                progressJob.cancel()
            }
            result.fold(
                onSuccess = { transcript -> save(operationId, title, engineKind, modelTier, file, transcript) },
                onFailure = { error ->
                    if (engineKind == EngineKind.ONLINE_WHISPER && error is IOException &&
                        (error !is BackendException || error.status >= 500) && runAttemptCount < 2) Result.retry()
                    else failure(error.message ?: "Transcription failed.")
                }
            )
        }
    }

    private suspend fun save(
        operationId: String,
        title: String,
        engineKind: EngineKind,
        modelTier: ModelTier,
        source: File,
        result: TranscriptionResult
    ): Result = withContext(Dispatchers.IO) {
        app.database.transcriptDao().getByOperationId(operationId)?.let { existing ->
            return@withContext Result.success(workDataOf(OUTPUT_TRANSCRIPT_ID to existing.id))
        }
        val extension = source.extension.ifBlank { "wav" }
        val staged = File(applicationContext.filesDir, "recordings/pending-$operationId.$extension")
        staged.parentFile?.mkdirs()
        if (source.absolutePath != staged.absolutePath) source.copyTo(staged, overwrite = true)

        val transcriptId = app.database.withTransaction {
            app.database.transcriptDao().getByOperationId(operationId)?.id ?: run {
                val newId = app.database.transcriptDao().insert(
                    TranscriptEntity(
                        title = title,
                        text = result.text,
                        languageCode = result.language,
                        engine = engineKind.name,
                        model = if (engineKind == EngineKind.ONLINE_WHISPER) OnlineModel.MINI.apiId else modelTier.name,
                        createdAtEpochMs = System.currentTimeMillis(),
                        durationMs = audioDurationMs(staged),
                        operationId = operationId
                    )
                )
                val destination = File(staged.parentFile, "$newId.$extension")
                staged.copyTo(destination, overwrite = true)
                app.database.audioMetadataDao().insert(
                    AudioMetadataEntity(
                        transcriptId = newId,
                        filePath = destination.absolutePath,
                        mimeType = extension.mediaType(),
                        sizeBytes = destination.length()
                    )
                )
                newId
            }
        }
        if (source.absolutePath != staged.absolutePath) source.delete()
        staged.delete()
        Result.success(workDataOf(OUTPUT_TRANSCRIPT_ID to transcriptId))
    }

    private fun failure(message: String) = Result.failure(workDataOf(OUTPUT_ERROR to message))

    private fun foregroundInfo(progress: Int): ForegroundInfo {
        notificationManager.createNotificationChannel(NotificationChannel(CHANNEL, applicationContext.getString(R.string.transcribing), NotificationManager.IMPORTANCE_LOW))
        return ForegroundInfo(NOTIFICATION_ID, notification(progress), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun notification(progress: Int) = NotificationCompat.Builder(applicationContext, CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle(applicationContext.getString(R.string.transcribing))
        .setProgress(100, progress, progress == 0)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun audioDurationMs(file: File): Long = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)

    companion object {
        const val INPUT_FILE_PATH = "file_path"
        const val INPUT_TITLE = "title"
        const val INPUT_ENGINE = "engine"
        const val INPUT_MODEL_TIER = "model_tier"
        const val INPUT_LANGUAGE = "language"
        const val OUTPUT_TRANSCRIPT_ID = "transcript_id"
        const val OUTPUT_ERROR = "error"
        const val PROGRESS = "progress"
        const val TAG = "trispeech-transcription"
        private const val CHANNEL = "transcription"
        private const val NOTIFICATION_ID = 1002

        fun input(file: File, title: String, stateEngine: EngineKind, tier: ModelTier, language: String): Data = workDataOf(
            INPUT_FILE_PATH to file.absolutePath,
            INPUT_TITLE to title,
            INPUT_ENGINE to stateEngine.name,
            INPUT_MODEL_TIER to tier.name,
            INPUT_LANGUAGE to language
        )
    }
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? =
    value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

private fun String.mediaType(): String = when (lowercase()) {
    "flac" -> "audio/flac"
    "mp3", "mpga" -> "audio/mpeg"
    "mp4", "m4a" -> "audio/mp4"
    "ogg" -> "audio/ogg"
    "webm" -> "audio/webm"
    else -> "audio/wav"
}
