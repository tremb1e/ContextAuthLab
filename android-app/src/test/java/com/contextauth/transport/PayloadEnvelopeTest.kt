package com.contextauth.transport

import com.contextauth.core.Batch
import com.contextauth.core.CollectionSource
import com.contextauth.core.JsonCodec
import com.contextauth.core.SensorSample
import net.jpountz.lz4.LZ4FrameInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID

class PayloadEnvelopeTest {
    @Test
    fun lz4FrameEnvelopeRoundtrip() {
        val batch = Batch(
            batchId = UUID.randomUUID().toString(),
            deviceId = "a".repeat(64),
            sessionId = UUID.randomUUID().toString(),
            collectionSource = CollectionSource.THIRD_PARTY_APP,
            taskCategory = null,
            taskSessionId = null,
            taskStartedAtWallMillis = null,
            taskElapsedSecondsAtBatchEnd = null,
            startedAtWallMillis = 1000,
            endedAtWallMillis = 6000,
            baseElapsedNanos = 123,
            sensorSamples = listOf(SensorSample("ACCELEROMETER", 1, 1000, 0.1f, 0.2f, 9.8f, 3)),
            touchEvents = emptyList(),
            contextEvents = emptyList(),
            contextFeatures = emptyList(),
            skipEvents = emptyList()
        )
        val envelope = JsonCodec.buildEnvelope(batch, "1", "b".repeat(64))
        assertEquals("LZ4_FRAME+JSON", envelope.algorithm)
        val compressed = Base64.getDecoder().decode(envelope.payloadBase64)
        assertEquals(envelope.payloadSha256Hex, JsonCodec.sha256Bytes(compressed))
        val decoded = LZ4FrameInputStream(ByteArrayInputStream(compressed)).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(256)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
            }
            out.toByteArray().toString(Charsets.UTF_8)
        }
        assertTrue(decoded.contains("\"encryption\":\"none\""))
        compressed[compressed.lastIndex] = (compressed.last() + 1).toByte()
        assertNotEquals(envelope.payloadSha256Hex, JsonCodec.sha256Bytes(compressed))
    }
}
