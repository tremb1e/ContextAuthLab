# Deployment

## System Requirements

- Docker 20.10 or newer.
- Docker Compose v2.
- x86_64 or aarch64 Linux host.
- 2 GB RAM minimum.
- 50 GB free disk recommended for study data.

## Quick Start

```bash
git clone <repo>
cd ContextAuthLab
cp .env.example .env
docker compose up -d
curl http://127.0.0.1:8000/health
```

Set `SERVER_STUDY_SALT` once and keep it stable. Losing or changing it changes every derived research `device_id`.

## Environment

- `SERVER_PORT`: host port, default `8000`.
- `SERVER_STUDY_SALT`: stable study salt. Default for this prototype is `Continuous_Authentication`.
- `RULES_VERSION`: redaction rules version, default `1`.
- `INGEST_REQUIRE_AUTH`: reserved, default `false`.
- `TIME_SYNC_REGION`: advisory region in `/api/v1/config`, default `CN`.
- `TIME_SYNC_NTP_SERVERS`: comma-separated advisory NTP hosts in `/api/v1/config`; defaults to China-region public/cloud hosts.
- `TIME_SYNC_MAX_ACCEPTABLE_RTT_MILLIS`: advisory client clock-sync RTT limit, default `3000`.
- `TZ`: container timezone.
- `VERSION`: Docker image tag.
- `DATA_VOLUME` and `LOG_VOLUME`: production override paths.

## Modes

The checked-in Dockerfile uses `python:3.11-slim-bookworm` and installs from the offline `server/vendor/wheels` directory, so the container build does not need external package index access. The runtime stage contains only a Python-based healthcheck shim, installed Python dependencies, and the API code. It does not contain tests, docs, `.git`, Node, npm, yarn, templates, static assets, or dashboard code.

Local development:

```bash
docker compose up
```

Build a local server image for deployment:

```bash
docker build -t contextauthlab/server:latest ./server
docker image inspect contextauthlab/server:latest
```

Single-machine background deployment:

```bash
cp .env.example .env
docker compose up -d
docker compose logs -f contextauthlab-server
```

Production override:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## Health And Troubleshooting

```bash
docker compose ps
docker compose logs -f contextauthlab-server
curl http://127.0.0.1:8000/health
docker compose exec contextauthlab-server sh
docker compose exec contextauthlab-server ls -la /data
```

ClockSync troubleshooting:

```bash
curl -fsS http://127.0.0.1:8000/api/v1/config
```

The Android app syncs on resume and then every 60 seconds. It first tries the China-region NTP hosts advertised in `timeSync.recommendedNtpServers`. If UDP/123 is blocked, it falls back to the config response `serverTimeMillis`.

The app uses these NTP hosts internally only. Home and Diagnostics display generic ClockSync sources such as `NTP synced` or `Server time fallback`; they do not display individual NTP server addresses.

Release builds disable cleartext traffic and trust only system certificate
authorities through the main network security config. Debug builds keep
cleartext and user CA trust enabled for emulator/lab endpoints such as
`http://10.0.2.2:8000`.

## Android APK

Debug APK build:

```bash
JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/home/tremb1e/Android/Sdk ./gradlew :android-app:assembleDebug
mkdir -p artifacts
cp android-app/build/outputs/apk/debug/android-app-debug.apk artifacts/contextauthlab-debug.apk
```

Install on a test device:

```bash
adb install -r artifacts/contextauthlab-debug.apk
```

After installation, enable AccessibilityService, battery optimization exemption, and notification permission. The app starts collection automatically once `/health`, ClockSync, screen/unlock state, and Wi-Fi policy are ready.

For UI verification, switch the device system language between Chinese and English and relaunch the app. Participant-facing screens, task instructions, protocol text, notification copy, settings, diagnostics, and dialogs should follow the system language.

Home's `Collection Status` card is intentionally about server connectivity. It should show connected/disconnected state, latest health-check time, and latest server response.

## Data Backup

Backup:

```bash
tar -czf contextauthlab-data-$(date +%Y%m%d).tar.gz data/paper logs
```

Restore:

```bash
tar -xzf contextauthlab-data-YYYYMMDD.tar.gz
docker compose up -d
```

For larger deployments, use `rsync -a data/paper/ backup-host:/path/`.

## Upgrade

Prebuilt image:

```bash
docker compose pull
docker compose up -d
```

Self-built:

```bash
git pull
docker compose build --no-cache
docker compose up -d
```

## Cleanup

Archive old data before deleting. A future `tools/prune_data/paper.py` can be mounted into the container for dry-run cleanup; current deployments should use manual retention review.

## Metrics

Prometheus scrape example:

```yaml
scrape_configs:
  - job_name: contextauthlab
    static_configs:
      - targets: ["127.0.0.1:8000"]
```

## TLS Reverse Proxy

Caddy:

```caddyfile
cca.macrz.com {
    reverse_proxy 127.0.0.1:8000
}
```

Nginx:

```nginx
server {
    server_name cca.macrz.com;
    listen 443 ssl http2;
    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

## Recovery

`server_study_salt.txt` loss or `SERVER_STUDY_SALT` changes invalidate stable device IDs. Back up `SERVER_DATA_DIR` daily, preferably at 00:00.

## Resource Baseline

- 5 devices: under 0.25 CPU, under 256 MB RAM, low disk growth.
- 50 devices: around 0.5 CPU, under 512 MB RAM, depends on sensor/context volume.
- 500 devices: use external monitoring, log rotation, and disk capacity planning.

## Security

Do not expose port 8000 directly to the public internet. Put the service behind a firewall and terminate TLS with Caddy or Nginx. The service runs as non-root `appuser` and uses bind-mounted host directories for direct research data access.
