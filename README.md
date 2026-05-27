# ContextAuthLab

ContextAuthLab is a research prototype for collecting Android motion sensor data and redacted UI/component context for early-stage continuous authentication studies.

Current scope:

- Android collects accelerometer, gyroscope, magnetic field, global screen touch timing, and Accessibility UI context after explicit consent.
- Android uploads the foreground app package name, foreground Activity/ComponentName, `viewIdResourceName`, visible component text such as button labels, and an input-method-visible flag alongside sensor samples and UI structure.
- Android records only global screen touch interaction start/end timestamps while collection is active; it does not upload touch positions, trajectories, pressure, or size.
- Android drops input-field text and password nodes, and redacts fixed-format sensitive strings before upload.
- Android starts collection automatically when consent, Accessibility, battery optimization, notification permission, a valid research `device_id`, and screen/unlock state are ready. Server health, ClockSync, Wi-Fi, and rule refresh failures do not block local collection; upload queues until policy/network conditions allow delivery.
- Android performs ClockSync on app entry and every 60 seconds, using China-region NTP hosts first and falling back to server config time when UDP NTP is unavailable.
- Android localizes participant-facing UI, task copy, protocol text, details, settings, dialogs, and notification copy to Chinese or English based on system language.
- Android Home's `Collection Status` card reports server connection state, automatic collection state, latest connectivity-test time, latest upload time, and latest server response. Tapping the server connection chip starts a fresh `/health` test; NTP host names are not displayed in app UI.
- Android Home reminds for missing Accessibility, battery optimization, and notification permissions one by one, while excluding server reachability and ClockSync from permission prompts.
- Android Details replaces Diagnostics and shows runtime, app/device, sensor, ScreenGate, upload/performance, local upload history, and ClockSync details without a one-click Diagnostics export button.
- Android serializes redacted batches as UTF-8 JSON, compresses them with LZ4 frame, and uploads a payload envelope every batch interval.
- Server is a pure FastAPI API and disk storage service. It receives envelopes, verifies SHA-256, decompresses LZ4 frame, validates schema, serves config/rules, and stores by `device_id`. The previous server-side secondary UI/text scan has been removed; client-side redaction is the primary protection.

Not implemented in this stage: authentication models, context routing models, MoE, training code, inference code, external rule-management consoles, or server frontend pages.

## Privacy Boundary

- No hidden collection, automatic clicking, automatic input, remote control, screenshots, keylogging, or Accessibility actions.
- Accessibility collection may read only accessible node structure, foreground app/activity metadata, resource IDs, visible component text, and input-method visibility.
- Touch collection records global screen interaction times only and excludes coordinates, paths, pressure, and contact size.
- Raw input field text is dropped. Password nodes are dropped. Fixed-format sensitive strings inside visible component text are redacted before upload. Per-key timestamps, key intervals, and key hold durations are not collected.
- `/api/v1/rules` returns schema-backed text/content redaction rules from an editable JSON file. Package-name skip/blocklist behavior has been removed from Android collection; foreground app UI is collected and then redacted.
- Server ingest accepts non-editable component `text`, `viewIdResourceName`, touch timing, and current/legacy UI metadata when the schema permits it; it still rejects editable nodes containing raw text and requires `diagnostics.redaction_applied: true`.
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

The target app flow is: Consent -> permission onboarding -> Home. When the three permissions, a valid research `device_id`, and screen/unlock state are satisfied, collection starts automatically; there is no third-party collection opt-in/out switch. Upload keeps sensor-only batches and also includes Accessibility UI context and global timing-only touch events when present. Server `/health`, ClockSync, Wi-Fi, and rule refresh run in the background and affect upload/retry metadata rather than blocking on-device sampling. The hidden researcher server switch is available through the version/footer gesture and the Home server-host gesture. C0-C7 built-in tasks record `task_id`, `task_sequence`, English `task_name`, English `task_intuitive_description`, and a non-empty `session_id`.

Task updates:

- C5 is now a landscape blue-ball tapping challenge. Tap Start to enter landscape, hit 30 randomly positioned balls, then the task passes and returns to the portrait task page.
- C6 is local video playback from `android-app/src/main/res/raw/c6_video.mp4` with play/pause, speed, seek, and portrait/landscape controls.
- The previous wrist task is now C7 and includes left-right swing, lateral translation, and forward-back flexion animations.

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

Build both distributable images after assembling the debug APK:

```bash
make build-images
```

This produces `contextauthlab/server:latest` and an APK artifact image
`contextauthlab/android-app-debug:latest`. The app image is not a runnable
Android environment; it stores the APK at `/artifacts/contextauthlab-debug.apk`
for registry-based delivery.

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

Use the commands above for the current local verification summary.

## Artifacts

- APK: `artifacts/contextauthlab-debug.apk`
- App artifact image: `contextauthlab/android-app-debug:latest`
- Server image: `contextauthlab/server:latest`
