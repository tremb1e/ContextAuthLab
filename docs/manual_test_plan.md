# Manual Test Plan

## First Launch Researcher Flows

Default server:

1. Launch app.
2. Consent.
3. Complete Accessibility, battery optimization, and notification permission onboarding.
4. Return to Home and wait for `/health` and ClockSync.
5. Verify collection starts automatically; no Home start/stop buttons are shown.

Local or self-hosted server:

1. Launch app and stay on Consent.
2. Long-press version text for 3 seconds, tap it 7 times within 800 ms intervals, or use the hidden Home server-host gesture after consent, to enter ResearcherSettings.
3. Set URL to `http://10.0.2.2:8000` or the lab server.
4. Test connectivity with `GET /health`.
5. Return, consent, complete onboarding, wait for ClockSync, then verify automatic collection starts.

Already-used app:

1. Open Settings.
2. Use the same hidden version gesture to enter ResearcherSettings.
3. Change URL, test connectivity, confirm.
4. Home should show the yellow researcher override banner.

## Collection Checks

1. Verify Home `Collection Status` shows server connection state, latest server health-check time, and latest server response. It must not show NTP host names.
2. Verify Home also shows server host only, masked device ID, rule version, Accessibility state, battery whitelist, notification permission, sensor availability/rates, and ClockSync state.
3. Confirm automatic collection does not start until `/api/v1/rules` has been fetched and the displayed rule hash is non-zero.
4. Run all 7 built-in tasks for the fixed 30 second duration.
5. Verify screen off or lock pauses collection, flushes the already-redacted partial batch when data exists, and resumes with the same task context after unlock.
6. Verify airplane mode writes failed envelopes to local queue, then network restoration replays them.
7. Verify server files under `data/paper/devices/{device_id}/`.
8. Inspect stored JSON for no raw input text, no password nodes, redaction placeholders, correct `collection_source`, and correct `task_id`, `task_sequence`, `task_name`, `task_intuitive_description`, and `task_category`.
9. Submit or unit-test a deliberately raw UI field such as `root_nodes[0].text`; server should quarantine it as `raw_accessibility_field:text` without storing the raw payload in quarantine.
10. Submit or unit-test prose inside `text_redacted`; server should quarantine it as `unredacted_ui_text:text_redacted`.
11. Inspect `logs/server.jsonl` for no raw payload, no complete device ID, and no unredacted text.
12. Confirm `/api/v1/rules` returns non-empty default redaction rules plus a package blocklist, and Android Diagnostics/Settings show the fetched rule version/hash.
13. Confirm ClockSync occurs on app resume and then refreshes about every 60 seconds. If UDP/123 is blocked, the source may show a generic server-time fallback; NTP host names should not appear in app UI.

## Built-In Task Checks

1. Open BuiltInTasks and confirm the single-column list shows C0-C6 with neutral copy, a `7/7` progress indicator, and the sitting posture requirement.
2. Run each task and confirm the detail page repeats the sitting posture requirement.
3. C0 shows a quiet clock; C1 shows the full study protocol inside a scrollable text box; C2 supports scrolling and expanding cards; C3 accepts mixed Chinese, English, and special-character input; C4 is named Simulated Phone Settings and exposes tabs, slider, radio buttons, switches, checkbox, buttons, chips, and a local note; C5 shows a tilt-controlled maze ball with walls that stop ball motion and a clear exit marker; C6 shows two responsive wrist animations with a stable forearm and wrist pivot, a fixed face plane, a phone-screen plane, and motion arcs: left-right fan sweep and forward-back wrist flexion.
4. For each task, tap the shield icon and confirm the privacy text says the task records only sensors and component structure, not original content.
5. Press power during a task. The countdown should freeze or stop, collection state should become paused, and no incomplete batch should be queued.
6. In every task's Collection Status card, verify measured sampling includes accelerometer, gyroscope, and magnetic field, plus sensor availability, event count, latest batch, and ClockSync.

## Language Checks

1. Set the system language to Chinese and relaunch; all participant-facing UI text should appear in Chinese.
2. Set the system language to English and relaunch; all participant-facing UI text should appear in English.
3. Protocol text, task labels, settings, diagnostics, notification copy, dialogs, and privacy notices must all switch with system language.
