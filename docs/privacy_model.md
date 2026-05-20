# Privacy Model

## Collected Fields

- Motion sensors: sensor type, elapsed timestamp, server-offset wall time estimate, x/y/z values, and accuracy.
- UI context: Accessibility event type, hashed package name, redacted window title, allowed node structure fields, and derived context features.
- Diagnostics: sample counts, redaction status, compression type, and upload metadata.

## Not Collected

- IMEI, serial, MAC, MediaDrm ID, or other non-resettable hardware identifiers.
- Screenshots, screen recording, raw keystrokes, automatic input, automatic clicks, gestures, or remote control actions.
- Password nodes or their descendants.
- Raw input field text.
- Raw UI text by default.

## Device ID

Android computes:

```text
device_id = lowercase_hex(HMAC-SHA256(
  key = serverStudySalt,
  message = Settings.Secure.ANDROID_ID
))
```

The default study salt is `Continuous_Authentication`. Hardware identifiers are excluded because they are not resettable by the participant and would create unnecessary re-identification risk.

## Redaction

Client-side redaction is the primary protection. Email, phone, URL, long number, card-like, ID-number-like, and token-like content is replaced with placeholders. Editable text is dropped. Password subtrees are dropped.

Server performs only a second-line check. If obvious unredacted sensitive strings are found, the batch is quarantined and rejected. Server also rejects payloads that use raw Accessibility/UI field keys such as `text`, `contentDescription`, `package_name`, `view_id`, or `window_title`, and requires `diagnostics.redaction_applied: true`.

## Storage

Server stores data under `data/paper/devices/{device_id}/`. `device_id` is regex validated before use in paths. Path traversal is rejected by validation and safe path resolution.
