# Server API

## `GET /health`

Returns `{"status":"ok"}`.

## `GET /api/v1/config`

Returns stable `serverStudySalt`, `rulesVersion`, and top-level `serverTimeMillis`.
`serverTimeMillis` remains the backwards-compatible HTTP midpoint clock-sync field.

The response also includes advisory clock-sync metadata. Defaults are China-region
NTP hosts and can be overridden with `TIME_SYNC_NTP_SERVERS`:

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

## `GET /api/v1/rules`

Returns the current UI redaction rule payload and `rule_hash`. The current study
payload intentionally has empty `rules` and `package_blocklist`; clients should
continue using local safety redaction plus `default_text_action`.

Current payload:

```json
{
  "version": "1",
  "updated_at": "2026-05-18T00:00:00Z",
  "rules": [],
  "package_blocklist": [],
  "max_text_length": 128,
  "default_text_action": "REDACT",
  "rule_hash": "c61b3eddebddd53da56231d76875b5706ade2e0ce724a59d9e4f5dae500e9df8"
}
```

The Android client fetches this endpoint on startup/health success and after
server URL changes. Empty rules are valid and mean no additional remote
redaction beyond the built-in on-device baseline.

## `POST /api/v1/ingest`

Accepts LZ4 frame JSON envelope. Valid requests are stored on disk. Invalid hash, invalid algorithm, bad IDs, corrupt LZ4, schema failures, task-label contract failures, and sensitive text findings are rejected or quarantined.

The server expects Accessibility-derived UI values to arrive as redacted or hashed fields only, such as `text_redacted`, `content_desc_redacted`, `window_title_redacted`, `package_name_hash`, and `view_id_hash`. Raw UI field keys such as `text`, `contentDescription`, `content_description`, `package_name`, `view_id`, and `window_title` are quarantined with `raw_accessibility_field:<field>`. Batches with `diagnostics.redaction_applied` other than `true` fail schema validation.

## `GET /metrics`

Prometheus exposition format. Does not include `device_id` or `batch_id`.
