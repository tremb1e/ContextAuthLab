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
- Ordinary visible UI text that does not match a specific placeholder rule: `<TEXT_REDACTED>`.

The current server rule payload ships schema-backed default frontend UI redaction rules and a package blocklist. These rules mirror the built-in baseline so clients can refresh policy and rule lineage from `/api/v1/rules` without weakening on-device safety.

The remote payload carries explicit policy metadata:

- `max_text_length`: `128`.
- `default_text_action`: `REDACT`.
- `rule_hash`: SHA-256 over the rule payload excluding `rule_hash`.
- `rules`: default text rules for email, China mobile number, URL, China ID-like number, payment-card-like number, token-like strings, and long numbers.
- `package_blocklist`: default package-name fragments for dialer, contacts, SMS, banking, payment, medical, password manager, and private messaging apps.

Server does not rely on server-side redaction as primary protection. It quarantines batches that still contain obvious fixed-format sensitive strings or prose inside fields named `text_redacted`, `content_desc_redacted`, or `window_title_redacted`.

Server also rejects batches that claim raw Accessibility/UI fields instead of the redacted/hash fields expected by the schema. Exact raw field keys such as `text`, `contentDescription`, `content_description`, `package_name`, `view_id`, `viewIdResourceName`, and `window_title` are quarantined before storage. Batches must report `diagnostics.redaction_applied: true`.
