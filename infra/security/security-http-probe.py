#!/usr/bin/env python3
"""Read-only HTTP security checks for the local M5.3 evidence run."""

from __future__ import annotations

import json
import os
import random
import sys
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

GATEWAY = "http://nginx"
API = "http://backend:8080/api/v1"
RUN_ID = os.environ.get("RABBIT_EVIDENCE_RUN_ID", "")

if not RUN_ID or not all(character.isalnum() or character in "._-" for character in RUN_ID):
    raise SystemExit("RABBIT_EVIDENCE_RUN_ID is missing or unsafe.")

checks: list[dict[str, str]] = []


def record(passed: bool, name: str, detail: str) -> None:
    checks.append(
        {"status": "PASS" if passed else "FAIL", "check": name, "detail": detail}
    )


def request(
    url: str,
    *,
    method: str = "GET",
    headers: dict[str, str] | None = None,
    payload: bytes | None = None,
) -> tuple[int, dict[str, str], bytes]:
    try:
        with urlopen(
            Request(url, data=payload, headers=headers or {}, method=method),
            timeout=15,
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
        raise RuntimeError(f"Local Rabbit endpoint is unavailable: {error.reason}") from error


try:
    readiness_status, _, readiness_body = request(
        f"{GATEWAY}/api/actuator/health/readiness"
    )
    readiness_ok = readiness_status == 200 and b'"status":"UP"' in readiness_body
    record(readiness_ok, "Gateway readiness", "Local gateway and API report ready.")

    login_status, login_headers, _ = request(f"{GATEWAY}/login")
    record(login_status == 200, "Login route", f"HTTP {login_status} through local Nginx.")
    required_headers = {
        "x-content-type-options": "nosniff",
        "x-frame-options": "DENY",
        "referrer-policy": "strict-origin-when-cross-origin",
        "permissions-policy": "camera=()",
        "content-security-policy": "frame-ancestors 'none'",
    }
    for header, expected_fragment in required_headers.items():
        actual = login_headers.get(header, "")
        record(
            expected_fragment.lower() in actual.lower(),
            f"Security header: {header}",
            "Required policy is present." if actual else "Header is missing.",
        )

    protected_status, _, _ = request(f"{API}/dashboard")
    record(
        protected_status in {401, 403},
        "Protected API rejects anonymous access",
        f"HTTP {protected_status} without a bearer token.",
    )

    cors_status, cors_headers, _ = request(
        f"{API}/dashboard",
        method="OPTIONS",
        headers={
            "Origin": "https://attacker.invalid",
            "Access-Control-Request-Method": "GET",
        },
    )
    record(
        "access-control-allow-origin" not in cors_headers,
        "Unapproved CORS origin",
        f"HTTP {cors_status}; no allow-origin header returned.",
    )

    trace_status, _, _ = request(f"{API}/dashboard", method="TRACE")
    record(
        trace_status not in range(200, 300),
        "TRACE method rejected",
        f"HTTP {trace_status}.",
    )

    synthetic_source = f"198.18.{random.randint(1, 254)}.{random.randint(1, 254)}"
    rate_statuses: list[int] = []
    for sequence in range(1, 26):
        body = json.dumps(
            {
                "email": f"m5-3-rate-{RUN_ID}-{sequence}@invalid.local",
                "password": "Invalid-local-probe-only-credential",
            }
        ).encode("utf-8")
        status, headers, _ = request(
            f"{API}/auth/login",
            method="POST",
            headers={
                "Content-Type": "application/json",
                "X-Forwarded-For": synthetic_source,
            },
            payload=body,
        )
        rate_statuses.append(status)
        if status == 429:
            record(
                "retry-after" in headers and "x-ratelimit-limit" in headers,
                "Rate-limit response headers",
                "Retry-After and X-RateLimit-Limit are present.",
            )
            break
    record(
        429 in rate_statuses,
        "Anonymous login rate limit",
        f"Rate limiting engaged after {len(rate_statuses)} local probe requests.",
    )
except Exception as error:  # Evidence must record a controlled failure.
    record(False, "Security probe execution", str(error))

failures = sum(item["status"] == "FAIL" for item in checks)
result = {
    "evidenceType": "Rabbit M5.3 local HTTP security review",
    "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"),
    "externalNetworkUsed": False,
    "institutionContentModified": False,
    "operationalSideEffects": "Synthetic failed-login counters may exist for up to the configured lock window.",
    "checks": checks,
    "failures": failures,
    "passed": failures == 0,
}
output = Path("/evidence") / RUN_ID / "security-http.json"
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(f"Rabbit M5.3 HTTP security {'PASSED' if failures == 0 else 'FAILED'}.")
sys.exit(0 if failures == 0 else 1)
