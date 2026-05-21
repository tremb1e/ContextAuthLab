package com.contextauth.transport

import com.contextauth.core.Batch
import com.contextauth.core.CollectionSource
import com.contextauth.core.ContextEventSnapshot
import com.contextauth.core.JsonCodec
import com.contextauth.core.NodeSnapshot
import com.contextauth.core.SensorSample
import org.json.JSONObject
import org.junit.Assert.assertEquals
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

    @Test
    fun batchJsonContainsAllowedUiComponentAndContentFields() {
        val event = ContextEventSnapshot(
            eventType = "TYPE_VIEW_CLICKED",
            eventTimeWallMillis = 1_100,
            packageNameHash = "b".repeat(64),
            windowTitleRedacted = "Settings",
            rootNodes = listOf(
                NodeSnapshot(
                    nodeId = "node-1",
                    packageNameHash = "b".repeat(64),
                    className = "android.widget.Button",
                    viewIdHash = "c".repeat(64),
                    textRedacted = "<TEXT_REDACTED>",
                    contentDescRedacted = "<TEXT_REDACTED>",
                    clickable = true,
                    editable = false,
                    scrollable = false,
                    password = false,
                    childCount = 0,
                    checkable = false,
                    checked = false,
                    enabled = true,
                    focused = false,
                    selected = false,
                    boundsGrid = mapOf("left" to 1, "top" to 2, "right" to 3, "bottom" to 4),
                    actionsSummary = listOf("CLICK"),
                    depth = 0
                )
            ),
            redactionSummary = mapOf("replaced_email" to 0)
        )
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
            sensorSamples = emptyList(),
            contextEvents = listOf(event),
            contextFeatures = emptyList(),
            skipEvents = emptyList()
        )

        val json = JsonCodec.batchToJson(batch, "1", "b".repeat(64))
        val node = JSONObject(json)
            .getJSONArray("context_events")
            .getJSONObject(0)
            .getJSONArray("root_nodes")
            .getJSONObject(0)

        assertEquals("android.widget.Button", node.getString("class_name"))
        assertEquals("c".repeat(64), node.getString("view_id_hash"))
        assertEquals("<TEXT_REDACTED>", node.getString("text_redacted"))
        assertEquals("<TEXT_REDACTED>", node.getString("content_desc_redacted"))
        assertTrue(node.getBoolean("clickable"))
        assertEquals(1, node.getJSONObject("bounds_grid").getInt("left"))
        assertEquals("CLICK", node.getJSONArray("actions_summary").getString(0))
        assertFalse(json.contains("\"text\":"))
        assertFalse(json.contains("\"view_id\":"))
        assertFalse(json.contains("Submit"))
    }

    @Test
    fun batchJsonEscapesControlCharactersInRedactedContent() {
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
            sensorSamples = emptyList(),
            contextEvents = listOf(
                ContextEventSnapshot(
                    eventType = "TYPE_VIEW_CLICKED",
                    eventTimeWallMillis = 1_100,
                    packageNameHash = "b".repeat(64),
                    windowTitleRedacted = "<TEXT_REDACTED>\r\n<TOKEN>",
                    rootNodes = emptyList(),
                    redactionSummary = emptyMap()
                )
            ),
            contextFeatures = emptyList(),
            skipEvents = emptyList()
        )

        val parsed = JSONObject(JsonCodec.batchToJson(batch, "1", "b".repeat(64)))
        assertEquals(
            "<TEXT_REDACTED>\r\n<TOKEN>",
            parsed.getJSONArray("context_events").getJSONObject(0).getString("window_title_redacted")
        )
    }
}
