package com.trispeech.app.ui

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.trispeech.app.TriSpeechApplication
import com.trispeech.app.R
import com.trispeech.app.ads.OnlineRewards
import com.trispeech.app.audio.AudioRecorderEngine
import com.trispeech.app.audio.RecordingService
import com.trispeech.app.data.AudioMetadataEntity
import com.trispeech.app.data.TranscriptEntity
import com.trispeech.app.models.ModelDownloadManager
import com.trispeech.app.transcription.EngineKind
import com.trispeech.app.transcription.ModelTier
import com.trispeech.app.transcription.NativeSpeechRecognizerEngine
import com.trispeech.app.transcription.OnlineModel
import com.trispeech.app.transcription.TranscriptionResult
import com.trispeech.app.transcription.TranscriptionWorker
import java.io.File
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex

data class StudioState(
    val recording: Boolean = false,
    val processing: Boolean = false,
    val paused: Boolean = false,
    val elapsedSeconds: Long = 0,
    val language: String = "auto",
    val engine: EngineKind = EngineKind.NATIVE_STT,
    val onlineModel: OnlineModel = OnlineModel.MINI,
    val modelTier: ModelTier = ModelTier.BEST_BALANCE,
    val processingProgress: Int = 0,
    val partialText: String = "",
    val sourceName: String = "Recording.wav",
    val error: String? = null,
    val latestTranscriptId: Long? = null,
    val showOnlineDisclosure: Boolean = false,
    val showSupportPrompt: Boolean = false,
    val showBalanceDialog: Boolean = false,
    val automaticRewardRequested: Boolean = false,
    val pendingOnlineSeconds: Int = 0,
    val hasPendingOnline: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TriSpeechApplication
    private val modelDirectory = File(application.filesDir, "models")
    private val recorder = AudioRecorderEngine(application)
    private val nativeEngine = NativeSpeechRecognizerEngine(application)
    private val backend = app.backend
    private val workManager = WorkManager.getInstance(application)
    private val promptPrefs = application.getSharedPreferences("support_prompt", Application.MODE_PRIVATE)
    val onlineRewards = OnlineRewards(application, backend, viewModelScope)
    private data class PendingOnline(val file: File, val title: String, val snapshot: StudioState)
    private var pendingOnline: PendingOnline? = null
    private val enqueueMutex = Mutex()
    private var recordingJob: Job? = null
    private var nativeJob: Deferred<Result<TranscriptionResult>>? = null
    private var timerJob: Job? = null
    private var currentFile: File? = null
    private var activeTranscriptionWorkId: java.util.UUID? = null
    private val _state = MutableStateFlow(StudioState())
    val state: StateFlow<StudioState> = _state.asStateFlow()
    private val _amplitudes = MutableStateFlow(List(56) { 0f })
    val amplitudes: StateFlow<List<Float>> = _amplitudes.asStateFlow()
    private val _installedModels = MutableStateFlow(installedModelSet())
    val installedModels: StateFlow<Set<ModelTier>> = _installedModels.asStateFlow()
    val transcripts = app.database.transcriptDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val pendingPath = promptPrefs.getString("pending_online_path", null)
        if (pendingPath != null && File(pendingPath).isFile) {
            pendingOnline = PendingOnline(File(pendingPath), promptPrefs.getString("pending_online_title", "Recording")!!,
                StudioState(engine = EngineKind.ONLINE_WHISPER, language = promptPrefs.getString("pending_online_language", "auto")!!))
            _state.update { it.copy(engine = EngineKind.ONLINE_WHISPER, pendingOnlineSeconds = promptPrefs.getInt("pending_online_seconds", 0), hasPendingOnline = true) }
            onlineRewards.refresh()
        }
        viewModelScope.launch { recorder.amplitudes.collectLatest { _amplitudes.value = it } }
        viewModelScope.launch {
            workManager.getWorkInfosByTagLiveData(TranscriptionWorker.TAG).asFlow().collectLatest { work ->
                _state.update { it.copy(processing = work.any { info -> !info.state.isFinished }) }
            }
        }
    }

    fun setLanguage(code: String) { _state.value = _state.value.copy(language = code) }
    fun setMethod(engine: EngineKind, tier: ModelTier? = null, onlineModel: OnlineModel? = null) {
        _state.value = _state.value.copy(
            engine = engine,
            modelTier = tier ?: _state.value.modelTier,
            onlineModel = onlineModel ?: _state.value.onlineModel,
            showOnlineDisclosure = engine == EngineKind.ONLINE_WHISPER && !promptPrefs.getBoolean(ONLINE_DISCLOSURE, false)
        )
    }
    fun refreshInstalledModels() { _installedModels.value = installedModelSet() }
    fun clearError() { _state.value = _state.value.copy(error = null) }

    fun openBalance() {
        if (_state.value.recording || _state.value.processing) return
        _state.update { it.copy(showBalanceDialog = true) }
        onlineRewards.refresh()
    }

    fun closeBalance() { if (!onlineRewards.state.value.busy) _state.update { it.copy(showBalanceDialog = false) } }

    fun beginAutomaticReward() { _state.update { it.copy(automaticRewardRequested = false) } }

    fun finishAutomaticReward(rewarded: Boolean, onComplete: (Long) -> Unit) {
        if (rewarded) resumePendingOnline(onComplete)
        else _state.update { it.copy(processing = false, error = onlineRewards.state.value.message) }
    }

    fun discardPendingOnline() {
        if (_state.value.processing || onlineRewards.state.value.busy || enqueueMutex.isLocked) return
        pendingOnline?.file?.delete()
        pendingOnline = null
        promptPrefs.edit().remove("pending_online_path").remove("pending_online_title").remove("pending_online_language").remove("pending_online_seconds").remove("pending_online_operation").apply()
        _state.update { it.copy(pendingOnlineSeconds = 0, hasPendingOnline = false, showBalanceDialog = false, automaticRewardRequested = false) }
    }

    fun resumePendingOnline(onComplete: (Long) -> Unit) {
        val pending = pendingOnline ?: return
        if (onlineRewards.state.value.busy) return
        viewModelScope.launch { enqueueTranscription(pending.file, pending.title, pending.snapshot, onComplete) }
    }

    fun acceptOnlineDisclosure() {
        promptPrefs.edit().putBoolean(ONLINE_DISCLOSURE, true).apply()
        _state.update { it.copy(showOnlineDisclosure = false) }
    }

    fun dismissOnlineDisclosure() {
        _state.update { current ->
            current.copy(
                showOnlineDisclosure = false,
                engine = if (current.engine == EngineKind.ONLINE_WHISPER) EngineKind.NATIVE_STT else current.engine
            )
        }
    }
    fun dismissSupportPrompt() {
        promptPrefs.edit().putLong(LAST_SUPPORT_PROMPT, System.currentTimeMillis()).apply()
        _state.update { it.copy(showSupportPrompt = false) }
    }
    fun updateTitle(transcript: TranscriptEntity, title: String) {
        val clean = title.trim()
        if (clean.isNotEmpty()) viewModelScope.launch { app.database.transcriptDao().update(transcript.copy(title = clean)) }
    }

    fun deleteTranscript(transcript: TranscriptEntity) {
        viewModelScope.launch {
            val metadata = app.database.audioMetadataDao().get(transcript.id)
            withContext(Dispatchers.IO) { metadata?.filePath?.let(::File)?.delete() }
            app.database.transcriptDao().delete(transcript)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            val metadata = app.database.audioMetadataDao().getAll()
            withContext(Dispatchers.IO) {
                metadata.forEach { item -> File(item.filePath).delete() }
                File(getApplication<Application>().filesDir, "recordings")
                    .listFiles()
                    ?.forEach(File::delete)
            }
            app.database.transcriptDao().deleteAll()
        }
    }

    fun clearTemporaryFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            getApplication<Application>().cacheDir.listFiles()?.forEach { entry ->
                if (entry.isDirectory) entry.walkBottomUp().forEach(File::delete) else entry.delete()
            }
            modelDirectory.listFiles { file -> file.name.endsWith(".part") }
                ?.forEach(File::delete)
            File(getApplication<Application>().filesDir, "pending").listFiles()?.forEach(File::delete)
        }
    }

    fun deleteModel(tier: ModelTier) {
        viewModelScope.launch {
            ModelDownloadManager(modelDirectory).remove(tier)
                .onSuccess { refreshInstalledModels() }
                .onFailure { failure -> _state.update { it.copy(error = failure.message) } }
        }
    }

    fun deleteAllModels() {
        viewModelScope.launch {
            ModelDownloadManager(modelDirectory).removeAll()
                .onSuccess { refreshInstalledModels() }
                .onFailure { failure -> _state.update { it.copy(error = failure.message) } }
        }
    }

    suspend fun audioPath(transcriptId: Long): String? = app.database.audioMetadataDao().get(transcriptId)?.filePath

    fun discardCurrentRecording() {
        timerJob?.cancel()
        timerJob = null
        _state.update {
            it.copy(
                recording = false,
                paused = false,
                elapsedSeconds = 0,
                processingProgress = 0,
                partialText = "",
                sourceName = "Recording.wav",
                error = null
            )
        }
        nativeEngine.cancel()
        recorder.stop()
        getApplication<Application>().stopService(Intent(getApplication(), RecordingService::class.java))
        val pendingFile = currentFile
        val pendingJob = recordingJob
        viewModelScope.launch {
            pendingJob?.join()
            withContext(Dispatchers.IO) { pendingFile?.delete() }
        }
        currentFile = null
        nativeJob = null
        recordingJob = null
        resetStudio()
    }

    fun resetStudio() {
        if (_state.value.recording) return
        recorder.resetWaveform()
        _amplitudes.value = List(56) { 0f }
        _state.update {
            it.copy(
                recording = false,
                paused = false,
                elapsedSeconds = 0,
                processingProgress = 0,
                partialText = "",
                sourceName = "Recording.wav"
            )
        }
    }

    fun startRecording(onPendingComplete: (Long) -> Unit = {}) {
        if (_state.value.recording || _state.value.processing || onlineRewards.state.value.busy) return
        if (pendingOnline != null && _state.value.engine == EngineKind.ONLINE_WHISPER) {
            _state.update { it.copy(processing = true) }
            resumePendingOnline(onPendingComplete)
            return
        }
        if (_state.value.showOnlineDisclosure) return
        recorder.resetWaveform()
        _amplitudes.value = List(56) { 0f }
        _state.value = _state.value.copy(recording = true, paused = false, elapsedSeconds = 0, partialText = "", sourceName = "Recording.wav", error = null)
        startTimer()
        if (_state.value.engine == EngineKind.NATIVE_STT) {
            nativeJob = viewModelScope.async {
                nativeEngine.listen(
                    languageCode = _state.value.language,
                    onPartial = { _state.value = _state.value.copy(partialText = it) },
                    onAmplitude = { amplitude -> _amplitudes.value = _amplitudes.value.drop(1) + amplitude },
                    onProgress = { _state.value = _state.value.copy(processingProgress = it) }
                )
            }
            return
        }
        val file = File(getApplication<Application>().cacheDir, "recording_${System.currentTimeMillis()}.wav")
        currentFile = file
        ContextCompat.startForegroundService(getApplication(), Intent(getApplication(), RecordingService::class.java))
        recordingJob = viewModelScope.launch {
            recorder.recordTo(file).onFailure { _state.value = _state.value.copy(recording = false, error = it.message) }
        }
    }

    fun togglePause() {
        if (_state.value.engine == EngineKind.NATIVE_STT) return
        val paused = !_state.value.paused
        if (paused) recorder.pause() else recorder.resume()
        _state.value = _state.value.copy(paused = paused)
    }

    fun stopAndTranscribe(onProcessing: () -> Unit, onComplete: (Long) -> Unit) {
        val snapshot = _state.value
        timerJob?.cancel()
        _state.value = snapshot.copy(recording = false, processing = true, paused = false, processingProgress = 0)
        onProcessing()
        if (snapshot.engine == EngineKind.NATIVE_STT) {
            nativeEngine.stopListening()
            viewModelScope.launch {
                val result = nativeJob?.await() ?: Result.failure(IllegalStateException("Native recognition did not start."))
                finishResult(result, "Native recording", snapshot, onComplete, null)
            }
            return
        }
        val file = currentFile ?: run { _state.value = _state.value.copy(error = "The recording file is missing."); return }
        recorder.stop()
        getApplication<Application>().stopService(Intent(getApplication(), RecordingService::class.java))
        viewModelScope.launch {
            recordingJob?.join()
            enqueueTranscription(file, file.nameWithoutExtension.replace('_', ' '), snapshot, onComplete)
        }
    }

    fun importAudio(uri: Uri, onProcessing: () -> Unit, onComplete: (Long) -> Unit) {
        if (_state.value.processing || _state.value.recording || onlineRewards.state.value.busy) return
        if (pendingOnline != null && _state.value.engine == EngineKind.ONLINE_WHISPER) {
            _state.update { it.copy(processing = true) }
            onProcessing()
            resumePendingOnline(onComplete)
            return
        }
        if (_state.value.showOnlineDisclosure) return
        if (_state.value.engine == EngineKind.NATIVE_STT) {
            _state.value = _state.value.copy(error = "Android Native STT listens live. Choose Online Whisper or a downloaded offline model for audio files.")
            return
        }
        val importSnapshot = _state.value
        _state.update { it.copy(processing = true) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "imported_audio"
                val extension = name.substringAfterLast('.', "audio").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "audio"
                val file = File(getApplication<Application>().cacheDir, "import_${System.currentTimeMillis()}.$extension")
                resolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to open selected file" }
                    file.outputStream().use(input::copyTo)
                }
                file to name.substringBeforeLast('.')
                }
            }.onSuccess { (file, title) ->
                onProcessing(); val snapshot = importSnapshot.copy(processing = true, processingProgress = 0, sourceName = title); _state.value = snapshot
                enqueueTranscription(file, title, snapshot, onComplete)
            }.onFailure { _state.value = _state.value.copy(error = it.message, processing = false) }
        }
    }

    private suspend fun enqueueTranscription(file: File, title: String, snapshot: StudioState, onComplete: (Long) -> Unit) {
        if (!enqueueMutex.tryLock()) return
        try {
        _state.update { it.copy(processing = true) }
        if (snapshot.engine == EngineKind.ONLINE_WHISPER) {
            // Preserve the input before network access or requesting credit.
            val saved = File(getApplication<Application>().filesDir, "online-awaiting/audio.${file.extension.ifBlank { "wav" }}")
            withContext(Dispatchers.IO) {
                saved.parentFile?.mkdirs()
                if (file.absolutePath != saved.absolutePath) { file.copyTo(saved, overwrite = true); file.delete() }
            }
            val seconds = withContext(Dispatchers.IO) { ((audioDurationMs(saved) + 999) / 1000).toInt() }
            if (pendingOnline == null) promptPrefs.edit().putString("pending_online_operation", java.util.UUID.randomUUID().toString()).commit()
            pendingOnline = PendingOnline(saved, title, snapshot)
            promptPrefs.edit().putString("pending_online_path", saved.absolutePath).putString("pending_online_title", title)
                .putString("pending_online_language", snapshot.language).putInt("pending_online_seconds", seconds).commit()
            _state.update { it.copy(pendingOnlineSeconds = seconds, hasPendingOnline = true) }
            if (seconds !in 1..600 || saved.length() > 25L * 1024 * 1024) {
                saved.delete()
                pendingOnline = null
                promptPrefs.edit().remove("pending_online_path").remove("pending_online_title").remove("pending_online_language")
                    .remove("pending_online_seconds").remove("pending_online_operation").apply()
                _state.update { it.copy(error = getApplication<Application>().getString(R.string.online_audio_limit), processing = false,
                    pendingOnlineSeconds = 0, hasPendingOnline = false, automaticRewardRequested = false) }
                return
            }
            val access = try { backend.onlineAccess() } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.update { it.copy(error = error.message, processing = false) }; return
            }
            if (!access.enabled || (access.mode != "development-preview" && access.availableSeconds < seconds)) {
                _state.update { it.copy(automaticRewardRequested = true, processing = true) }
                onlineRewards.refresh()
                return
            }
            _state.update { it.copy(automaticRewardRequested = false, processing = true) }
        }
        val operationId = if (snapshot.engine == EngineKind.ONLINE_WHISPER) {
            promptPrefs.getString("pending_online_operation", null)?.let(java.util.UUID::fromString) ?: java.util.UUID.randomUUID()
        } else java.util.UUID.randomUUID()
        val extension = file.extension.ifBlank { "audio" }
        val pending = File(getApplication<Application>().filesDir, "pending/$operationId.$extension")
        withContext(Dispatchers.IO) {
            pending.parentFile?.mkdirs()
            val source = if (snapshot.engine == EngineKind.ONLINE_WHISPER) pendingOnline!!.file else file
            source.copyTo(pending, overwrite = true)
            if (snapshot.engine != EngineKind.ONLINE_WHISPER && source.absolutePath != pending.absolutePath) source.delete()
        }
        currentFile = null
        recordingJob = null
        val builder = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setId(operationId)
            .setInputData(TranscriptionWorker.input(pending, title, snapshot.engine, snapshot.modelTier, snapshot.language))
            .addTag(TranscriptionWorker.TAG)
        if (snapshot.engine == EngineKind.ONLINE_WHISPER) {
            builder.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        }
        val request = builder.build()
        activeTranscriptionWorkId = request.id
        val enqueued = workManager.enqueueUniqueWork("transcription-$operationId", ExistingWorkPolicy.KEEP, request)
        withContext(Dispatchers.IO) { enqueued.result.get() }
        if (snapshot.engine == EngineKind.ONLINE_WHISPER) {
            val savedSource = pendingOnline?.file
            pendingOnline = null
            promptPrefs.edit().remove("pending_online_path").remove("pending_online_title").remove("pending_online_language").remove("pending_online_seconds").remove("pending_online_operation").commit()
            savedSource?.delete()
            _state.update { it.copy(pendingOnlineSeconds = 0, hasPendingOnline = false, automaticRewardRequested = false) }
        }

        var terminal: WorkInfo? = null
        workManager.getWorkInfoByIdFlow(request.id).filterNotNull().takeWhile { info ->
            if (info.state.isFinished) {
                terminal = info
                false
            } else {
                val reported = info.progress.getInt(TranscriptionWorker.PROGRESS, 0)
                _state.update { current -> current.copy(processingProgress = maxOf(current.processingProgress, reported)) }
                true
            }
        }.collect { }
        activeTranscriptionWorkId = null
        when (terminal?.state) {
            WorkInfo.State.SUCCEEDED -> {
                val transcriptId = terminal!!.outputData.getLong(TranscriptionWorker.OUTPUT_TRANSCRIPT_ID, -1L)
                if (transcriptId > 0) {
                    recorder.resetWaveform()
                    _amplitudes.value = List(56) { 0f }
                    _state.update { it.copy(latestTranscriptId = transcriptId, processing = false, processingProgress = 0, elapsedSeconds = 0, partialText = "", sourceName = "Recording.wav") }
                    recordSuccessfulTranscription()
                    onComplete(transcriptId)
                } else {
                    _state.update { it.copy(error = "Transcription completed without a saved result.", processing = false, processingProgress = 0) }
                }
            }
            WorkInfo.State.CANCELLED -> _state.update { it.copy(processing = false, processingProgress = 0) }
            else -> _state.update { it.copy(error = terminal?.outputData?.getString(TranscriptionWorker.OUTPUT_ERROR) ?: "Transcription failed.", processing = false, processingProgress = 0) }
        }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            _state.update { it.copy(processing = false, error = error.message) }
        } finally { enqueueMutex.unlock() }
    }

    fun cancelTranscription() {
        activeTranscriptionWorkId?.let(workManager::cancelWorkById) ?: workManager.cancelAllWorkByTag(TranscriptionWorker.TAG)
        activeTranscriptionWorkId = null
        _state.update { it.copy(processing = false, processingProgress = 0) }
    }

    private suspend fun finishResult(result: Result<TranscriptionResult>, title: String, snapshot: StudioState, onComplete: (Long) -> Unit, audioFile: File?) {
        result.onSuccess { transcript ->
            val durationMs = audioFile?.let(::audioDurationMs)?.takeIf { it > 0 }
                ?: snapshot.elapsedSeconds * 1_000
            val id = app.database.transcriptDao().insert(TranscriptEntity(
                title = title, text = transcript.text, languageCode = transcript.language,
                engine = snapshot.engine.name,
                model = if (snapshot.engine == EngineKind.ONLINE_WHISPER) snapshot.onlineModel.apiId else snapshot.modelTier.name,
                createdAtEpochMs = System.currentTimeMillis(), durationMs = durationMs
            ))
            audioFile?.takeIf(File::isFile)?.let { source ->
                val extension = source.extension.ifBlank { "wav" }
                val destination = File(getApplication<Application>().filesDir, "recordings/$id.$extension")
                withContext(Dispatchers.IO) {
                    destination.parentFile?.mkdirs()
                    source.copyTo(destination, overwrite = true)
                    if (source.absolutePath != destination.absolutePath) source.delete()
                }
                app.database.audioMetadataDao().insert(AudioMetadataEntity(
                    transcriptId = id,
                    filePath = destination.absolutePath,
                    sizeBytes = destination.length()
                ))
            }
            currentFile = null
            recordingJob = null
            nativeJob = null
            recorder.resetWaveform()
            _amplitudes.value = List(56) { 0f }
            _state.value = _state.value.copy(
                latestTranscriptId = id,
                recording = false,
                processing = false,
                paused = false,
                elapsedSeconds = 0,
                processingProgress = 0,
                partialText = "",
                sourceName = "Recording.wav"
            )
            recordSuccessfulTranscription()
            onComplete(id)
        }.onFailure { _state.value = _state.value.copy(error = it.message, processing = false, processingProgress = 0) }
    }

    private fun audioDurationMs(file: File): Long = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_state.value.recording) {
                delay(1_000)
                if (!_state.value.paused && _state.value.recording) _state.value = _state.value.copy(elapsedSeconds = _state.value.elapsedSeconds + 1)
            }
        }
    }

    private fun installedModelSet(): Set<ModelTier> = ModelTier.entries.filter { tier ->
        File(modelDirectory, tier.fileName).let { it.isFile && it.length() >= (tier.downloadBytes * .95).toLong() }
    }.toSet()

    private fun recordSuccessfulTranscription() {
        val count = promptPrefs.getInt(SUCCESS_COUNT, 0) + 1
        promptPrefs.edit().putInt(SUCCESS_COUNT, count).apply()
        val milestone = count == 5 || (count > 5 && (count - 5) % 20 == 0)
        val oldEnough = System.currentTimeMillis() - promptPrefs.getLong(LAST_SUPPORT_PROMPT, 0L) >= 30L * 24 * 60 * 60 * 1_000
        if (milestone && oldEnough) _state.update { it.copy(showSupportPrompt = true) }
    }

    companion object {
        private const val ONLINE_DISCLOSURE = "online_disclosure_accepted"
        private const val SUCCESS_COUNT = "successful_transcriptions"
        private const val LAST_SUPPORT_PROMPT = "last_support_prompt"
    }
}
