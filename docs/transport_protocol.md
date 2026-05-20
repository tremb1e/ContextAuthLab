# Transport Protocol

## Serialization

Android serializes the already-redacted batch object to UTF-8 JSON.

## Compression

The JSON bytes are compressed with LZ4 frame format. Raw LZ4 blocks are not accepted because they lack a self-describing frame header.

## Integrity

Android computes SHA-256 over compressed bytes and writes the lowercase hex digest to `payload_sha256_hex`. Server base64-decodes `payload_base64`, recomputes SHA-256 before decompression, and rejects mismatches with `payload_hash_mismatch`.

## Envelope

The request body for `POST /api/v1/ingest` is JSON with:

- `algorithm`: must be `LZ4_FRAME+JSON`.
- `payload_base64`: base64-encoded LZ4 frame bytes.
- `payload_sha256_hex`: SHA-256 of compressed bytes.
- `device_id`: 64 lowercase hex research device ID.
- `batch_id`: UUID.
- `rule_version`, `rule_hash`, `created_at_wall_millis`.

## TLS Threat Model

This phase does not use AES or end-to-end content encryption. Confidentiality depends on HTTPS/TLS 1.2+ between Android and the deployment endpoint. Local HTTP is allowed only for emulator or lab development.

This is acceptable for the prototype because redaction happens before upload, raw input text is dropped, password nodes are dropped, and the payload should not contain original PII. For production, add end-to-end encryption around the already-redacted JSON before compression or use an envelope format that preserves integrity and key rotation metadata without reintroducing raw identifiers.
