package com.contextauth.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class Uploader(
    private val context: Context,
    private val client: OkHttpClient
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val queueDir: File = File(context.filesDir, "upload_queue").apply { mkdirs() }
    private val deadLetterDir: File = File(context.filesDir, "dead_letter").apply { mkdirs() }
    private val metadata = QueueMetadataStore(context)
    private var droppedDueToQuota = 0

    suspend fun uploadOrQueue(serverUrl: String, envelope: PayloadEnvelope, allowQueue: Boolean = true): Result<String> = withContext(Dispatchers.IO) {
        val body = JsonCodec.envelopeToJson(envelope)
        runCatching {
            postEnvelope(serverUrl, body)
        }.onFailure {
            if (allowQueue) writeQueue(envelope, body, it.message ?: it::class.java.simpleName)
        }
    }

    fun queueOnly(envelope: PayloadEnvelope, reason: String) {
        writeQueue(envelope, JsonCodec.envelopeToJson(envelope), reason)
    }

    suspend fun replayDue(serverUrl: String): Int = withContext(Dispatchers.IO) {
        var replayed = 0
        val now = System.currentTimeMillis()
        metadata.dueEntries(now).forEach { item ->
            val file = File(queueDir, item.fileName)
            if (!file.exists()) {
                metadata.delete(item.fileName)
                return@forEach
            }
            val body = file.readText(Charsets.UTF_8)
            runCatching { postEnvelope(serverUrl, body) }
                .onSuccess {
                    file.delete()
                    metadata.delete(item.fileName)
                    replayed += 1
                }
                .onFailure { error ->
                    val nextRetry = item.retryCount + 1
                    if (FailureQueuePolicy.shouldDeadLetter(nextRetry)) {
                        file.renameTo(File(deadLetterDir, file.name))
                        metadata.delete(item.fileName)
                    } else {
                        metadata.upsert(
                            item.copy(
                                retryCount = nextRetry,
                                lastError = error.message ?: error::class.java.simpleName,
                                nextRetryAtWallMillis = now + FailureQueuePolicy.fullJitterDelayMillis(nextRetry, Math.random())
                            )
                        )
                    }
                }
        }
        replayed
    }

    fun clearQueue() {
        queueDir.listFiles()?.forEach { it.deleteRecursively() }
        deadLetterDir.listFiles()?.forEach { it.deleteRecursively() }
        metadata.clear()
    }

    fun queueEntries(): Int = metadata.count()
    fun queueBytes(): Long = metadata.totalBytes()
    fun droppedDueToQuota(): Int = droppedDueToQuota
    fun earliestQueueEntryAt(): Long = metadata.oldest()?.createdAt ?: 0L

    private fun postEnvelope(serverUrl: String, body: String): String {
        val request = Request.Builder()
            .url(serverUrl.trimEnd('/') + "/api/v1/ingest")
            .post(body.toRequestBody(jsonMedia))
            .build()
        val start = System.nanoTime()
        client.newCall(request).execute().use { resp ->
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            val responseText = resp.body?.string()?.take(120).orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}: $responseText")
            return "HTTP ${resp.code}, ${elapsedMs}ms"
        }
    }

    private fun writeQueue(envelope: PayloadEnvelope, body: String, reason: String) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8).size.toLong()
        while (queueBytes() + bodyBytes > FailureQueuePolicy.QUEUE_LIMIT_BYTES) {
            val oldest = metadata.oldest() ?: break
            File(queueDir, oldest.fileName).delete()
            metadata.delete(oldest.fileName)
            droppedDueToQuota += 1
        }
        val file = File(queueDir, "${envelope.createdAtWallMillis}-${envelope.batchId}.json")
        file.writeText(body, Charsets.UTF_8)
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
        metadata.upsert(
            QueueMetadata(
                fileName = file.name,
                batchId = envelope.batchId,
                createdAt = envelope.createdAtWallMillis,
                sizeBytes = bodyBytes,
                retryCount = 0,
                lastError = reason.take(240),
                nextRetryAtWallMillis = System.currentTimeMillis() + FailureQueuePolicy.fullJitterDelayMillis(0, Math.random())
            )
        )
    }
}

object FailureQueuePolicy {
    const val QUEUE_LIMIT_BYTES: Long = 200L * 1024L * 1024L
    const val MAX_RETRY_COUNT: Int = 20
    private const val BASE_BACKOFF_MILLIS: Long = 5_000L
    private const val CAP_BACKOFF_MILLIS: Long = 5L * 60L * 1000L

    fun cappedBackoffMillis(retryCount: Int): Long {
        val exponent = retryCount.coerceAtLeast(0).coerceAtMost(20)
        val raw = BASE_BACKOFF_MILLIS * (1L shl exponent).coerceAtMost(1L shl 20)
        return raw.coerceAtMost(CAP_BACKOFF_MILLIS)
    }

    fun fullJitterDelayMillis(retryCount: Int, jitterFraction: Double): Long {
        val bounded = jitterFraction.coerceIn(0.0, 1.0)
        return (cappedBackoffMillis(retryCount) * bounded).toLong()
    }

    fun shouldDeadLetter(retryCount: Int): Boolean = retryCount >= MAX_RETRY_COUNT
}
