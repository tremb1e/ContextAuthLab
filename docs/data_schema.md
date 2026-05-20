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
  "rule_hash": "sha256-hex",
  "created_at_wall_millis": 1710000000000
}
```

## Batch

The decompressed payload is UTF-8 JSON:

```json
{
  "batch_id": "uuid",
  "device_id": "64-char-lowercase-hex",
  "session_id": "uuid-or-null",
  "record_type": "collection",
  "collection_source": "BUILTIN_TASK",
  "app_package_name": "com.contextauth",
  "sampling_rate_hz": 100,
  "batch_duration_seconds": 5,
  "task_sequence": 4,
  "task_id": "C4",
  "task_name": "模拟手机设置",
  "task_intuitive_description": "多控件操作",
  "task_category": "C4",
  "task_session_id": "uuid",
  "task_started_at_wall_millis": 1710000000000,
  "task_elapsed_seconds_at_batch_end": 5,
  "app_version": "1.0.0",
  "rule_version": "1",
  "rule_hash": "sha256-hex",
  "consent_version": "1",
  "started_at_wall_millis": 1710000000000,
  "ended_at_wall_millis": 1710000005000,
  "base_elapsed_nanos": 123456789,
  "sensor_samples": [],
  "context_events": [],
  "context_features": [],
  "skip_events": [],
  "diagnostics": {
    "sensor_sample_count": 0,
    "context_event_count": 0,
    "redaction_applied": true,
    "compression": "lz4_frame",
    "encryption": "none",
    "sampling_rate_hz": 100
  }
}
```

`BUILTIN_TASK` requires non-null `task_sequence`, `task_id`, `task_name`, `task_intuitive_description`, `task_category`, `task_session_id`, `task_started_at_wall_millis`, and `task_elapsed_seconds_at_batch_end`. `task_id` and `task_category` use `C0` through `C6`; `task_sequence` is the numeric part. Current task labels are stable Chinese research labels in the payload, while the app UI localizes labels to Chinese or English according to system language. `THIRD_PARTY_APP` requires those task fields to be null.

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

## Context Event And Node Snapshot

Context events contain event metadata, redaction summary, and `root_nodes`. Node snapshots contain allowed structural fields only: class, hashed package/view id, bounds grid, booleans such as clickable/editable/scrollable, child count, redacted text placeholders, action summary, and depth.

Editable node text is replaced with `<EDITABLE_TEXT_DROPPED>` before serialization. Password nodes are omitted entirely. Package and view identifiers are SHA-256 hashes. Dynamic rules from `/api/v1/rules` may add pattern replacements or package skips; the current server payload intentionally contains no extra dynamic rules.

Server ingest treats raw Accessibility/UI field keys as invalid payload shape. Keys such as `text`, `contentDescription`, `content_description`, `package_name`, `view_id`, and `window_title` are quarantined with `raw_accessibility_field:<field>`. Valid batches must include `diagnostics.redaction_applied: true`; a false or missing value fails schema validation.

## Context Feature

Features include counts and heuristic scores such as `editable_count`, `scrollable_count`, `clickable_count`, `media_like_score`, `form_like_score`, `node_class_histogram`, nominal task fields (`task_sequence`, `task_id`, `task_name`, `task_intuitive_description`, `task_category`), and independent `estimated_context_category`.

## Redaction Rule

`GET /api/v1/rules` is schema-backed even when the current remote UI rule list is
empty. `rule_hash` is computed over the payload excluding `rule_hash`.

```json
{
  "version": "1",
  "updated_at": "2026-05-18T00:00:00Z",
  "rules": [],
  "package_blocklist": [],
  "max_text_length": 128,
  "default_text_action": "REDACT",
  "rule_hash": "sha256-hex"
}
```
