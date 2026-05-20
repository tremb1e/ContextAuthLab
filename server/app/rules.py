from __future__ import annotations

import hashlib
import json
import re
from typing import Any

from .config import SETTINGS
from .schemas import RulesResponse


DEFAULT_RULES: dict[str, Any] = {
    "version": SETTINGS.rules_version,
    "updated_at": "2026-05-18T00:00:00Z",
    "rules": [],
    "package_blocklist": [],
    "max_text_length": 128,
    "default_text_action": "REDACT",
}


EMAIL_RE = re.compile(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", re.IGNORECASE)
PHONE_RE = re.compile(r"(?<!\d)(?:\+?86[-\s]?)?1[3-9]\d{9}(?!\d)")
URL_RE = re.compile(r"\b(?:https?://|www\.)[^\s<>\"]+", re.IGNORECASE)
CARD_RE = re.compile(r"(?<![\w-])(?:\d[ -]?){13,19}(?![\w-])")
ID_NUM_RE = re.compile(r"(?<![\w-])(?:\d{15}|\d{17}[\dXx])(?![\w-])")
RAW_ACCESSIBILITY_FIELD_KEYS = {
    "contentdescription",
    "content_description",
    "packagename",
    "package_name",
    "rawtext",
    "raw_text",
    "text",
    "viewid",
    "view_id",
    "windowtitle",
    "window_title",
}


def rule_hash(rules: dict[str, Any] | None = None) -> str:
    payload = json.dumps(rules or DEFAULT_RULES, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def rules_response() -> RulesResponse:
    payload = dict(DEFAULT_RULES)
    payload["rule_hash"] = rule_hash(payload)
    return RulesResponse.model_validate(payload)


def find_forbidden_raw_ui_field(value: Any) -> str | None:
    """Reject raw Accessibility/UI fields that must be hashed or redacted client-side."""
    if isinstance(value, dict):
        for key, child in value.items():
            normalized_key = str(key).strip()
            if normalized_key in RAW_ACCESSIBILITY_FIELD_KEYS:
                return f"raw_accessibility_field:{normalized_key}"
            lowered_key = normalized_key.lower()
            if lowered_key in RAW_ACCESSIBILITY_FIELD_KEYS:
                return f"raw_accessibility_field:{normalized_key}"
            found = find_forbidden_raw_ui_field(child)
            if found:
                return found
    elif isinstance(value, list):
        for child in value:
            found = find_forbidden_raw_ui_field(child)
            if found:
                return found
    return None


def find_unredacted_sensitive_text(value: Any) -> str | None:
    """Second-line server check after client-side redaction.

    It recursively scans JSON values and returns a coarse reason only.
    Raw text is never returned to callers or logs.
    """
    if isinstance(value, dict):
        for child in value.values():
            found = find_unredacted_sensitive_text(child)
            if found:
                return found
    elif isinstance(value, list):
        for child in value:
            found = find_unredacted_sensitive_text(child)
            if found:
                return found
    elif isinstance(value, str):
        if EMAIL_RE.search(value):
            return "unredacted_email"
        if URL_RE.search(value):
            return "unredacted_url"
        if ID_NUM_RE.search(value):
            return "unredacted_id_number"
        if CARD_RE.search(value):
            digits = re.sub(r"\D", "", CARD_RE.search(value).group(0))
            if len(digits) >= 13:
                return "unredacted_card"
        if PHONE_RE.search(value):
            return "unredacted_phone"
    return None
