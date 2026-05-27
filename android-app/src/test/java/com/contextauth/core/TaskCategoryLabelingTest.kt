package com.contextauth.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TaskCategoryLabelingTest {
    @Test
    fun builtinTaskSerializesRequiredTaskFields() {
        val sessionId = UUID.randomUUID().toString()
        val batch = Batch(
            batchId = UUID.randomUUID().toString(),
            deviceId = "a".repeat(64),
            sessionId = sessionId,
            collectionSource = CollectionSource.BUILTIN_TASK,
            taskCategory = TaskCategory.C4,
            taskSessionId = sessionId,
            taskStartedAtWallMillis = 1_000,
            taskElapsedSecondsAtBatchEnd = 5,
            startedAtWallMillis = 1_000,
            endedAtWallMillis = 6_000,
            baseElapsedNanos = 123,
            sensorSamples = emptyList(),
            touchEvents = emptyList(),
            contextEvents = emptyList(),
            contextFeatures = emptyList(),
            skipEvents = emptyList()
        )

        val json = JsonCodec.batchToJson(batch, "1", "b".repeat(64))
        assertTrue(json.contains("\"collection_source\":\"BUILTIN_TASK\""))
        assertTrue(json.contains("\"task_sequence\":4"))
        assertTrue(json.contains("\"task_id\":\"C4\""))
        assertTrue(json.contains("\"session_id\":\"$sessionId\""))
        assertTrue(json.contains("\"task_name\":\"Simulated phone settings\""))
        assertTrue(json.contains("\"task_intuitive_description\":\"Multi-control operation\""))
        assertTrue(json.contains("\"task_category\":\"C4\""))
        assertTrue(json.contains("\"task_session_id\":\"$sessionId\""))
        assertTrue(json.contains("\"task_elapsed_seconds_at_batch_end\":5"))
    }

    @Test
    fun thirdPartySerializesNullTaskFields() {
        val batch = Batch(
            batchId = UUID.randomUUID().toString(),
            deviceId = "a".repeat(64),
            sessionId = UUID.randomUUID().toString(),
            collectionSource = CollectionSource.THIRD_PARTY_APP,
            taskCategory = null,
            taskSessionId = null,
            taskStartedAtWallMillis = null,
            taskElapsedSecondsAtBatchEnd = null,
            startedAtWallMillis = 1_000,
            endedAtWallMillis = 6_000,
            baseElapsedNanos = 123,
            sensorSamples = emptyList(),
            touchEvents = emptyList(),
            contextEvents = emptyList(),
            contextFeatures = emptyList(),
            skipEvents = emptyList()
        )

        val json = JsonCodec.batchToJson(batch, "1", "b".repeat(64))
        assertTrue(json.contains("\"collection_source\":\"THIRD_PARTY_APP\""))
        assertTrue(json.contains("\"task_category\":null"))
        assertTrue(json.contains("\"task_session_id\":null"))
    }
}
