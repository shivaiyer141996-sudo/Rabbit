#!/usr/bin/env python3
"""Read-only functional checks against an isolated restored Rabbit stack."""

from __future__ import annotations

import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

API = "http://backend:8080/api/v1"
RUN_ID = os.environ.get("RABBIT_EVIDENCE_RUN_ID", "")
CONFIG_PATH = Path("/run/secrets/m5-3.env")

if not RUN_ID or not all(character.isalnum() or character in "._-" for character in RUN_ID):
    raise SystemExit("RABBIT_EVIDENCE_RUN_ID is missing or unsafe.")


def parse_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key.strip()] = value
    return values


config = parse_env(CONFIG_PATH)
checks: list[dict[str, str]] = []


def required(name: str) -> str:
    value = config.get(name, "")
    if not value or "REPLACE" in value:
        raise RuntimeError(f"{name} is missing from the protected M5.3 configuration.")
    return value


def record(passed: bool, name: str, detail: str) -> None:
    checks.append(
        {"status": "PASS" if passed else "FAIL", "check": name, "detail": detail}
    )


def request(
    path: str,
    *,
    method: str = "GET",
    token: str | None = None,
    payload: dict[str, Any] | None = None,
) -> tuple[int, dict[str, str], bytes]:
    headers = {"Accept": "application/json"}
    body = None
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if payload is not None:
        headers["Content-Type"] = "application/json"
        body = json.dumps(payload).encode("utf-8")
    try:
        url = path if path.startswith("http://") else f"{API}{path}"
        with urlopen(
            Request(url, data=body, headers=headers, method=method),
            timeout=20,
        ) as response:
            return (
                response.status,
                {key.lower(): value for key, value in response.headers.items()},
                response.read(),
            )
    except HTTPError as error:
        return (
            error.code,
            {key.lower(): value for key, value in error.headers.items()},
            error.read(),
        )
    except URLError as error:
        raise RuntimeError(f"Restored Rabbit API is unavailable: {error.reason}") from error


def json_body(body: bytes, description: str) -> Any:
    try:
        return json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise RuntimeError(f"{description} did not return valid JSON.") from error


def login(email_key: str, password_key: str, expected_role: str) -> str:
    email = required(email_key)
    if email.lower().endswith("@demo.rabbit.local"):
        raise RuntimeError("M5.3 recovery evidence cannot use a seeded demo identity.")
    status, _, body = request(
        "/auth/login",
        method="POST",
        payload={"email": email, "password": required(password_key)},
    )
    if status != 200:
        raise RuntimeError(f"Restored {expected_role} login returned HTTP {status}.")
    payload = json_body(body, f"{expected_role} login")
    if payload.get("requiresOrganisationSelection"):
        organisation_code = required("RABBIT_RECOVERY_ORGANISATION_CODE")
        choice = next(
            (
                item
                for item in payload.get("organisations", [])
                if item.get("code") == organisation_code
            ),
            None,
        )
        if choice is None:
            raise RuntimeError(f"Configured organisation is unavailable to {expected_role}.")
        status, _, body = request(
            "/auth/select-organisation",
            method="POST",
            payload={
                "selectionToken": payload.get("selectionToken"),
                "organisationId": choice.get("id"),
            },
        )
        if status != 200:
            raise RuntimeError(
                f"Restored {expected_role} organisation selection returned HTTP {status}."
            )
        payload = json_body(body, f"{expected_role} organisation selection")
    if payload.get("role") != expected_role or not payload.get("accessToken"):
        raise RuntimeError(f"Restored identity did not resolve to {expected_role}.")
    return str(payload["accessToken"])


def expect_json(path: str, token: str, name: str, expected_type: type) -> Any:
    status, _, body = request(path, token=token)
    payload = json_body(body, name) if status == 200 else None
    passed = status == 200 and isinstance(payload, expected_type)
    record(passed, name, f"HTTP {status}; restored response type is valid." if passed else f"HTTP {status}.")
    return payload


try:
    health_status, _, health_body = request(
        "http://backend:8080/actuator/health/readiness"
    )
    record(
        health_status == 200 and b'"status":"UP"' in health_body,
        "Restored API readiness",
        f"HTTP {health_status}.",
    )

    admin_token = login(
        "RABBIT_RECOVERY_ADMIN_EMAIL", "RABBIT_RECOVERY_ADMIN_PASSWORD", "ORG_ADMIN"
    )
    admin_me = expect_json("/auth/me", admin_token, "Restored Admin login", dict)
    record(
        isinstance(admin_me, dict) and admin_me.get("role") == "ORG_ADMIN",
        "Restored Admin role",
        "Organisation Admin authority is intact.",
    )

    assessments = expect_json("/assessments", admin_token, "Restored assessments", list)
    assessment_id = assessments[0].get("id") if assessments else None
    record(bool(assessment_id), "Restored assessment data", "At least one assessment is available.")
    if assessment_id:
        expect_json(
            f"/assessments/{assessment_id}",
            admin_token,
            "Restored assessment detail",
            dict,
        )

    audits = expect_json("/audit-events", admin_token, "Restored audit history", list)
    record(bool(audits), "Restored audit data", "At least one immutable audit event is available.")
    audit_status, _, audit_csv = request("/audit-events/export", token=admin_token)
    record(
        audit_status == 200 and b"module" in audit_csv.lower(),
        "Restored audit export",
        f"HTTP {audit_status}; CSV bytes={len(audit_csv)}.",
    )

    pdf_status, pdf_headers, pdf_body = request("/reports/teacher/export.pdf", token=admin_token)
    record(
        pdf_status == 200
        and pdf_body.startswith(b"%PDF")
        and "no-store" in pdf_headers.get("cache-control", "").lower(),
        "Restored PDF export",
        f"HTTP {pdf_status}; PDF bytes={len(pdf_body)}; no-store required.",
    )
    xlsx_status, xlsx_headers, xlsx_body = request(
        "/reports/teacher/export.xlsx", token=admin_token
    )
    record(
        xlsx_status == 200
        and xlsx_body.startswith(b"PK")
        and "no-store" in xlsx_headers.get("cache-control", "").lower(),
        "Restored Excel export",
        f"HTTP {xlsx_status}; XLSX bytes={len(xlsx_body)}; no-store required.",
    )

    student_token = login(
        "RABBIT_RECOVERY_STUDENT_EMAIL",
        "RABBIT_RECOVERY_STUDENT_PASSWORD",
        "STUDENT",
    )
    expect_json("/auth/me", student_token, "Restored Student login", dict)
    expect_json("/dashboard", student_token, "Restored Student dashboard", dict)
    expect_json("/student/assessments", student_token, "Restored assessment discovery", list)
    history = expect_json(
        "/student/attempts/history", student_token, "Restored result history", list
    )
    published = next(
        (
            item
            for item in history or []
            if item.get("publicationStatus") == "PUBLISHED" and item.get("attemptId")
        ),
        None,
    )
    record(
        published is not None,
        "Restored published result data",
        "Configured recovery Student has a published rehearsal result.",
    )
    if published:
        expect_json(
            f"/student/results/{published['attemptId']}",
            student_token,
            "Restored published result",
            dict,
        )
    expect_json(
        "/reports/students/me/analytics",
        student_token,
        "Restored Student analytics",
        dict,
    )

    try:
        with urlopen("http://minio:9000/minio/health/ready", timeout=10) as response:
            minio_status = response.status
    except (HTTPError, URLError):
        minio_status = 0
    record(
        minio_status == 200,
        "Restored MinIO readiness",
        f"HTTP {minio_status}; asset counts are reconciled separately.",
    )
except Exception as error:  # Never print or persist credential values.
    record(False, "Functional recovery execution", str(error))

failures = sum(item["status"] == "FAIL" for item in checks)
result = {
    "evidenceType": "Rabbit M5.3 isolated functional recovery",
    "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"),
    "liveEnvironmentModified": False,
    "credentialsRecorded": False,
    "checks": checks,
    "failures": failures,
    "passed": failures == 0,
}
output = Path("/evidence") / RUN_ID / "functional-recovery.json"
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(f"Rabbit M5.3 functional recovery {'PASSED' if failures == 0 else 'FAILED'}.")
sys.exit(0 if failures == 0 else 1)
