# Test Results

Verified on 2026-05-20 in `/data/paper/sp/app_exp/ContextAuthLab`.

## Commands

```bash
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/home/tremb1e/Android/Sdk ANDROID_SDK_ROOT=/home/tremb1e/Android/Sdk ./gradlew :android-app:testDebugUnitTest
PYTHONPATH=server pytest -q server/tests
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/home/tremb1e/Android/Sdk ANDROID_SDK_ROOT=/home/tremb1e/Android/Sdk ./gradlew :android-app:assembleDebug
mkdir -p artifacts
cp android-app/build/outputs/apk/debug/android-app-debug.apk artifacts/contextauthlab-debug.apk
sha256sum artifacts/contextauthlab-debug.apk
docker build -t contextauthlab/server:latest ./server
bash tools/test_e2e.sh
bash tools/test_docker_deployment.sh
docker image inspect contextauthlab/server:latest --format '{{.Id}} {{.Config.User}}'
```

## Results

- Android unit tests: passed.
- Server pytest: `33 passed`, including raw UI field quarantine and `diagnostics.redaction_applied` schema checks.
- Debug APK build: passed; artifact copied to `artifacts/contextauthlab-debug.apk`.
- APK SHA-256: `5e029bc92dc945da4aea7a64cb24492655a0fee170fa52fa7ef9800c7d34cb0e`.
- Server Docker image: built as `contextauthlab/server:latest`.
- Server image ID: `sha256:31b16180de16ec7e14d8ba0d07549845d0bd0ddf045d19e55ff8740c10582ebb`.
- Server image runtime user: `appuser`.
- E2E ingest: passed; validated `/health`, `/api/v1/config`, `/api/v1/rules`, sample batch ingest, category storage, metrics, logs, and persistence after compose restart.
- Docker deployment smoke: passed after a clean serial rerun; validated non-root image, healthcheck, writable bind mounts, sample ingest, metrics, no `/dashboard` route, and data persistence after restart.

## Notes

- Compose startup can emit transient curl connection-reset messages before the container is ready; both scripts wait and passed.
- Android compile emitted existing Compose/Gradle deprecation warnings only.

## Remaining Manual Checks

- Real device or emulator interaction should still verify Accessibility permission flow, battery optimization UI, notification permission UI, tilt-maze hand feel, wrist animation framing, and system-language switching.
- China-region NTP uses UDP/123; networks that block UDP NTP should show a generic server-time fallback source without displaying NTP host names in app UI.
