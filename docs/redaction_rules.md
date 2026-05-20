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
- Sensitive packages such as dialer, contacts, SMS, banking, payment, medical, password manager, and private messaging: skip event.

The current server rule payload intentionally ships no additional frontend UI redaction rules: `rules` and `package_blocklist` are empty. The client keeps the local safety logic above, and the cloud rule fetch path remains available for future study revisions.

The empty remote payload still carries explicit policy metadata:

- `max_text_length`: `128`.
- `default_text_action`: `REDACT`.
- `rule_hash`: SHA-256 over the rule payload excluding `rule_hash`.
- `rules`: empty list for future structured UI text/content-description/node rules.
- `package_blocklist`: empty list for future package-name block entries.

Server does not rely on server-side redaction as primary protection. It quarantines batches that still contain obvious fixed-format sensitive strings.

Server also rejects batches that claim raw Accessibility/UI fields instead of the redacted/hash fields expected by the schema. Exact raw field keys such as `text`, `contentDescription`, `content_description`, `package_name`, `view_id`, and `window_title` are quarantined before storage. Batches must report `diagnostics.redaction_applied: true`.
