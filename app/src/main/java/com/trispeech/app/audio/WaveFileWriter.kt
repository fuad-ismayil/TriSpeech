package com.trispeech.app.audio

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.sqrt

class WaveFileWriter(
    file: File,
    private val sampleRate: Int = 16_000,
    private val channels: Int = 1,
    private val bitsPerSample: Int = 16
) : Closeable {
    private val output = RandomAccessFile(file, "rw")
    private var pcmBytes = 0L

    init {
        require(sampleRate > 0 && channels > 0 && bitsPerSample == 16)
        output.setLength(0)
        output.write(ByteArray(44))
    }

    fun write(samples: ShortArray, count: Int = samples.size): Float {
        require(count in 0..samples.size)
        var sumSquares = 0.0
        val bytes = ByteArray(count * 2)
        for (index in 0 until count) {
            val sample = samples[index].toInt()
            bytes[index * 2] = (sample and 0xff).toByte()
            bytes[index * 2 + 1] = ((sample ushr 8) and 0xff).toByte()
            val normalized = sample / 32768.0
            sumSquares += normalized * normalized
        }
        output.write(bytes)
        pcmBytes += bytes.size
        return if (count == 0) 0f else sqrt(sumSquares / count).toFloat().coerceIn(0f, 1f)
    }

    override fun close() {
        output.seek(0)
        val byteRate = sampleRate * channels * bitsPerSample / 8
        output.writeBytes("RIFF")
        writeLittleEndian((36 + pcmBytes).toInt(), 4)
        output.writeBytes("WAVEfmt ")
        writeLittleEndian(16, 4)
        writeLittleEndian(1, 2)
        writeLittleEndian(channels, 2)
        writeLittleEndian(sampleRate, 4)
        writeLittleEndian(byteRate, 4)
        writeLittleEndian(channels * bitsPerSample / 8, 2)
        writeLittleEndian(bitsPerSample, 2)
        output.writeBytes("data")
        writeLittleEndian(pcmBytes.toInt(), 4)
        output.close()
    }

    private fun writeLittleEndian(value: Int, byteCount: Int) {
        repeat(byteCount) { shift -> output.write((value ushr (8 * shift)) and 0xff) }
    }
}
