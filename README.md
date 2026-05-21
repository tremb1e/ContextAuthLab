# ContextAuthLab

ContextAuthLab is a research prototype for collecting Android motion sensor data and redacted UI/component context for early-stage continuous authentication studies.

Current scope:

- Android collects accelerometer, gyroscope, magnetic field, and Accessibility UI context after explicit consent.
- Android redacts UI context before upload.
- Android starts collection automatically when consent, Accessibility, battery optimization, notification permission, server health, ClockSync, fetched redaction rules, screen state, and the Wi-Fi policy are all ready.
- Android performs ClockSync on app entry and every 60 seconds, using China-region NTP hosts first and falling back to server config time when UDP NTP is unavailable.
- Android localizes participant-facing UI, task copy, protocol text, diagnostics, settings, dialogs, and notification copy to Chinese or English based on system language.
- Android Home's `Collection Status` card reports server connection state, latest health-check time, and latest server response; NTP host names are not displayed in app UI.
- Android serializes redacted batches as UTF-8 JSON, compresses them with LZ4 frame, and uploads a payload envelope every batch interval.
- Server is a pure FastAPI API and disk storage service. It receives envelopes, verifies SHA-256, decompresses LZ4 frame, validates schema, checks for obvious unredacted sensitive text and raw Accessibility/UI field names, serves config/rules, and stores by `device_id`.

Not implemented in this stage: authentication models, context routing models, MoE, training code, inference code, external rule-management consoles, or server frontend pages.

## Privacy Boundary

- No hidden collection, automatic clicking, automatic input, remote control, screenshots, keylogging, or Accessibility actions.
- Accessibility collection may read only accessible node structure and allowed fields.
- Raw input field text is dropped. Password nodes are dropped. Fixed-format sensitive strings and ordinary UI prose are redacted before upload.
- `/api/v1/rules` returns schema-backed default UI redaction rules and a package blocklist; the client keeps built-in baseline redaction active even if remote rules cannot be fetched.
- Server ingest rejects batches that carry raw UI keys such as `text`, `contentDescription`, `package_name`, `view_id`, or `window_title`, rejects prose inside `*_redacted` UI fields, and requires `diagnostics.redaction_applied: true`.
- `device_id` is a research ID: lowercase hex HMAC-SHA256 over `Settings.Secure.ANDROID_ID` using stable `serverStudySalt`.
- Server accepts only `device_id` matching `^[a-f0-9]{64}$`.
- Payload content is not encrypted in this phase. Confidentiality relies on HTTPS/TLS 1.2+ in deployment; local HTTP is for emulator/dev only.

## Layout

```text
android-app/             Android project (APK source)
server/                  FastAPI ingest service
docs/                    privacy, schema, protocol, and test docs
tools/                   sample ingest, e2e, docker, and load scripts
docker-compose.yml       root compose for server
Makefile                 common test commands
```

## Android App

```bash
cd ContextAuthLab
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/home/tremb1e/Android/Sdk ./gradlew :android-app:testDebugUnitTest :android-app:assembleDebug
```

Enable AccessibilityService on device: Android Settings -> Accessibility -> ContextAuthLab -> enable, then return to the app. Also allow battery optimization exemption and notification permission.

The target app flow is: Consent -> permission onboarding -> Home. When the three permissions, server `/health`, ClockSync, screen/unlock state, and Wi-Fi policy are satisfied, collection/upload starts automatically. The hidden researcher server switch is available through the version/footer gesture and the Home server-host gesture. C0-C6 built-in tasks each run for 30 seconds and record `task_id`, `task_sequence`, `task_name`, and `task_intuitive_description`. See [manual_test_plan.md](docs/manual_test_plan.md).

## Server

```bash
python3 -m pip install -r server/requirements-dev.txt
PYTHONPATH=server pytest -q server/tests
PYTHONPATH=server uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Docker:

```bash
cp .env.example .env
docker compose up -d --build
curl http://127.0.0.1:8000/health
```

Build the server image explicitly:

```bash
docker build -t contextauthlab/server:latest ./server
```

## Sample Ingest

```bash
python tools/send_sample_batch.py --server http://127.0.0.1:8000 --count 6 --task-category C3
```

Stored files appear under `data/paper/devices/{device_id}/{date}/` and category symlinks under `data/paper/devices/{device_id}/by_category/{task_category}/{date}/`.

## Tests

```bash
make test-server
make test-android
make test-e2e
make test-docker
```

`tools/test_load.py` is a manual pressure test and is not intended for default CI.

The latest local verification summary is in [test_results.md](docs/test_results.md).

## Artifacts

- APK: `artifacts/contextauthlab-debug.apk`
- Server image: `contextauthlab/server:latest`
