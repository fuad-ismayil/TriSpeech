package com.trispeech.app.models

import android.os.StatFs
import com.trispeech.app.transcription.ModelTier
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class ModelDownloadSpec(val tier: ModelTier, val url: String, val sha256: String)

class ModelDownloadManager(private val directory: File, private val client: OkHttpClient = OkHttpClient()) {
    suspend fun download(spec: ModelDownloadSpec, progress: (Long, Long) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            directory.mkdirs()
            val destination = File(directory, spec.tier.fileName)
            val partial = File(directory, "${spec.tier.fileName}.part")
            val existing = partial.takeIf(File::exists)?.length() ?: 0L
            val available = StatFs(directory.absolutePath).availableBytes
            require(available >= (spec.tier.downloadBytes - existing).coerceAtLeast(0) + 16L * 1024 * 1024) { "Insufficient storage for model" }
            val request = Request.Builder().url(spec.url).apply { if (existing > 0) header("Range", "bytes=$existing-") }.build()
            client.newCall(request).execute().use { response ->
                require(response.isSuccessful || response.code == 206) { "Download failed with HTTP ${response.code}" }
                val body = requireNotNull(response.body)
                val append = existing > 0 && response.code == 206
                if (!append) partial.delete()
                RandomAccessFile(partial, "rw").use { output ->
                    if (append) output.seek(existing) else output.setLength(0)
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = if (append) existing else 0L
                        val total = downloaded + body.contentLength().coerceAtLeast(0)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count); downloaded += count; progress(downloaded, total)
                        }
                    }
                }
            }
            require(sha256(partial).equals(spec.sha256, ignoreCase = true)) { "Model integrity check failed" }
            require(partial.renameTo(destination)) { "Unable to finalize model download" }
            destination
        }
    }

    fun isInstalled(tier: ModelTier): Boolean = File(directory, tier.fileName).let { it.isFile && it.length() >= (tier.downloadBytes * .95).toLong() }

    suspend fun remove(tier: ModelTier): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val model = File(directory, tier.fileName)
            val partial = File(directory, "${tier.fileName}.part")
            require(!model.exists() || model.delete()) { "Unable to delete model" }
            require(!partial.exists() || partial.delete()) { "Unable to delete partial download" }
        }
    }

    suspend fun removeAll(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ModelTier.entries.forEach { tier ->
                val model = File(directory, tier.fileName)
                val partial = File(directory, "${tier.fileName}.part")
                require(!model.exists() || model.delete()) { "Unable to delete ${tier.fileName}" }
                require(!partial.exists() || partial.delete()) { "Unable to delete ${tier.fileName}.part" }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
