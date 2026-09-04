package com.trispeech.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AudioRecorderEngine(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object { const val SAMPLE_RATE = 16_000 }
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val _amplitudes = MutableStateFlow(List(56) { 0f })
    val amplitudes: StateFlow<List<Float>> = _amplitudes.asStateFlow()

    fun pause() { paused.set(true) }
    fun resume() { paused.set(false) }
    fun stop() { running.set(false) }
    fun resetWaveform() { _amplitudes.value = List(56) { 0f } }

    suspend fun recordTo(file: File): Result<File> = withContext(ioDispatcher) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return@withContext Result.failure(SecurityException("RECORD_AUDIO permission is required"))
        }
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return@withContext Result.failure(IllegalStateException("No supported microphone configuration"))
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2)
        val buffer = ShortArray(minBuffer)
        try {
            resetWaveform()
            WaveFileWriter(file).use { writer ->
                running.set(true)
                recorder.startRecording()
                while (running.get()) {
                    if (paused.get()) { Thread.sleep(30); continue }
                    val count = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (count < 0) throw IllegalStateException("AudioRecord failed with code $count")
                    val rms = writer.write(buffer, count)
                    _amplitudes.value = (_amplitudes.value.drop(1) + rms)
                }
            }
            Result.success(file)
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            recorder.release()
            running.set(false)
        }
    }
}
