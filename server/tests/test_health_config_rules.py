from __future__ import annotations

from pathlib import Path


def test_health_endpoint_returns_ok(server_client) -> None:
    response = server_client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_config_endpoint_returns_stable_values_across_calls(server_client) -> None:
    first = server_client.get("/api/v1/config").json()
    second = server_client.get("/api/v1/config").json()
    assert first["serverStudySalt"] == "Continuous_Authentication"
    assert first["serverStudySalt"] == second["serverStudySalt"]
    assert first["rulesVersion"] == "1"
    assert isinstance(first["serverTimeMillis"], int)
    assert first["timeSync"] == second["timeSync"]
    assert first["timeSync"]["method"] == "HTTP_MIDPOINT"
    assert first["timeSync"]["serverTimeField"] == "serverTimeMillis"
    assert first["timeSync"]["region"] == "CN"
    assert first["timeSync"]["maxAcceptableRttMillis"] > 0
    assert "ntp.aliyun.com" in first["timeSync"]["recommendedNtpServers"]
    assert "ntp.tencent.com" in first["timeSync"]["recommendedNtpServers"]
    assert "0.cn.pool.ntp.org" in first["timeSync"]["recommendedNtpServers"]


def test_rules_returns_hash_and_redaction_policy(server_client) -> None:
    from app.rules import rule_hash

    response = server_client.get("/api/v1/rules")
    assert response.status_code == 200
    payload = response.json()
    assert set(payload) == {
        "version",
        "updated_at",
        "rules",
        "package_blocklist",
        "max_text_length",
        "default_text_action",
        "rule_hash",
    }
    assert payload["version"] == "1"
    assert payload["rules"] == []
    assert payload["package_blocklist"] == []
    assert payload["max_text_length"] == 128
    assert payload["default_text_action"] == "REDACT"
    assert len(payload["rule_hash"]) == 64
    hash_payload = {key: value for key, value in payload.items() if key != "rule_hash"}
    assert payload["rule_hash"] == rule_hash(hash_payload)


def test_openapi_documents_config_and_rules_models(server_client) -> None:
    response = server_client.get("/openapi.json")
    assert response.status_code == 200
    schemas = response.json()["components"]["schemas"]
    assert "ConfigResponse" in schemas
    assert "TimeSyncConfig" in schemas
    assert "RulesResponse" in schemas
    assert "UiRedactionRule" in schemas


def test_no_dashboard_routes(server_client) -> None:
    assert server_client.get("/dashboard").status_code == 404
    assert server_client.get("/dashboard/devices").status_code == 404


def test_no_templates_or_static_frontend_required() -> None:
    server_root = Path(__file__).resolve().parents[1]
    assert not (server_root / "app" / "templates").exists()
    assert not (server_root / "app" / "static").exists()
