from __future__ import annotations

import base64
import hashlib
import json
import uuid
from copy import deepcopy
from typing import Any

import lz4.frame


DEVICE_ID = "a" * 64
RULE_HASH = "b" * 64


def sample_batch(
    *,
    device_id: str = DEVICE_ID,
    batch_id: str | None = None,
    collection_source: str = "BUILTIN_TASK",
    task_category: str | None = "C3",
    text_redacted: str = "<EMAIL>",
) -> dict[str, Any]:
    batch_uuid = batch_id or str(uuid.uuid4())
    task_meta = {
        "C0": ("静置计时", "持机静止"),
        "C1": ("研究协议阅读", "静态阅读"),
        "C2": ("研究咨询流", "单指滑动信息流"),
        "C3": ("段落抄写", "文本输入"),
        "C4": ("模拟手机设置", "多控件操作"),
        "C5": ("倾斜迷宫", "主动倾斜操作"),
        "C6": ("手腕转动", "显式转腕挑战"),
    }
    task_name, intuition = task_meta.get(task_category or "", (None, None))
    task_fields = {
        "task_sequence": int(task_category[1:]) if collection_source == "BUILTIN_TASK" and task_category else None,
        "task_id": task_category if collection_source == "BUILTIN_TASK" else None,
        "task_name": task_name if collection_source == "BUILTIN_TASK" else None,
        "task_intuitive_description": intuition if collection_source == "BUILTIN_TASK" else None,
        "task_category": task_category if collection_source == "BUILTIN_TASK" else None,
        "task_session_id": str(uuid.uuid4()) if collection_source == "BUILTIN_TASK" else None,
        "task_started_at_wall_millis": 1710000000000 if collection_source == "BUILTIN_TASK" else None,
        "task_elapsed_seconds_at_batch_end": 5 if collection_source == "BUILTIN_TASK" else None,
    }
    return {
        "batch_id": batch_uuid,
        "device_id": device_id,
        "session_id": task_fields["task_session_id"],
        "record_type": "collection",
        "collection_source": collection_source,
        "app_package_name": "com.contextauth",
        "sampling_rate_hz": 100,
        "batch_duration_seconds": 5,
        **task_fields,
        "app_version": "1.0.0",
        "rule_version": "1",
        "rule_hash": RULE_HASH,
        "consent_version": "1",
        "started_at_wall_millis": 1710000000000,
        "ended_at_wall_millis": 1710000005000,
        "base_elapsed_nanos": 123456789,
        "sensor_samples": [
            {
                "sensor_type": "ACCELEROMETER",
                "timestamp_elapsed_nanos": 123456790,
                "wall_time_estimated_millis": 1710000000001,
                "x": 0.1,
                "y": 0.2,
                "z": 9.8,
                "accuracy": 3,
            }
        ],
        "context_events": [
            {
                "event_id": str(uuid.uuid4()),
                "event_type": "TYPE_WINDOW_CONTENT_CHANGED",
                "event_time_wall_millis": 1710000000123,
                "package_name_hash": "c" * 64,
                "window_title_redacted": "<DROPPED>",
                "root_nodes": [
                    {
                        "node_id": "node-1",
                        "package_name_hash": "c" * 64,
                        "class_name": "android.widget.TextView",
                        "clickable": False,
                        "editable": False,
                        "scrollable": False,
                        "password": False,
                        "child_count": 0,
                        "text_redacted": text_redacted,
                        "content_desc_redacted": None,
                        "actions_summary": [],
                        "depth": 0,
                    }
                ],
                "redaction_summary": {
                    "dropped_password_nodes": 0,
                    "dropped_editable_texts": 0,
                    "replaced_email": 1,
                    "replaced_phone": 0,
                    "replaced_url": 0,
                    "replaced_number": 0,
                    "replaced_card": 0,
                    "replaced_id_number": 0,
                },
            }
        ],
        "context_features": [
            {
                "feature_id": str(uuid.uuid4()),
                "event_id": str(uuid.uuid4()),
                "computed_at_wall_millis": 1710000000200,
                "collection_source": collection_source,
                "task_sequence": task_fields["task_sequence"],
                "task_id": task_fields["task_id"],
                "task_name": task_fields["task_name"],
                "task_intuitive_description": task_fields["task_intuitive_description"],
                "task_category": task_category if collection_source == "BUILTIN_TASK" else None,
                "task_session_id": task_fields["task_session_id"],
                "keyboard_visible_estimated": False,
                "editable_count": 0,
                "scrollable_count": 0,
                "clickable_count": 1,
                "password_node_seen": False,
                "media_like_score": 0.0,
                "list_like_score": 0.0,
                "form_like_score": 0.0,
                "game_like_score": 0.0,
                "node_class_histogram": {"TextView": 1},
                "event_type": "TYPE_WINDOW_CONTENT_CHANGED",
                "coarse_orientation": "portrait",
                "estimated_context_category": task_category if collection_source == "BUILTIN_TASK" else "UNKNOWN",
            }
        ],
        "skip_events": [],
        "diagnostics": {
            "sensor_sample_count": 1,
            "context_event_count": 1,
            "redaction_applied": True,
            "compression": "lz4_frame",
            "encryption": "none",
            "sampling_rate_hz": 100,
        },
    }


def envelope_for(batch: dict[str, Any]) -> dict[str, Any]:
    compressed = lz4.frame.compress(json.dumps(batch, ensure_ascii=False, sort_keys=True).encode("utf-8"))
    return {
        "algorithm": "LZ4_FRAME+JSON",
        "payload_base64": base64.b64encode(compressed).decode("ascii"),
        "payload_sha256_hex": hashlib.sha256(compressed).hexdigest(),
        "device_id": batch["device_id"],
        "batch_id": batch["batch_id"],
        "rule_version": batch["rule_version"],
        "rule_hash": batch["rule_hash"],
        "created_at_wall_millis": batch["started_at_wall_millis"],
    }


def clone(value: dict[str, Any]) -> dict[str, Any]:
    return deepcopy(value)
