# Privacy Model

## Collected Fields

- Motion sensors: sensor type, elapsed timestamp, server-offset wall time estimate, x/y/z values, and accuracy.
- Touch timing: global screen touch interaction start/end timestamps (`uptime` and wall-clock estimate) while collection is active. Touch coordinates, paths, pressure, and contact size are not collected.
- UI context: Accessibility event type, plaintext foreground `app_package_name`, foreground Activity/ComponentName, input-method visibility, coarse orientation, allowed node structure fields, `viewIdResourceName`, non-editable visible component text, and derived context features.
- Diagnostics: sample counts, redaction status, compression type, queue/upload metadata, and rule lineage.

## Not Collected

- IMEI, serial, MAC, MediaDrm ID, or other non-resettable hardware identifiers.
- Screenshots, screen recording, raw keystrokes, automatic input, automatic clicks, gestures, or remote control actions.
- Touch trajectories, touch coordinates, pressure, contact size, or pointer paths.
- Password nodes or their descendants.
- Raw input field text.
- Input method dynamics such as per-key timestamps, key intervals, key hold duration, or per-character text-change events.

## Device ID And Sessions

Android computes:

```text
device_id = lowercase_hex(HMAC-SHA256(
  key = serverStudySalt,
  message = Settings.Secure.ANDROID_ID
))
```

The default study salt is `Continuous_Authentication`. Hardware identifiers are excluded because they are not resettable by the participant and would create unnecessary re-identification risk.

Every uploaded batch also carries a non-empty `session_id`. For built-in tasks, `session_id` equals `task_session_id`; for foreground app collection, it is a collection-session UUID shared by batches from the same continuous collection period.

## Redaction

Client-side redaction is the primary protection. Email, phone, URL, long number, card-like, ID-number-like, and token-like content in visible component text is replaced with placeholders. Non-editable component text such as button labels is retained after those replacements so UI semantics like `确认`/`取消` remain available for research. Editable text is dropped. Password subtrees are dropped. Foreground app package names, Activity/ComponentName, and `viewIdResourceName` are uploaded in plaintext by design.

Remote rules from `/api/v1/rules` can add text/content-description redaction patterns, but failures or invalid entries do not disable the built-in baseline. Package-name skip behavior has been removed: the app collects each foreground app's accessible UI and applies redaction instead of dropping whole packages.

The server no longer performs the former secondary sensitive-string/raw-UI scan. It still validates the envelope, compressed payload hash, LZ4/JSON shape, Pydantic schema, task contract, editable-text dropping, password-node absence, and `diagnostics.redaction_applied: true`.

## Storage

Server stores data under `data/paper/devices/{device_id}/`. `device_id` is regex validated before use in paths. Path traversal is rejected by validation and safe path resolution.
