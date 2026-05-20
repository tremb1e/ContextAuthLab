from __future__ import annotations

import importlib
import os
import stat
from pathlib import Path


def test_server_study_salt_persistence(tmp_path, monkeypatch) -> None:
    monkeypatch.delenv("SERVER_STUDY_SALT", raising=False)
    monkeypatch.setenv("SERVER_DATA_DIR", str(tmp_path / "paper"))

    import app.config as config

    importlib.reload(config)
    first = config.get_server_study_salt(config.SETTINGS)
    second = config.get_server_study_salt(config.SETTINGS)
    assert first == second
    assert (tmp_path / "paper" / "server_study_salt.txt").exists()


def test_server_study_salt_file_permissions(tmp_path, monkeypatch) -> None:
    monkeypatch.delenv("SERVER_STUDY_SALT", raising=False)
    monkeypatch.setenv("SERVER_DATA_DIR", str(tmp_path / "paper"))

    import app.config as config

    importlib.reload(config)
    config.get_server_study_salt(config.SETTINGS)
    mode = (tmp_path / "paper" / "server_study_salt.txt").stat().st_mode
    assert stat.S_IMODE(mode) == 0o600


def test_time_sync_ntp_servers_can_be_overridden(monkeypatch) -> None:
    monkeypatch.setenv("TIME_SYNC_REGION", "LAB")
    monkeypatch.setenv("TIME_SYNC_NTP_SERVERS", "time1.example.test, time2.example.test")
    monkeypatch.setenv("TIME_SYNC_MAX_ACCEPTABLE_RTT_MILLIS", "1500")

    import app.config as config

    importlib.reload(config)
    assert config.SETTINGS.time_sync_region == "LAB"
    assert config.SETTINGS.time_sync_ntp_servers == ("time1.example.test", "time2.example.test")
    assert config.SETTINGS.time_sync_max_acceptable_rtt_millis == 1500


def test_time_sync_rtt_limit_must_be_positive(monkeypatch) -> None:
    monkeypatch.setenv("TIME_SYNC_MAX_ACCEPTABLE_RTT_MILLIS", "0")

    import app.config as config

    try:
        importlib.reload(config)
    except ValueError as exc:
        assert str(exc) == "TIME_SYNC_MAX_ACCEPTABLE_RTT_MILLIS_must_be_positive"
    else:
        raise AssertionError("expected TIME_SYNC_MAX_ACCEPTABLE_RTT_MILLIS validation error")
    finally:
        monkeypatch.delenv("TIME_SYNC_MAX_ACCEPTABLE_RTT_MILLIS", raising=False)
        importlib.reload(config)
