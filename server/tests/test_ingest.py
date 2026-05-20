from __future__ import annotations

import base64
import hashlib
import json
from pathlib import Path

import lz4.frame
from prometheus_client.parser import text_string_to_metric_families

from helpers import DEVICE_ID, clone, envelope_for, sample_batch


def _data_dir(server_client) -> Path:
    return server_client.app.state.test_data_dir


def _log_dir(server_client) -> Path:
    return server_client.app.state.test_log_dir


def test_ingest_valid_envelope_stores_batch_and_meta(server_client) -> None:
    batch = sample_batch()
    response = server_client.post("/api/v1/ingest", json=envelope_for(batch))
    assert response.status_code == 200
    assert response.json()["stored"] is True

    data_dir = _data_dir(server_client)
    batch_path = data_dir / "devices" / DEVICE_ID / "2024-03-09" / f"{batch['batch_id']}.json"
    meta_path = data_dir / "devices" / DEVICE_ID / "2024-03-09" / f"{batch['batch_id']}.meta.json"
    assert batch_path.exists()
    assert meta_path.exists()
    stored = json.loads(batch_path.read_text(encoding="utf-8"))
    assert stored["task_category"] == "C3"
    assert stored["task_id"] == "C3"
    assert stored["task_intuitive_description"] == "文本输入"
    meta = json.loads(meta_path.read_text(encoding="utf-8"))
    assert meta["compressed_payload_omitted"] is True
    assert "payload_base64" not in json.dumps(meta)


def test_by_category_index_created(server_client) -> None:
    batch = sample_batch(task_category="C4")
    response = server_client.post("/api/v1/ingest", json=envelope_for(batch))
    assert response.status_code == 200
    link = _data_dir(server_client) / "devices" / DEVICE_ID / "by_category" / "C4" / "2024-03-09" / f"{batch['batch_id']}.json"
    assert link.exists()
    if link.is_symlink():
        assert link.resolve().exists()


def test_ingest_rejects_bad_device_id(server_client) -> None:
    batch = sample_batch(device_id="A" * 64)
    env = envelope_for(batch)
    response = server_client.post("/api/v1/ingest", json=env)
    assert response.status_code == 400


def test_ingest_rejects_path_traversal_device_id(server_client) -> None:
    batch = sample_batch(device_id="../" + "a" * 61)
    env = envelope_for(batch)
    response = server_client.post("/api/v1/ingest", json=env)
    assert response.status_code == 400


def test_ingest_rejects_bad_uuid(server_client) -> None:
    batch = sample_batch()
    env = envelope_for(batch)
    env["batch_id"] = "not-a-uuid"
    response = server_client.post("/api/v1/ingest", json=env)
    assert response.status_code == 400


def test_ingest_rejects_bad_algorithm(server_client) -> None:
    batch = sample_batch()
    env = envelope_for(batch)
    env["algorithm"] = "AES-GCM"
    response = server_client.post("/api/v1/ingest", json=env)
    assert response.status_code == 400


def test_ingest_rejects_payload_hash_mismatch(server_client) -> None:
    batch = sample_batch()
    env = envelope_for(batch)
    env["payload_sha256_hex"] = "0" * 64
    response = server_client.post("/api/v1/ingest", json=env)
    assert response.status_code == 400
    assert response.json()["detail"] == "payload_hash_mismatch"


def test_ingest_rejects_corrupted_lz4_payload(server_client) -> None:
    batch = sample_batch()
    env = envelope_for(batch)
    compressed = bytearray(base64.b64decode(env["payload_base64"]))
    compressed[-1] ^= 0x01
    env["payload_base64"] = base64.b64encode(bytes(compressed)).decode("ascii")
    env["payload_sha256_hex"] = hashlib.sha256(bytes(compressed)).hexdigest()
    response = server_client.post("/api/v1/ingest", json=env)
    assert response.status_code == 400
    assert response.json()["detail"] == "corrupted_lz4_payload"


def test_lz4_roundtrip() -> None:
    payload = b'{"hello":"world"}'
    compressed = lz4.frame.compress(payload)
    assert lz4.frame.decompress(compressed) == payload


def test_quarantine_when_unredacted_email_detected(server_client) -> None:
    batch = sample_batch(text_redacted="alice@example.com")
    response = server_client.post("/api/v1/ingest", json=envelope_for(batch))
    assert response.status_code == 400
    assert response.json()["detail"] == "unredacted_email"
    quarantine = _data_dir(server_client) / "quarantine" / DEVICE_ID / "2024-03-09" / f"{batch['batch_id']}.json"
    assert quarantine.exists()


def test_quarantine_when_card_number_detected(server_client) -> None:
    batch = sample_batch(text_redacted="4111 1111 1111 1111")
    response = server_client.post("/api/v1/ingest", json=envelope_for(batch))
    assert response.status_code == 400
    assert response.json()["detail"] == "unredacted_card"


def test_quarantine_when_raw_accessibility_text_field_detected(server_client) -> None:
    batch = sample_batch()
    batch["context_events"][0]["root_nodes"][0]["text"] = "visible UI label"
    response = server_client.post("/api/v1/ingest", json=envelope_for(batch))
    assert response.status_code == 400
    assert response.json()["detail"] == "raw_accessibility_field:text"
    quarantine = _data_dir(server_client) / "quarantine" / DEVICE_ID / "2024-03-09" / f"{batch['batch_id']}.json"
    assert quarantine.exists()
    assert "visible UI label" not in quarantine.read_text(encoding="utf-8")


def test_schema_rejects_batches_without_redaction_applied(server_client) -> None:
    batch = sample_batch()
    batch["diagnostics"]["redaction_applied"] = False
    response = server_client.post("/api/v1/ingest", json=envelope_for(batch))
    assert response.status_code == 400
    assert response.json()["detail"] == "schema_validation_failed"


def test_task_category_required_when_builtin(server_client) -> None:
    batch = sample_batch()
    batch["task_category"] = None
    batch["context_features"][0]["task_category"] = None
    response = server_client.post("/api/v1/ingest", json=envelope_for(batch))
    assert response.status_code == 400
    assert response.json()["detail"] == "schema_validation_failed"


def test_task_category_must_be_null_when_third_party(server_client) -> None:
    batch = sample_batch(collection_source="THIRD_PARTY_APP", task_category=None)
    batch["task_category"] = "C3"
    batch["context_features"][0]["task_category"] = "C3"
    response = server_client.post("/api/v1/ingest", json=envelope_for(batch))
    assert response.status_code == 400
    assert response.json()["detail"] == "schema_validation_failed"


def test_envelope_batch_device_id_mismatch(server_client) -> None:
    batch = sample_batch()
    env = envelope_for(batch)
    env["device_id"] = "c" * 64
    response = server_client.post("/api/v1/ingest", json=env)
    assert response.status_code == 400
    assert response.json()["detail"] == "envelope_batch_device_id_mismatch"


def test_envelope_batch_id_mismatch(server_client) -> None:
    batch = sample_batch()
    env = envelope_for(batch)
    env["batch_id"] = "11111111-1111-4111-8111-111111111111"
    response = server_client.post("/api/v1/ingest", json=env)
    assert response.status_code == 400
    assert response.json()["detail"] == "envelope_batch_id_mismatch"


def test_error_log_no_plain_payload(server_client) -> None:
    batch = sample_batch()
    env = envelope_for(batch)
    env["payload_sha256_hex"] = "0" * 64
    server_client.post("/api/v1/ingest", json=env)
    errors = (_data_dir(server_client) / "index" / "errors.jsonl").read_text(encoding="utf-8")
    assert env["payload_base64"] not in errors
    assert "alice@example.com" not in errors


def test_structured_log_for_each_ingest(server_client) -> None:
    batch = sample_batch()
    response = server_client.post("/api/v1/ingest", json=envelope_for(batch))
    assert response.status_code == 200
    log_text = (_log_dir(server_client) / "server.jsonl").read_text(encoding="utf-8")
    events = [json.loads(line)["event"] for line in log_text.splitlines() if line.strip()]
    assert "ingest_received" in events
    assert "ingest_decompressed" in events
    assert "ingest_stored" in events


def test_structured_log_no_sensitive_fields(server_client) -> None:
    batch = sample_batch()
    env = envelope_for(batch)
    server_client.post("/api/v1/ingest", json=env)
    log_text = (_log_dir(server_client) / "server.jsonl").read_text(encoding="utf-8")
    assert "ENCRYPTION_PASSWORD" not in log_text
    assert env["payload_base64"] not in log_text
    assert DEVICE_ID not in log_text
    assert DEVICE_ID[:8] in log_text


def test_metrics_endpoint_returns_prometheus_format(server_client) -> None:
    response = server_client.get("/metrics")
    assert response.status_code == 200
    families = list(text_string_to_metric_families(response.text))
    names = {family.name for family in families}
    assert "ingest" in names
    assert "server_up" in names
    assert "ingest_total" in response.text


def test_metrics_counters_increment(server_client) -> None:
    for _ in range(2):
        batch = sample_batch()
        assert server_client.post("/api/v1/ingest", json=envelope_for(batch)).status_code == 200
    metrics = server_client.get("/metrics").text
    assert 'ingest_total{result="ok"}' in metrics


def test_disk_space_threshold_reject(server_client, monkeypatch) -> None:
    import app.main as main

    monkeypatch.setattr(main.STORE, "assert_space_available", lambda: (_ for _ in ()).throw(OSError("disk_space_below_threshold")))
    batch = sample_batch()
    response = server_client.post("/api/v1/ingest", json=envelope_for(batch))
    assert response.status_code == 507
