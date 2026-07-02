#!/usr/bin/env python3
"""Log selected Codex hook events as local JSONL records.

The log target is intentionally outside the repository so prompt/response logs are not committed.
"""

from __future__ import annotations

import json
import os
import pathlib
import re
import sys
from datetime import datetime, timezone
from typing import Any

REDACTION_PATTERNS = [
    # Common bearer/API token shapes.
    (re.compile(r"(?i)(bearer\s+)[A-Za-z0-9._\-]+"), r"\1<REDACTED>"),
    (re.compile(r"(?i)(api[_-]?key\s*[:=]\s*)[^\s,;]+"), r"\1<REDACTED>"),
    (re.compile(r"(?i)(token\s*[:=]\s*)[^\s,;]+"), r"\1<REDACTED>"),
    (re.compile(r"(?i)(password\s*[:=]\s*)[^\s,;]+"), r"\1<REDACTED>"),
    (re.compile(r"(?i)(secret\s*[:=]\s*)[^\s,;]+"), r"\1<REDACTED>"),
]


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def redact(value: Any) -> Any:
    if isinstance(value, str):
        result = value
        for pattern, replacement in REDACTION_PATTERNS:
            result = pattern.sub(replacement, result)
        return result
    if isinstance(value, list):
        return [redact(item) for item in value]
    if isinstance(value, dict):
        return {key: redact(item) for key, item in value.items()}
    return value


def resolve_log_dir(event: dict[str, Any]) -> pathlib.Path:
    configured = os.environ.get("CODEX_AUDIT_LOG_DIR")
    if configured:
        return pathlib.Path(configured).expanduser()

    cwd = event.get("cwd") or os.getcwd()
    project_name = pathlib.Path(cwd).resolve().name if cwd else "unknown-project"
    return pathlib.Path.home() / ".codex" / project_name / "logs"


def write_jsonl(log_dir: pathlib.Path, record: dict[str, Any]) -> None:
    log_dir.mkdir(parents=True, exist_ok=True)
    log_file = log_dir / f"codex-audit-{datetime.now(timezone.utc).date().isoformat()}.jsonl"
    with log_file.open("a", encoding="utf-8") as file:
        file.write(json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n")


def main() -> int:
    hook_event_name = sys.argv[1] if len(sys.argv) > 1 else "unknown"

    try:
        event = json.load(sys.stdin)
    except json.JSONDecodeError as exc:
        # Stop hooks must write JSON to stdout on success. On malformed input, fail closed but non-fatally.
        print(json.dumps({"continue": True, "suppressOutput": True}))
        print(f"Failed to parse Codex hook input: {exc}", file=sys.stderr)
        return 0

    record = {
        "timestamp": utc_now(),
        "hook_event_name": hook_event_name,
        "session_id": event.get("session_id"),
        "turn_id": event.get("turn_id"),
        "cwd": event.get("cwd"),
        "model": event.get("model"),
        "permission_mode": event.get("permission_mode"),
        "transcript_path": event.get("transcript_path"),
        "prompt": event.get("prompt") if hook_event_name == "UserPromptSubmit" else None,
        "last_assistant_message": event.get("last_assistant_message") if hook_event_name == "Stop" else None,
    }

    write_jsonl(resolve_log_dir(event), redact(record))

    # JSON is valid for both UserPromptSubmit and Stop. Stop requires JSON on stdout.
    print(json.dumps({"continue": True, "suppressOutput": True}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
