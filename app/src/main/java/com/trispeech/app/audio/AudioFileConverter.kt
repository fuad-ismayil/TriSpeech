package com.trispeech.app.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decodes Android-supported audio into the canonical PCM stream expected by whisper.cpp. */
object AudioFileConverter {
    private const val OUTPUT_SAMPLE_RATE = 16_000
    private const val DEQUEUE_TIMEOUT_US = 10_000L

    fun prepareForWhisper(input: File, convertedOutput: File): File {
        require(input.isFile && input.length() > 0) { "The selected audio file is empty or unavailable." }
        if (isCanonicalWhisperWave(input)) return input

        runCatching { decodeToCanonicalWave(input, convertedOutput) }.getOrElse { error ->
            convertedOutput.delete()
            throw IllegalArgumentException(
                "This audio file could not be decoded on this device. Try MP3, M4A, FLAC, OGG, WebM, or WAV.",
                error
            )
        }
        require(convertedOutput.length() > 44) { "The selected audio file contains no decodable audio." }
        return convertedOutput
    }

    internal fun isCanonicalWhisperWave(file: File): Boolean {
        if (!file.isFile || file.length() < 44) return false
        val header = ByteArray(44)
        val bytesRead = file.inputStream().use { input ->
            var offset = 0
            while (offset < header.size) {
                val count = input.read(header, offset, header.size - offset)
                if (count < 0) break
                offset += count
            }
            offset
        }
        if (bytesRead != header.size) return false
        val values = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        return String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(header, 8, 4, Charsets.US_ASCII) == "WAVE" &&
            String(header, 12, 4, Charsets.US_ASCII) == "fmt " &&
            values.getShort(20).toInt() == 1 &&
            values.getShort(22).toInt() == 1 &&
            values.getInt(24) == OUTPUT_SAMPLE_RATE &&
            values.getShort(34).toInt() == 16 &&
            String(header, 36, 4, Charsets.US_ASCII) == "data"
    }

    private fun decodeToCanonicalWave(input: File, output: File) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var codecStarted = false
        try {
            extractor.setDataSource(input.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track was found.")
            extractor.selectTrack(trackIndex)

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("The audio codec is unknown.")
            inputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()
            codecStarted = true

            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var inputEnded = false
            var outputEnded = false
            val bufferInfo = MediaCodec.BufferInfo()

            WaveFileWriter(output, OUTPUT_SAMPLE_RATE, 1, 16).use { writer ->
                val resampler = MonoResampler(writer, sampleRate)
                while (!outputEnded) {
                    if (!inputEnded) {
                        val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex) ?: error("Decoder input buffer is unavailable.")
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime.coerceAtLeast(0), 0)
                                extractor.advance()
                            }
                        }
                    }

                    when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outputFormat = codec.outputFormat
                            sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else {
                                AudioFormat.ENCODING_PCM_16BIT
                            }
                            resampler.inputSampleRate = sampleRate
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (outputIndex >= 0) {
                            if (bufferInfo.size > 0) {
                                val outputBuffer = codec.getOutputBuffer(outputIndex) ?: error("Decoder output buffer is unavailable.")
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                                resampler.write(outputBuffer, channelCount, pcmEncoding)
                            }
                            outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
                resampler.finish()
            }
        } finally {
            if (codecStarted) runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }

    private class MonoResampler(
        private val writer: WaveFileWriter,
        var inputSampleRate: Int
    ) {
        private val output = ShortArray(4_096)
        private var outputSize = 0
        private var phase = 0L

        fun write(buffer: ByteBuffer, channels: Int, encoding: Int) {
            require(channels > 0 && inputSampleRate > 0)
            val bytesPerSample = when (encoding) {
                AudioFormat.ENCODING_PCM_8BIT -> 1
                AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                else -> 2
            }
            while (buffer.remaining() >= bytesPerSample * channels) {
                var sum = 0L
                repeat(channels) { sum += readSample(buffer, encoding).toLong() }
                accept((sum / channels).coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort())
            }
        }

        fun finish() = flush()

        private fun accept(sample: Short) {
            phase += OUTPUT_SAMPLE_RATE
            while (phase >= inputSampleRate) {
                output[outputSize++] = sample
                if (outputSize == output.size) flush()
                phase -= inputSampleRate
            }
        }

        private fun flush() {
            if (outputSize > 0) {
                writer.write(output, outputSize)
                outputSize = 0
            }
        }

        private fun readSample(buffer: ByteBuffer, encoding: Int): Short = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> (((buffer.get().toInt() and 0xff) - 128) shl 8).toShort()
            AudioFormat.ENCODING_PCM_FLOAT -> (buffer.float.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            AudioFormat.ENCODING_PCM_32BIT -> (buffer.int shr 16).toShort()
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                val raw = (buffer.get().toInt() and 0xff) or
                    ((buffer.get().toInt() and 0xff) shl 8) or
                    ((buffer.get().toInt() and 0xff) shl 16)
                val signed = if (raw and 0x800000 != 0) raw or -0x1000000 else raw
                (signed shr 8).toShort()
            }
            else -> buffer.short
        }
    }
}
