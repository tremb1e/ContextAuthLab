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
payload has non-empty default rules and a package blocklist. Clients should
apply the fetched policy while keeping built-in baseline redaction active as a
safety fallback.

Current payload:

```json
{
  "version": "1",
  "updated_at": "2026-05-21T00:00:00Z",
  "rules": [
    {"id": "email", "target": "text", "action": "REDACT", "replacement": "<EMAIL>"},
    {"id": "phone_cn", "target": "text", "action": "REDACT", "replacement": "<PHONE>"},
    {"id": "url", "target": "text", "action": "REDACT", "replacement": "<URL>"},
    {"id": "id_number_cn", "target": "text", "action": "REDACT", "replacement": "<ID_NUM>"},
    {"id": "payment_card", "target": "text", "action": "REDACT", "replacement": "<CARD>"},
    {"id": "opaque_token", "target": "text", "action": "REDACT", "replacement": "<TOKEN>"},
    {"id": "long_number", "target": "text", "action": "REDACT", "replacement": "<NUM>"}
  ],
  "package_blocklist": ["dialer", "contacts", "sms", "bank", "pay", "medical", "password", "signal", "telegram", "whatsapp", "wechat"],
  "max_text_length": 128,
  "default_text_action": "REDACT",
  "rule_hash": "sha256-hex"
}
```

The Android client fetches this endpoint on startup/health success and after
server URL changes. Collection is gated until a non-zero `rule_hash` has been
fetched, and the client verifies that the hash matches the canonical rule
payload before applying it.

## `POST /api/v1/ingest`

Accepts LZ4 frame JSON envelope. Valid requests are stored on disk. Invalid hash, invalid algorithm, bad IDs, corrupt LZ4, schema failures, task-label contract failures, and sensitive text findings are rejected or quarantined.

The server expects Accessibility-derived UI values to arrive as redacted or hashed fields only, such as `text_redacted`, `content_desc_redacted`, `window_title_redacted`, `package_name_hash`, and `view_id_hash`. Raw UI field keys such as `text`, `contentDescription`, `content_description`, `package_name`, `view_id`, `viewIdResourceName`, and `window_title` are quarantined with `raw_accessibility_field:<field>`. Redacted UI content fields must contain placeholders such as `<TEXT_REDACTED>`, `<EMAIL>`, or `<DROPPED>` rather than prose. Batches with `diagnostics.redaction_applied` other than `true` fail schema validation.

## `GET /metrics`

Prometheus exposition format. Does not include `device_id` or `batch_id`.
