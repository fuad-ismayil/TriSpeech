package com.trispeech.app.network

import android.content.Context
import com.trispeech.app.BuildConfig
import com.trispeech.app.transcription.TranscriptSegment
import com.trispeech.app.transcription.TranscriptionRequest
import com.trispeech.app.transcription.TranscriptionResult
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class BackendClient(
    context: Context,
    private val baseUrl: String = BuildConfig.BACKEND_BASE_URL.trimEnd('/'),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.MINUTES)
        .readTimeout(2, TimeUnit.MINUTES)
        .callTimeout(3, TimeUnit.MINUTES)
        .build()
) {
    private val identity = InstallationIdentity(context.applicationContext)
    private val registrationMutex = Mutex()
    @Volatile private var registered = false

    suspend fun transcribe(request: TranscriptionRequest, onProgress: (Int) -> Unit): TranscriptionResult = withContext(Dispatchers.IO) {
        ensureRegistered()
        require(request.audioFile.length() <= MAX_UPLOAD_BYTES) { "Online audio is limited to 25 MiB." }
        onProgress(5)

        val logicalOperationId = request.operationId ?: UUID.randomUUID().toString()
        val uploadKey = "$logicalOperationId-upload"
        val logicalJobKey = "$logicalOperationId-job"
        val mime = request.audioFile.audioMediaType()
        val createBody = JSONObject()
            .put("fileName", request.audioFile.name)
            .put("sizeBytes", request.audioFile.length())
            .put("mediaType", mime)
            .toString()
        val upload = executeJson(signedRequest("POST", "/v1/uploads", createBody, uploadKey))
        val uploadId = upload.getString("uploadId")
        val uploadPath = upload.optString("uploadPath", "/v1/uploads/$uploadId/content")
        onProgress(12)

        if (!upload.optBoolean("complete")) {
            val fileHash = sha256(request.audioFile)
            executeEmpty(
                signedRequest(
                    method = "PUT",
                    path = uploadPath,
                    body = request.audioFile.asRequestBody(mime.toMediaType()),
                    bodyHash = fileHash,
                    idempotencyKey = uploadId
                )
            )
            executeJson(signedRequest("POST", "/v1/uploads/$uploadId/complete", "{}", uploadId))
        }
        onProgress(25)

        val jobBody = JSONObject()
            .put("uploadId", uploadId)
            .put("language", request.languageCode)
            .put("model", "gpt-4o-mini-transcribe")
            .toString()
        val job = executeJson(signedRequest("POST", "/v1/transcription-jobs", jobBody, logicalJobKey))
        val jobId = job.getString("jobId")

        repeat(240) { attempt ->
            val status = executeJson(signedRequest("GET", "/v1/transcription-jobs/$jobId", null, logicalJobKey))
            when (status.getString("status")) {
                "succeeded" -> return@withContext status.toTranscriptionResult(request.languageCode)
                "failed", "cancelled" -> throw IOException(status.optString("message", "Online transcription failed."))
            }
            onProgress((25 + attempt / 4).coerceAtMost(94))
            delay(750)
        }
        throw IOException("Online transcription timed out. Your unused credit remains available.")
    }

    private suspend fun ensureRegistered() {
        if (registered) return
        registrationMutex.withLock {
            if (registered) return
            val body = JSONObject()
                .put("installationId", identity.installationId)
                .put("publicKey", identity.publicKey)
                .put("platform", "android")
                .toString()
            executeJson(signedRequest("POST", "/v1/installations/register", body, identity.installationId))
            registered = true
        }
    }

    private suspend fun signedRequest(method: String, path: String, body: String?, idempotencyKey: String): Request =
        signedRequest(method, path, body?.toRequestBody(JSON), body?.toByteArray()?.let(InstallationIdentity::sha256) ?: EMPTY_HASH, idempotencyKey)

    private suspend fun signedRequest(method: String, path: String, body: RequestBody?, bodyHash: String, idempotencyKey: String): Request {
        require(baseUrl.startsWith("https://") || BuildConfig.DEBUG) { "The production backend must use HTTPS." }
        val timestamp = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString()
        val canonical = listOf(method, path, timestamp, nonce, idempotencyKey, bodyHash).joinToString("\n")
        return Request.Builder()
            .url(if (path.startsWith("http")) path else "$baseUrl$path")
            .method(method, body)
            .header("X-Installation-Id", identity.installationId)
            .header("X-Request-Timestamp", timestamp)
            .header("X-Request-Nonce", nonce)
            .header("Idempotency-Key", idempotencyKey)
            .header("X-Body-SHA256", bodyHash)
            .header("X-Request-Signature", identity.sign(canonical))
            .build()
    }

    private fun executeJson(request: Request): JSONObject = client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IOException(JSONObject(body.ifBlank { "{}" }).optString("message", "Service error (${response.code})."))
        JSONObject(body.ifBlank { "{}" })
    }

    private fun executeEmpty(request: Request) = client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Upload failed (${response.code}).")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val MAX_UPLOAD_BYTES = 25L * 1024 * 1024
        private val EMPTY_HASH = InstallationIdentity.sha256(ByteArray(0))
    }
}

private fun JSONObject.toTranscriptionResult(fallbackLanguage: String): TranscriptionResult {
    val result = optJSONObject("result") ?: this
    val segmentsJson = result.optJSONArray("segments") ?: JSONArray()
    val segments = buildList {
        for (index in 0 until segmentsJson.length()) {
            val item = segmentsJson.getJSONObject(index)
            add(TranscriptSegment(item.optLong("startMs"), item.optLong("endMs"), item.optString("text").trim()))
        }
    }
    return TranscriptionResult(result.getString("text"), result.optString("language", fallbackLanguage), segments)
}

private fun File.audioMediaType(): String = when (extension.lowercase()) {
    "flac" -> "audio/flac"
    "mp3", "mpga" -> "audio/mpeg"
    "mp4", "m4a" -> "audio/mp4"
    "ogg" -> "audio/ogg"
    "webm" -> "audio/webm"
    else -> "audio/wav"
}
