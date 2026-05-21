from __future__ import annotations

import hashlib
import json
import re
from typing import Any

from .config import SETTINGS
from .schemas import RulesResponse


DEFAULT_UI_REDACTION_RULES: list[dict[str, Any]] = [
    {
        "id": "email",
        "target": "text",
        "action": "REDACT",
        "pattern": r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b",
        "replacement": "<EMAIL>",
        "description": "Email addresses in visible UI text/content descriptions.",
    },
    {
        "id": "phone_cn",
        "target": "text",
        "action": "REDACT",
        "pattern": r"(?<!\d)(?:\+?86[-\s]?)?1[3-9]\d{9}(?!\d)",
        "replacement": "<PHONE>",
        "description": "Mainland China mobile phone numbers.",
    },
    {
        "id": "url",
        "target": "text",
        "action": "REDACT",
        "pattern": r"\b(?:https?://|www\.)[^\s<>\"']+",
        "replacement": "<URL>",
        "description": "HTTP(S) and www URLs.",
    },
    {
        "id": "id_number_cn",
        "target": "text",
        "action": "REDACT",
        "pattern": r"(?<![\w-])(?:\d{15}|\d{17}[\dXx])(?![\w-])",
        "replacement": "<ID_NUM>",
        "description": "Chinese resident ID-like numbers.",
    },
    {
        "id": "payment_card",
        "target": "text",
        "action": "REDACT",
        "pattern": r"(?<![\w-])(?:\d[ -]?){13,19}(?![\w-])",
        "replacement": "<CARD>",
        "description": "Payment-card-like digit groups.",
    },
    {
        "id": "opaque_token",
        "target": "text",
        "action": "REDACT",
        "pattern": r"\b(?:[A-Fa-f0-9]{24,}|[A-Za-z0-9+/=_-]{32,})\b",
        "replacement": "<TOKEN>",
        "description": "Long opaque identifiers, hashes, and token-like strings.",
    },
    {
        "id": "long_number",
        "target": "text",
        "action": "REDACT",
        "pattern": r"(?<!\d)\d{4,}(?!\d)",
        "replacement": "<NUM>",
        "description": "Long numeric strings that may be identifiers or codes.",
    },
]

DEFAULT_PACKAGE_BLOCKLIST = [
    "dialer",
    "contacts",
    "sms",
    "bank",
    "pay",
    "medical",
    "password",
    "signal",
    "telegram",
    "whatsapp",
    "wechat",
]


DEFAULT_RULES: dict[str, Any] = {
    "version": SETTINGS.rules_version,
    "updated_at": "2026-05-21T00:00:00Z",
    "rules": DEFAULT_UI_REDACTION_RULES,
    "package_blocklist": DEFAULT_PACKAGE_BLOCKLIST,
    "max_text_length": 128,
    "default_text_action": "REDACT",
}


EMAIL_RE = re.compile(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", re.IGNORECASE)
PHONE_RE = re.compile(r"(?<!\d)(?:\+?86[-\s]?)?1[3-9]\d{9}(?!\d)")
URL_RE = re.compile(r"\b(?:https?://|www\.)[^\s<>\"']+", re.IGNORECASE)
CARD_RE = re.compile(r"(?<![\w-])(?:\d[ -]?){13,19}(?![\w-])")
ID_NUM_RE = re.compile(r"(?<![\w-])(?:\d{15}|\d{17}[\dXx])(?![\w-])")
RAW_ACCESSIBILITY_FIELD_KEYS = {
    "content_desc",
    "contentdescription",
    "content_description",
    "packagename",
    "package_name",
    "rawtext",
    "raw_text",
    "resource_id",
    "text",
    "viewid",
    "view_id",
    "viewidresourcename",
    "view_id_resource_name",
    "windowtitle",
    "window_title",
}
UI_REDACTED_FIELD_KEYS = {
    "content_desc_redacted",
    "content_description_redacted",
    "text_redacted",
    "window_title_redacted",
}
PLACEHOLDER_ONLY_RE = re.compile(r"^(?:<[^<>\s]{2,64}>\s*)+$")


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


def find_unredacted_ui_text_field(value: Any) -> str | None:
    """Reject UI content fields that are marked redacted but still carry prose."""
    if isinstance(value, dict):
        for key, child in value.items():
            normalized_key = str(key).strip()
            lowered_key = normalized_key.lower()
            if lowered_key in UI_REDACTED_FIELD_KEYS and isinstance(child, str):
                if child and not PLACEHOLDER_ONLY_RE.fullmatch(child.strip()):
                    return f"unredacted_ui_text:{normalized_key}"
            found = find_unredacted_ui_text_field(child)
            if found:
                return found
    elif isinstance(value, list):
        for child in value:
            found = find_unredacted_ui_text_field(child)
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
