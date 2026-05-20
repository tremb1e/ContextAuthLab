# Prompt Requirements Matrix

| ID | Area | Requirement | Acceptance |
|---|---|---|---|
| APP-01 | App shell | Kotlin + Jetpack Compose + Material 3, minSdk 26, targetSdk 35, ContextAuthLab branding | Debug APK builds and launches as ContextAuthLab |
| APP-02 | Consent | Significant disclosure before collection | User must consent before Home collection can start |
| APP-03 | Onboarding | Accessibility, battery optimization, notification permission flow | Automatic collection remains blocked until all three permissions pass |
| APP-04 | Home | `Collection Status` displays server connection state, latest health-check time, and latest server response; host-only server target, masked device ID, rule version, permissions, sensors, ClockSync, automatic collection readiness | No Home start/stop buttons; no NTP host names in UI; collection starts automatically when permissions, server health, ClockSync, screen, and Wi-Fi policy pass |
| APP-05 | Researcher settings | Hidden version/Home server gesture, editable server URL, health test, reset, clear queue, export diagnostics | URL save requires confirmation and triggers ClockSync/config/rule fetch; override banner appears when URL is changed |
| APP-06 | Built-in tasks | Seven C0-C6 adult-oriented task categories with sitting posture guidance and task labels | C1 scrollable protocol, C4 simulated phone settings, C5 collision maze, C6 dual wrist guide with stable forearm, wrist pivot, left-right fan sweep, and forward-back flexion; BUILTIN_TASK batches include task fields |
| APP-07 | Diagnostics | Sensor rates, ScreenGate history, queue, upload, timing, ClockSync | Exported report contains metadata only |
| APP-08 | Device ID | HMAC-SHA256(serverStudySalt, ANDROID_ID), 64 lowercase hex, cached | No hardware IDs such as IMEI/serial/MAC/MediaDrm |
| APP-09 | Accessibility | Read-only node structure with allowed fields | No performAction, gestures, screenshots, auto-click, or keylogging |
| APP-10 | Sensor collection | Accelerometer, gyroscope, magnetic field at fixed 100 Hz | registerListener uses 10,000 us sampling period; status displays measured acc/gyro/mag rates |
| APP-11 | ScreenGate | Stop collection while screen off or locked | Partial buffers are discarded and not queued |
| APP-12 | Redaction | Drop password nodes, drop editable text, hash package/view IDs, redact fixed-format PII, apply server-provided rule payload when present | Current server rule list is empty; serialized batches contain no raw input text; server rejects raw UI keys and requires `diagnostics.redaction_applied: true` |
| APP-15 | Internationalization | Chinese and English UI content selected by system language | Major participant-facing UI, tasks, notification, protocol, diagnostics, and dialogs have Chinese/English variants |
| APP-13 | Transport | UTF-8 JSON -> LZ4 frame -> SHA-256 envelope over HTTP(S) | No AES/PBKDF2/AAD/public-key envelope protocol |
| APP-14 | Failure queue | filesDir/upload_queue, metadata DB, 200 MB FIFO, retry/dead letter | Offline batches are persisted and replayable |
| SRV-01 | Server API | Pure FastAPI API: health/config/rules/ingest/metrics | Config exposes China-region ClockSync metadata; no templates/static/dashboard routes |
| SRV-02 | Ingest | Validate envelope, hash, LZ4, JSON, Pydantic schema | Valid sample batch returns stored=true |
| SRV-03 | Privacy checks | Detect obvious unredacted email/phone/URL/card/ID, raw Accessibility/UI field keys, and missing redaction marker | Reject/quarantine without logging raw payload |
| SRV-04 | Storage | Store by device_id/date, meta, indexes, by_category, quarantine | Safe path resolution prevents traversal |
| SRV-05 | Logging/metrics | JSON lines logs and Prometheus metrics | No full device_id, raw payload, salt, key, or plain IP |
| DEV-01 | Docker | Single server service, non-root container, bind-mounted data/logs | `docker build -t contextauthlab/server:latest ./server` and `tools/test_docker_deployment.sh` pass |
| DEV-02 | Tests | Android unit, APK build, server pytest, E2E, Docker smoke | Commands in README and Makefile pass in this environment |

Explicitly out of scope: authentication model, context routing model, MoE, training, inference, hidden collection, screenshots, automatic control, gRPC/proto, AES content encryption, anomaly detection, Dashboard/Web UI.
