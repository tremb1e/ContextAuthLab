# Test Results

Verified on 2026-05-21 in `/data/paper/sp/app_exp/ContextAuthLab`.

## Commands

```bash
git diff --check
PYTHONPATH=server pytest -q server/tests
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/home/tremb1e/Android/Sdk ANDROID_SDK_ROOT=/home/tremb1e/Android/Sdk ./gradlew :android-app:testDebugUnitTest
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/home/tremb1e/Android/Sdk ANDROID_SDK_ROOT=/home/tremb1e/Android/Sdk ./gradlew :android-app:assembleDebug
mkdir -p artifacts
cp android-app/build/outputs/apk/debug/android-app-debug.apk artifacts/contextauthlab-debug.apk
sha256sum artifacts/contextauthlab-debug.apk
docker build -t contextauthlab/server:latest ./server
docker image inspect contextauthlab/server:latest --format '{{.Id}} {{.Config.User}}'
bash tools/test_e2e.sh
bash tools/test_docker_deployment.sh
docker image inspect contextauthlab/server:latest --format '{{.Id}} {{.Config.User}}'
```

## Results

- Diff whitespace check: passed.
- Android unit tests: `BUILD SUCCESSFUL`.
- Server pytest: `36 passed`, including non-empty default rules, raw UI field quarantine, prose-in-`*_redacted` quarantine, and `diagnostics.redaction_applied` schema checks.
- Debug APK build: passed; artifact copied to `artifacts/contextauthlab-debug.apk`.
- APK SHA-256: `763865a10d92f35a03421fcfbc23b181233e6e3cc58a0d33d86e9924c5bdf9d1`.
- Server Docker image: built as `contextauthlab/server:latest`.
- Final server image ID: `sha256:12b26fb28a7363e6fd285e0ce065687d5cd5f132e206226ce879689e6c952a82`.
- Server image runtime user: `appuser`.
- E2E ingest: passed; validated `/health`, `/api/v1/config`, `/api/v1/rules`, sample batch ingest, category storage, metrics, logs, and persistence after compose restart.
- Docker deployment smoke: passed; validated non-root image, healthcheck, writable bind mounts, sample ingest, metrics, no `/dashboard` route, and data persistence after restart.

## Notes

- Compose startup emitted transient curl connection-reset messages before the container was ready; both scripts wait and passed.
- Android compile emitted existing Compose/Accessibility deprecation warnings only.
- Release network security disables cleartext and user CA trust; debug keeps lab/emulator cleartext enabled for local testing.

## Remaining Manual Checks

- Real device or emulator interaction should still verify Accessibility permission flow, battery optimization UI, notification permission UI, tilt-maze hand feel, wrist animation framing, and system-language switching.
- China-region NTP uses UDP/123; networks that block UDP NTP should show a generic server-time fallback source without displaying NTP host names in app UI.
