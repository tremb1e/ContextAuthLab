package com.contextauth.transport

import com.contextauth.core.Batch
import com.contextauth.core.CollectionSource
import com.contextauth.core.ContextEventSnapshot
import com.contextauth.core.JsonCodec
import com.contextauth.core.NodeSnapshot
import com.contextauth.core.SensorSample
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BatchSerializationTest {
    @Test
    fun batchJsonDoesNotLeakRawEditableText() {
        val batch = Batch(
            batchId = UUID.randomUUID().toString(),
            deviceId = "a".repeat(64),
            collectionSource = CollectionSource.THIRD_PARTY_APP,
            taskCategory = null,
            taskSessionId = null,
            taskStartedAtWallMillis = null,
            taskElapsedSecondsAtBatchEnd = null,
            startedAtWallMillis = 1_000,
            endedAtWallMillis = 6_000,
            baseElapsedNanos = 123,
            sensorSamples = listOf(SensorSample("ACCELEROMETER", 10, 1_001, 0f, 0f, 9.8f, 3)),
            contextEvents = listOf(
                ContextEventSnapshot(
                    eventType = "TYPE_VIEW_TEXT_CHANGED",
                    eventTimeWallMillis = 1_100,
                    packageNameHash = "b".repeat(64),
                    windowTitleRedacted = "<DROPPED>",
                    rootNodes = listOf(
                        NodeSnapshot(
                            nodeId = "node-1",
                            packageNameHash = "b".repeat(64),
                            className = "android.widget.EditText",
                            viewIdHash = null,
                            textRedacted = "<EDITABLE_TEXT_DROPPED>",
                            contentDescRedacted = null,
                            clickable = true,
                            editable = true,
                            scrollable = false,
                            password = false,
                            childCount = 0,
                            depth = 0
                        )
                    ),
                    redactionSummary = mapOf("dropped_editable_texts" to 1)
                )
            ),
            contextFeatures = emptyList(),
            skipEvents = emptyList()
        )

        val json = JsonCodec.batchToJson(batch, "1", "b".repeat(64))
        assertTrue(json.contains("<EDITABLE_TEXT_DROPPED>"))
        assertFalse(json.contains("secret"))
        assertTrue(json.contains("\"encryption\":\"none\""))
    }
}
