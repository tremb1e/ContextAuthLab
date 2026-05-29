# Data Schema

## Config Response

`GET /api/v1/config` keeps `serverTimeMillis` as the stable top-level HTTP
midpoint clock-sync value. `timeSync` is advisory metadata for clients that want
to display or use regional NTP fallbacks.

```json
{
  "serverStudySalt": "Continuous_Authentication",
  "rulesVersion": "1",
  "serverTimeMillis": 1710000000000,
  "timeSync": {
    "method": "HTTP_MIDPOINT",
    "region": "CN",
    "serverTimeField": "serverTimeMillis",
    "recommendedNtpServers": [
      "ntp.aliyun.com",
      "ntp.tencent.com",
      "0.cn.pool.ntp.org",
      "1.cn.pool.ntp.org",
      "2.cn.pool.ntp.org",
      "3.cn.pool.ntp.org"
    ],
    "maxAcceptableRttMillis": 3000
  }
}
```

## Payload Envelope

```json
{
  "algorithm": "LZ4_FRAME+JSON",
  "payload_base64": "<base64 LZ4 frame bytes>",
  "payload_sha256_hex": "<sha256 compressed bytes>",
  "device_id": "64-char-lowercase-hex",
  "batch_id": "uuid",
  "rule_version": "1",
  "rule_hash": "sha256-hex-or-64-zero-when-remote-rules-unavailable",
  "created_at_wall_millis": 1710000000000
}
```

## Batch

The decompressed payload is UTF-8 JSON:

```json
{
  "batch_id": "uuid",
  "device_id": "64-char-lowercase-hex",
  "session_id": "non-empty collection-or-task-session-id",
  "record_type": "collection",
  "collection_source": "BUILTIN_TASK",
  "app_package_name": "com.example.target",
  "foreground_activity_class_name": "com.example.target.MainActivity",
  "foreground_component_name": "com.example.target/.MainActivity",
  "sampling_rate_hz": 100,
  "batch_duration_seconds": 5,
  "task_sequence": 4,
  "task_id": "C4",
  "task_name": "Simulated phone settings",
  "task_intuitive_description": "Multi-control operation",
  "task_category": "C4",
  "task_session_id": "uuid",
  "task_started_at_wall_millis": 1710000000000,
  "task_elapsed_seconds_at_batch_end": 5,
  "app_version": "1.0.0",
  "rule_version": "1",
  "rule_hash": "sha256-hex-or-64-zero-when-remote-rules-unavailable",
  "consent_version": "1",
  "started_at_wall_millis": 1710000000000,
  "ended_at_wall_millis": 1710000005000,
  "base_elapsed_nanos": 123456789,
  "sensor_samples": [],
  "touch_events": [],
  "context_events": [],
  "context_features": [],
  "skip_events": [],
  "diagnostics": {
    "sensor_sample_count": 0,
    "context_event_count": 0,
    "touch_event_count": 0,
    "redaction_applied": true,
    "compression": "lz4_frame",
    "encryption": "none",
    "sampling_rate_hz": 100
  }
}
```

`session_id` is always populated by the Android app. For built-in tasks it equals `task_session_id`; for foreground app collection it is a collection session UUID so batches from the same continuous collection period can be grouped.

`BUILTIN_TASK` requires non-null `task_sequence`, `task_id`, `task_name`, `task_intuitive_description`, `task_category`, `task_session_id`, `task_started_at_wall_millis`, and `task_elapsed_seconds_at_batch_end`. `task_id` and `task_category` use `C0` through `C7`; `task_sequence` is the numeric part. Current task labels are stable English research labels in the payload, while the app UI localizes labels to Chinese or English according to system language. `THIRD_PARTY_APP` requires task-specific fields other than `session_id` to be null.

Server validation also checks that diagnostic sample/event counts match the actual arrays, that diagnostics `sampling_rate_hz` matches the batch sampling rate when present, and that each context feature uses the same source/task metadata as the enclosing batch. A context feature `event_id` must reference an event in the same batch's `context_events`.

## Sensor Sample

```json
{
  "sensor_type": "MAGNETIC_FIELD",
  "timestamp_elapsed_nanos": 123456789,
  "wall_time_estimated_millis": 1710000000000,
  "x": 0.0,
  "y": 0.0,
  "z": 0.0,
  "accuracy": 3
}
```

## Touch Event

Touch events are emitted by the AccessibilityService for global screen touch interactions while collection is active. The service returns before UI-window traversal when collection is not active, which avoids unnecessary Accessibility workload while preserving the enabled-service state. Touch events contain detailed timing and intentionally omit coordinates, trajectories, pressure, and contact size.

```json
{
  "event_id": "uuid",
  "event_type": "TOUCH_INTERACTION_START",
  "event_time_uptime_millis": 123456789,
  "event_time_wall_millis": 1710000000123,
  "collected_at_wall_millis": 1710000000124
}
```

Current global `event_type` values are `TOUCH_INTERACTION_START` and `TOUCH_INTERACTION_END`. The server also accepts legacy in-app timing values (`TOUCH_DOWN`, `TOUCH_UP`, `TOUCH_POINTER_DOWN`, `TOUCH_POINTER_UP`, and `TOUCH_CANCEL`) for previously generated payloads.

## Context Event And Node Snapshot

Context events contain event metadata, foreground context, input-method visibility, coarse orientation, redaction summary, and `root_nodes`. Node snapshots contain allowed structural fields: class, `viewIdResourceName`, visible non-editable component text, bounds grid, booleans such as clickable/long-clickable/editable/scrollable/visible/enabled/focused/selected/checkable, child count, redacted content-description placeholders, action summary, and depth. The old `package_name_hash` and `view_id_hash` fields are not emitted.

```json
{
  "event_id": "uuid",
  "event_type": "TYPE_WINDOW_CONTENT_CHANGED",
  "event_time_wall_millis": 1710000000123,
  "app_package_name": "com.example.target",
  "foreground_activity_class_name": "com.example.target.MainActivity",
  "foreground_component_name": "com.example.target/.MainActivity",
  "input_method_visible": false,
  "coarse_orientation": "portrait",
  "window_title_redacted": "<TEXT_REDACTED>",
  "root_nodes": [
    {
      "node_id": "node-1",
      "class_name": "android.widget.Button",
      "viewIdResourceName": "com.example.target:id/confirm",
      "text": "确认",
      "text_redacted": null,
      "content_desc_redacted": null,
      "clickable": true,
      "editable": false,
      "scrollable": false,
      "checkable": false,
      "checked": false,
      "enabled": true,
      "focused": false,
      "selected": false,
      "visible_to_user": true,
      "long_clickable": false,
      "password": false,
      "child_count": 0,
      "actions_summary": ["CLICK"],
      "depth": 0
    }
  ],
  "redaction_summary": {}
}
```

`app_package_name` is the plaintext foreground app package name being collected, not the ContextAuthLab package. `foreground_activity_class_name` and `foreground_component_name` are best-effort values derived from Accessibility window-state events and active application windows. `input_method_visible` is true when Accessibility reports an input-method window. `coarse_orientation` is captured when the Accessibility event is processed and may be `portrait`, `landscape`, `portrait_reverse`, `landscape_reverse`, or `unknown`; it is also copied into derived context features. The app does not emit per-character text-change events, per-key timestamps, key intervals, key hold durations, touch coordinates, or touch trajectories.

Editable node text is replaced with `<EDITABLE_TEXT_DROPPED>`/`null` before serialization, and password nodes are omitted entirely. Non-editable visible component text is retained after fixed-format sensitive substrings are replaced with placeholders such as `<EMAIL>`, `<PHONE>`, `<URL>`, `<CARD>`, `<ID_NUM>`, `<TOKEN>`, or `<NUM>`. Dynamic rules from `/api/v1/rules` provide text/content-description pattern replacements. Package-name skip behavior has been removed; every foreground app UI is collected and redacted.

Server ingest no longer performs the former secondary raw-field/sensitive-text scan. Pydantic schema validation remains active: non-editable `text` and `viewIdResourceName` are allowed, editable nodes with raw `text` fail schema validation, password nodes must be absent, valid batches must include `diagnostics.redaction_applied: true`, diagnostics counts must match the payload arrays, and context features must reference context events in the same batch; a false or missing redaction marker fails schema validation.

## Context Feature

Features include counts and heuristic scores such as `editable_count`, `scrollable_count`, `clickable_count`, `password_node_seen`, `media_like_score`, `form_like_score`, `game_like_score`, `node_class_histogram`, `input_method_visible`, backwards-compatible `keyboard_visible_estimated`, `coarse_orientation`, nominal task fields (`task_sequence`, `task_id`, `task_name`, `task_intuitive_description`, `task_category`), and independent `estimated_context_category`.

## Redaction Rule

`GET /api/v1/rules` is schema-backed and returns redaction rules initialized from `SERVER_RULES_FILE`. If that file is missing, the
server creates it from the packaged default rules before serving traffic.
`rule_hash` is computed over the payload excluding `rule_hash` and should not be
stored inside the editable rules file. Ingest stores `rule_version` and
`rule_hash` as client lineage metadata; they do not have to match the active
server rules for an otherwise valid redacted batch to be accepted.

```json
{
  "version": "1",
  "updated_at": "2026-05-21T00:00:00Z",
  "rules": [{"id": "email", "target": "text", "action": "REDACT", "pattern": "...", "replacement": "<EMAIL>"}],
  "package_blocklist": [],
  "max_text_length": 128,
  "default_text_action": "REDACT",
  "rule_hash": "sha256-hex"
}
```
