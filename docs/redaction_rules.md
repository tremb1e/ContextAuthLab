# Redaction Rules

Default client-side safety logic:

- Password node: drop subtree.
- Editable node: drop text and emit `<EDITABLE_TEXT_DROPPED>`.
- Email: `<EMAIL>`.
- Phone: `<PHONE>`.
- URL: `<URL>`.
- Continuous 4+ digit number: `<NUM>`.
- Card-like 13-19 digit number with optional spaces or hyphens: `<CARD>`.
- Chinese ID-number-like string: `<ID_NUM>`.
- Token-like long random/base64/hex strings: `<TOKEN>`.
- Over-length text: `<LONG_TEXT_DROPPED>`.
- Non-editable visible UI component text is retained after fixed-format sensitive replacements, so button labels and other UI semantics remain available.
- `contentDescription` and window-title text continue to use redacted placeholder fields.

Package-level skip/blocklist behavior has been removed. Android no longer drops entire apps based on package name; every foreground app's accessible UI is collected and then redacted on device.

The server rule payload lives in an independent JSON file. `SERVER_RULES_FILE` defaults to `${SERVER_DATA_DIR}/rules.json`; if missing, the server materializes it from `server/app/default_rules.json` on startup and then initializes active rules from that file. These rules mirror the built-in baseline so clients can refresh policy and rule lineage from `/api/v1/rules` without weakening on-device safety.

The remote payload carries explicit policy metadata:

- `max_text_length`: `128`.
- `default_text_action`: `REDACT`.
- `rule_hash`: SHA-256 over compact canonical JSON for the rule payload excluding `rule_hash`; object keys are sorted and separators contain no whitespace.
- `rules`: default text rules for email, China mobile number, URL, China ID-like number, payment-card-like number, token-like strings, and long numbers.
- `package_blocklist`: retained as an empty compatibility field; Android ignores package-name skip rules.

Android treats remote rules as extensions rather than a hard dependency. Missing or mismatched `rule_hash` values, a failed rules request, or an invalid regex do not stop collection. Invalid regex entries are skipped, package-name targets are ignored, `content_description` rules apply only to content descriptions, and built-in baseline redaction remains active.

Server does not rely on server-side redaction as primary protection and no longer performs the former secondary sensitive-string/prose/raw-field scan. It validates schema-level safety: editable nodes must not contain raw `text`, password nodes must be absent, batches must report `diagnostics.redaction_applied: true`, diagnostics counts must match the payload arrays, and context features must reference events in the same batch.
