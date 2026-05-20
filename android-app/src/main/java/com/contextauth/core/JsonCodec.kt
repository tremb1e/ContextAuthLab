package com.contextauth.core

import com.contextauth.BuildConfig
import net.jpountz.lz4.LZ4FrameOutputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

object JsonCodec {
    const val ALGORITHM = "LZ4_FRAME+JSON"

    fun batchToJson(batch: Batch, ruleVersion: String, ruleHash: String): String = jsonObject(
        "batch_id" to batch.batchId,
        "device_id" to batch.deviceId,
        "session_id" to batch.taskSessionId,
        "record_type" to "collection",
        "collection_source" to batch.collectionSource.name,
        "app_package_name" to BuildConfig.APPLICATION_ID,
        "sampling_rate_hz" to SamplingConfig.SAMPLING_RATE_HZ,
        "batch_duration_seconds" to ((batch.endedAtWallMillis - batch.startedAtWallMillis).coerceAtLeast(0) / 1000L).toInt(),
        "task_sequence" to batch.taskCategory?.sequence,
        "task_id" to batch.taskCategory?.name,
        "task_name" to batch.taskCategory?.taskName,
        "task_intuitive_description" to batch.taskCategory?.intuitiveDescription,
        "task_category" to batch.taskCategory?.name,
        "task_session_id" to batch.taskSessionId,
        "task_started_at_wall_millis" to batch.taskStartedAtWallMillis,
        "task_elapsed_seconds_at_batch_end" to batch.taskElapsedSecondsAtBatchEnd,
        "app_version" to "1.0.0",
        "rule_version" to ruleVersion,
        "rule_hash" to ruleHash,
        "consent_version" to "1",
        "started_at_wall_millis" to batch.startedAtWallMillis,
        "ended_at_wall_millis" to batch.endedAtWallMillis,
        "base_elapsed_nanos" to batch.baseElapsedNanos,
        "sensor_samples" to batch.sensorSamples.map(::sensorJson),
        "context_events" to batch.contextEvents.map(::eventJson),
        "context_features" to batch.contextFeatures.map(::featureJson),
        "skip_events" to batch.skipEvents,
        "diagnostics" to mapOf(
            "sensor_sample_count" to batch.sensorSamples.size,
            "context_event_count" to batch.contextEvents.size,
            "sampling_rate_hz" to SamplingConfig.SAMPLING_RATE_HZ,
            "redaction_applied" to true,
            "compression" to "lz4_frame",
            "encryption" to "none",
            "gated_resume" to batch.gatedResume
        )
    )

    fun buildEnvelope(batch: Batch, ruleVersion: String, ruleHash: String): PayloadEnvelope {
        val jsonBytes = batchToJson(batch, ruleVersion, ruleHash).toByteArray(Charsets.UTF_8)
        val compressed = lz4Frame(jsonBytes)
        return PayloadEnvelope(
            algorithm = ALGORITHM,
            payloadBase64 = Base64.getEncoder().encodeToString(compressed),
            payloadSha256Hex = sha256Bytes(compressed),
            deviceId = batch.deviceId,
            batchId = batch.batchId,
            ruleVersion = ruleVersion,
            ruleHash = ruleHash,
            createdAtWallMillis = batch.startedAtWallMillis
        )
    }

    fun envelopeToJson(envelope: PayloadEnvelope): String = jsonObject(
        "algorithm" to envelope.algorithm,
        "payload_base64" to envelope.payloadBase64,
        "payload_sha256_hex" to envelope.payloadSha256Hex,
        "device_id" to envelope.deviceId,
        "batch_id" to envelope.batchId,
        "rule_version" to envelope.ruleVersion,
        "rule_hash" to envelope.ruleHash,
        "created_at_wall_millis" to envelope.createdAtWallMillis
    )

    fun lz4Frame(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        LZ4FrameOutputStream(out).use { stream -> stream.write(bytes) }
        return out.toByteArray()
    }

    fun sha256Bytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sensorJson(sample: SensorSample): Map<String, Any?> = mapOf(
        "sensor_type" to sample.sensorType,
        "timestamp_elapsed_nanos" to sample.timestampElapsedNanos,
        "wall_time_estimated_millis" to sample.wallTimeEstimatedMillis,
        "x" to sample.x.toDouble(),
        "y" to sample.y.toDouble(),
        "z" to sample.z.toDouble(),
        "accuracy" to sample.accuracy
    )

    private fun eventJson(event: ContextEventSnapshot): Map<String, Any?> = mapOf(
        "event_id" to event.eventId,
        "event_type" to event.eventType,
        "event_time_wall_millis" to event.eventTimeWallMillis,
        "package_name_hash" to event.packageNameHash,
        "window_title_redacted" to event.windowTitleRedacted,
        "root_nodes" to event.rootNodes.map(::nodeJson),
        "redaction_summary" to event.redactionSummary
    )

    private fun nodeJson(node: NodeSnapshot): Map<String, Any?> = mapOf(
        "node_id" to node.nodeId,
        "package_name_hash" to node.packageNameHash,
        "package_category" to null,
        "class_name" to node.className,
        "view_id_hash" to node.viewIdHash,
        "bounds_grid" to node.boundsGrid,
        "clickable" to node.clickable,
        "editable" to node.editable,
        "scrollable" to node.scrollable,
        "checkable" to node.checkable,
        "checked" to node.checked,
        "enabled" to node.enabled,
        "focused" to node.focused,
        "selected" to node.selected,
        "password" to false,
        "input_type_category" to if (node.editable) "text" else null,
        "child_count" to node.childCount,
        "text_redacted" to node.textRedacted,
        "content_desc_redacted" to node.contentDescRedacted,
        "actions_summary" to node.actionsSummary,
        "depth" to node.depth
    )

    private fun featureJson(feature: ContextFeature): Map<String, Any?> = mapOf(
        "feature_id" to feature.featureId,
        "event_id" to feature.eventId,
        "computed_at_wall_millis" to feature.computedAtWallMillis,
        "collection_source" to feature.collectionSource.name,
        "task_sequence" to feature.taskCategory?.sequence,
        "task_id" to feature.taskCategory?.name,
        "task_name" to feature.taskCategory?.taskName,
        "task_intuitive_description" to feature.taskCategory?.intuitiveDescription,
        "task_category" to feature.taskCategory?.name,
        "task_session_id" to feature.taskSessionId,
        "keyboard_visible_estimated" to (feature.editableCount > 0),
        "editable_count" to feature.editableCount,
        "scrollable_count" to feature.scrollableCount,
        "clickable_count" to feature.clickableCount,
        "password_node_seen" to false,
        "media_like_score" to feature.mediaLikeScore,
        "list_like_score" to feature.listLikeScore,
        "form_like_score" to feature.formLikeScore,
        "game_like_score" to feature.gameLikeScore,
        "node_class_histogram" to feature.nodeClassHistogram,
        "event_type" to feature.eventType,
        "coarse_orientation" to feature.coarseOrientation,
        "estimated_context_category" to feature.estimatedContextCategory
    )

    private fun jsonObject(vararg pairs: Pair<String, Any?>): String = jsonValue(pairs.toMap())

    private fun jsonValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
            jsonValue(k.toString()) + ":" + jsonValue(v)
        }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { jsonValue(it) }
        else -> jsonValue(value.toString())
    }
}
