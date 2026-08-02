#!/usr/bin/env python3
"""Prepare and finalize local-only Rabbit M5.5 approval evidence."""

from __future__ import annotations

import argparse
import csv
import hashlib
import ipaddress
import json
import os
import re
import shutil
import subprocess
import sys
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen

OUTCOMES = {"GO", "CONDITIONAL_RETEST", "NO_GO"}
SEVERITIES = {"S1", "S2", "S3", "S4"}
STATUSES = {"OPEN", "CLOSED"}
DEMO_DOMAIN = "@demo.rabbit.local"
LOCAL_REFERENCE = re.compile(r"^urn:rabbit-evidence:[A-Za-z0-9][A-Za-z0-9._:-]{7,900}$")


class Evidence:
    def __init__(self) -> None:
        self.checks: list[dict[str, str]] = []

    def record(self, passed: bool, check: str, detail: str) -> None:
        self.checks.append(
            {"status": "PASS" if passed else "FAIL", "check": check, "detail": detail}
        )

    @property
    def failures(self) -> int:
        return sum(item["status"] == "FAIL" for item in self.checks)


def parse_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key.strip()] = value
    return values


def required(config: dict[str, str], name: str) -> str:
    value = config.get(name, "").strip()
    upper = value.upper()
    if not value or upper == "REPLACE_ME" or upper.startswith("REPLACE_WITH_"):
        raise ValueError(f"{name} is missing or still contains a placeholder.")
    return value


def confirmation(config: dict[str, str], name: str) -> bool:
    value = required(config, name).lower()
    if value not in {"yes", "no"}:
        raise ValueError(f"{name} must be yes or no.")
    return value == "yes"


def parse_timestamp(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("Timestamp must include a timezone.")
    return parsed.astimezone(timezone.utc)


def safe_base_url(value: str) -> str:
    parsed = urlparse(value)
    if parsed.scheme not in {"http", "https"}:
        raise ValueError("PILOT_M5_5_BASE_URL must use HTTP or HTTPS.")
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("PILOT_M5_5_BASE_URL cannot contain credentials, query, or fragment.")
    if parsed.path not in {"", "/"}:
        raise ValueError("PILOT_M5_5_BASE_URL must not contain a path.")
    host = (parsed.hostname or "").lower()
    if host != "localhost":
        try:
            address = ipaddress.ip_address(host)
        except ValueError as error:
            raise ValueError(
                "Use localhost or an explicit loopback/private IP for M5.5."
            ) from error
        if not (address.is_loopback or address.is_private):
            raise ValueError("M5.5 refuses a public network target.")
    return value.rstrip("/")


class RabbitApi:
    def __init__(self, base_url: str) -> None:
        self.root = f"{safe_base_url(base_url)}/gateway/backend"
        self.token: str | None = None

    def request(
        self,
        path: str,
        *,
        method: str = "GET",
        payload: dict[str, Any] | None = None,
        query: dict[str, str] | None = None,
    ) -> tuple[int, bytes]:
        url = f"{self.root}{path}"
        if query:
            url = f"{url}?{urlencode(query)}"
        headers = {"Accept": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        body = None
        if payload is not None:
            headers["Content-Type"] = "application/json"
            body = json.dumps(payload).encode("utf-8")
        try:
            with urlopen(
                Request(url, data=body, headers=headers, method=method), timeout=30
            ) as response:
                return response.status, response.read()
        except HTTPError as error:
            return error.code, error.read()
        except URLError as error:
            raise RuntimeError(f"Local Rabbit API is unavailable: {error.reason}") from error

    def json(
        self,
        path: str,
        *,
        method: str = "GET",
        payload: dict[str, Any] | None = None,
        query: dict[str, str] | None = None,
    ) -> Any:
        status, body = self.request(path, method=method, payload=payload, query=query)
        if status != 200:
            raise RuntimeError(f"Rabbit API {path} returned HTTP {status}.")
        try:
            return json.loads(body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise RuntimeError(f"Rabbit API {path} did not return valid JSON.") from error

    def login(self, email: str, password: str, organisation_code: str) -> dict[str, Any]:
        if email.lower().endswith(DEMO_DOMAIN):
            raise ValueError("M5.5 cannot use a seeded demo administrator.")
        response = self.json(
            "/auth/login", method="POST", payload={"email": email, "password": password}
        )
        if response.get("requiresOrganisationSelection"):
            choice = next(
                (
                    item
                    for item in response.get("organisations", [])
                    if item.get("code") == organisation_code
                ),
                None,
            )
            if choice is None:
                raise RuntimeError("Configured organisation is unavailable to the M5.5 Admin.")
            response = self.json(
                "/auth/select-organisation",
                method="POST",
                payload={
                    "selectionToken": response.get("selectionToken"),
                    "organisationId": choice.get("id"),
                },
            )
        if response.get("role") != "ORG_ADMIN" or not response.get("accessToken"):
            raise RuntimeError("M5.5 evidence requires an active Organisation Admin.")
        self.token = str(response["accessToken"])
        me = self.json("/auth/me")
        if me.get("organisationCode") != organisation_code:
            raise RuntimeError("Authenticated organisation does not match the protected config.")
        return me


def current_commit(repo_root: Path) -> str:
    return subprocess.run(
        ["git", "-C", str(repo_root), "rev-parse", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def resolve_path(config_path: Path, value: str) -> Path:
    path = Path(value).expanduser()
    if not path.is_absolute():
        path = config_path.parent / path
    return path.resolve()


def canonical_hash(value: Any) -> str:
    raw = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def read_checksum_file(bundle: Path) -> dict[str, str]:
    checksum_path = bundle / "SHA256SUMS"
    if not checksum_path.is_file():
        raise ValueError(f"Evidence bundle has no SHA256SUMS: {bundle}")
    expected: dict[str, str] = {}
    for number, raw in enumerate(checksum_path.read_text(encoding="utf-8").splitlines(), 1):
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9._/-]+)", raw)
        if not match:
            raise ValueError(f"Malformed SHA256SUMS row {number}.")
        relative = Path(match.group(2))
        if relative.is_absolute() or ".." in relative.parts:
            raise ValueError("Evidence checksum contains an unsafe path.")
        target = (bundle / relative).resolve()
        if bundle.resolve() not in target.parents or not target.is_file():
            raise ValueError(f"Evidence file is missing or unsafe: {relative}")
        actual = hashlib.sha256(target.read_bytes()).hexdigest()
        if actual != match.group(1):
            raise ValueError(f"Evidence checksum mismatch: {relative}")
        expected[relative.as_posix()] = actual
    return expected


def validate_bundled_json(path: Path) -> dict[str, Any]:
    checksums = read_checksum_file(path.parent)
    if path.name not in checksums:
        raise ValueError(f"{path.name} is not protected by its bundle checksum.")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError(f"{path.name} is not valid JSON.") from error


def read_csv(path: Path, columns: set[str]) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        if not reader.fieldnames or not columns.issubset(reader.fieldnames):
            raise ValueError(f"{path.name} is missing required columns: {sorted(columns)}")
        return [
            {key: (value or "").strip() for key, value in row.items() if key is not None}
            for row in reader
            if any((value or "").strip() for value in row.values())
        ]


def incident_summary(path: Path) -> dict[str, Any]:
    rows = read_csv(
        path,
        {
            "incident_id",
            "severity",
            "status",
            "summary",
            "owner",
            "workaround",
            "defect_id",
            "due_date",
            "opened_at",
            "closed_at",
        },
    )
    seen: set[str] = set()
    open_by_severity = {severity: 0 for severity in SEVERITIES}
    for number, row in enumerate(rows, 2):
        incident_id = row["incident_id"]
        severity = row["severity"].upper()
        status = row["status"].upper()
        if (
            not incident_id
            or incident_id in seen
            or severity not in SEVERITIES
            or status not in STATUSES
            or not row["summary"]
            or not row["owner"]
            or not row["workaround"]
            or not row["due_date"]
            or not row["opened_at"]
            or (severity in {"S1", "S2"} and not row["defect_id"])
            or (status == "CLOSED" and not row["closed_at"])
        ):
            raise ValueError(f"Incident row {number} is incomplete or invalid.")
        if status == "OPEN":
            open_by_severity[severity] += 1
        seen.add(incident_id)
    return {"total": len(rows), "openBySeverity": open_by_severity}


def known_issue_summary(path: Path) -> tuple[dict[str, Any], list[dict[str, str]]]:
    rows = read_csv(
        path,
        {
            "issue_id",
            "severity",
            "status",
            "summary",
            "owner",
            "workaround",
            "target_date",
            "defect_id",
            "closed_at",
        },
    )
    seen: set[str] = set()
    open_rows: list[dict[str, str]] = []
    for number, row in enumerate(rows, 2):
        issue_id = row["issue_id"]
        severity = row["severity"].upper()
        status = row["status"].upper()
        try:
            target = date.fromisoformat(row["target_date"])
        except ValueError as error:
            raise ValueError(f"Known-issue row {number} has an invalid target date.") from error
        if (
            not issue_id
            or issue_id in seen
            or severity not in {"S3", "S4"}
            or status not in STATUSES
            or not row["summary"]
            or not row["owner"]
            or not row["workaround"]
            or not row["defect_id"]
            or (status == "CLOSED" and not row["closed_at"])
        ):
            raise ValueError(f"Known-issue row {number} is incomplete or invalid.")
        if status == "OPEN":
            if target < datetime.now(timezone.utc).date():
                raise ValueError(f"Known-issue row {number} has an overdue target date.")
            normalized = dict(row)
            normalized["severity"] = severity
            normalized["status"] = status
            open_rows.append(normalized)
        seen.add(issue_id)
    summary = {
        "totalRows": len(rows),
        "openCount": len(open_rows),
        "openS3": sum(row["severity"] == "S3" for row in open_rows),
        "openS4": sum(row["severity"] == "S4" for row in open_rows),
    }
    return summary, open_rows


def backup_summary(repo_root: Path, directory: Path) -> dict[str, Any]:
    manifest_path = directory / "manifest.txt"
    if not manifest_path.is_file():
        raise ValueError("Configured M5.5 backup has no manifest.txt.")
    values: dict[str, str] = {}
    for raw in manifest_path.read_text(encoding="utf-8").splitlines():
        if "=" in raw:
            key, value = raw.split("=", 1)
            values[key.strip()] = value.strip()
    try:
        age = int(datetime.now(timezone.utc).timestamp()) - int(values.get("created_at_epoch", "0"))
    except ValueError:
        age = -1
    if values.get("backup_format_version") != "2":
        raise ValueError("M5.5 requires backup format version 2.")
    if not 0 <= age <= 24 * 60 * 60:
        raise ValueError("M5.5 requires a verified backup no older than 24 hours.")
    if values.get("release_commit") != current_commit(repo_root):
        raise ValueError("Backup release commit does not match the M5.5 release.")
    if values.get("worktree_state") != "clean" or values.get("quiesced") != "true":
        raise ValueError("M5.5 requires a clean, quiesced local backup.")
    return {
        "directoryName": directory.name,
        "createdAt": values.get("created_at"),
        "ageSeconds": age,
        "releaseCommit": values.get("release_commit"),
        "manifestSha256": hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
    }


def owner_record(config: dict[str, str]) -> dict[str, str]:
    mapping = {
        "authorisedBy": "PILOT_M5_5_AUTHORISED_BY",
        "authoriserTitle": "PILOT_M5_5_AUTHORISER_TITLE",
        "uatLead": "PILOT_M5_5_UAT_LEAD",
        "technicalOwner": "PILOT_M5_5_TECHNICAL_OWNER",
        "supportContact": "PILOT_M5_5_SUPPORT_CONTACT",
        "monitoringOwner": "PILOT_M5_5_MONITORING_OWNER",
        "backupRestoreOwner": "PILOT_M5_5_BACKUP_RESTORE_OWNER",
        "incidentOwner": "PILOT_M5_5_INCIDENT_OWNER",
        "rollbackOwner": "PILOT_M5_5_ROLLBACK_OWNER",
        "dataPrivacyOwner": "PILOT_M5_5_DATA_PRIVACY_OWNER",
        "handoverRecipient": "PILOT_M5_5_HANDOVER_RECIPIENT",
    }
    owners = {name: required(config, key) for name, key in mapping.items()}
    if any(len(value) < 3 for value in owners.values()):
        raise ValueError("Every M5.5 owner/recipient must be a meaningful named value.")
    return owners


def write_json(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def prepare_run_dir(output: Path, mode: str) -> Path:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    run_dir = output.resolve() / f"rabbit-m5.5-{mode}-{stamp}"
    run_dir.mkdir(parents=True, exist_ok=False, mode=0o700)
    return run_dir


def finalize_checksums(run_dir: Path, mode: str) -> tuple[str, str]:
    files = sorted(
        path
        for path in run_dir.rglob("*")
        if path.is_file()
        and path.name not in {"SHA256SUMS", "evidence-reference.txt", "decision-payload.json"}
    )
    lines = [
        f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.relative_to(run_dir).as_posix()}"
        for path in files
    ]
    checksum_path = run_dir / "SHA256SUMS"
    checksum_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    digest = hashlib.sha256(checksum_path.read_bytes()).hexdigest()
    reference = f"urn:rabbit-evidence:m5-5:{mode}:{run_dir.name}:{digest}"
    (run_dir / "evidence-reference.txt").write_text(reference + "\n", encoding="utf-8")
    return reference, digest


def prepare(
    repo_root: Path,
    config_path: Path,
    config: dict[str, str],
    output: Path,
) -> tuple[Path, Evidence]:
    evidence = Evidence()
    outcome = required(config, "PILOT_M5_5_OUTCOME").upper()
    if outcome not in OUTCOMES:
        raise ValueError("PILOT_M5_5_OUTCOME must be GO, CONDITIONAL_RETEST, or NO_GO.")
    retest_raw = config.get("PILOT_M5_5_RETEST_BY_UTC", "").strip()
    retest = parse_timestamp(retest_raw) if retest_raw else None
    if outcome == "CONDITIONAL_RETEST" and (
        retest is None or retest <= datetime.now(timezone.utc)
    ):
        raise ValueError("Conditional Retest requires a future UTC deadline.")
    if outcome != "CONDITIONAL_RETEST" and retest is not None:
        raise ValueError("Only Conditional Retest may include a retest deadline.")

    api = RabbitApi(required(config, "PILOT_M5_5_BASE_URL"))
    organisation_code = required(config, "PILOT_M5_5_ORGANISATION_CODE")
    me = api.login(
        required(config, "PILOT_M5_5_ADMIN_EMAIL"),
        required(config, "PILOT_M5_5_ADMIN_PASSWORD"),
        organisation_code,
    )
    institution = required(config, "PILOT_M5_5_INSTITUTION_NAME")
    evidence.record(
        me.get("organisationName") == institution,
        "Institution identity",
        "Authenticated tenant matches the approved institution.",
    )
    readiness = api.json("/pilot-readiness")
    evidence.record(
        not readiness.get("signedOff"),
        "Decision register open",
        "No prior Go decision has locked the register.",
    )
    nonpassing = sorted(
        str(item.get("key"))
        for item in readiness.get("checks", [])
        if item.get("mandatory") and item.get("status") != "PASS"
    )
    go_eligible = not nonpassing and bool(readiness.get("mandatoryChecksPassed"))
    evidence.record(
        go_eligible or outcome != "GO",
        "Outcome matches mandatory gates",
        "Every mandatory check passed."
        if go_eligible
        else f"Non-passing mandatory checks: {', '.join(nonpassing)}.",
    )
    web_evidence = [
        str(item.get("key"))
        for item in readiness.get("checks", [])
        if item.get("status") == "PASS"
        and not LOCAL_REFERENCE.fullmatch(str(item.get("evidenceUrl") or ""))
    ]
    evidence.record(
        not web_evidence,
        "Local evidence references",
        "Every passing check points to checksummed local Rabbit evidence."
        if not web_evidence
        else "Non-local passing evidence: " + ", ".join(web_evidence),
    )

    release_commit = current_commit(repo_root)
    release_version = required(config, "PILOT_M5_5_RELEASE_VERSION")
    operations = api.json("/operations/readiness")
    evidence.record(
        operations.get("releaseVersion") == release_version,
        "Running release version",
        f"Running={operations.get('releaseVersion')}; approved={release_version}.",
    )
    evidence.record(
        operations.get("overallStatus") == "READY" or outcome != "GO",
        "Operations state",
        f"Operations status={operations.get('overallStatus')}; outcome={outcome}.",
    )
    flags = {str(item.get("key")): item for item in api.json("/feature-flags")}
    external = flags.get("EXTERNAL_DELIVERY", {})
    evidence.record(
        external.get("enabled") is False
        and external.get("activeForCurrentUser") is False,
        "External delivery disabled",
        "Email/SMS delivery remains disabled.",
    )

    live_path = resolve_path(
        config_path, required(config, "PILOT_M5_5_LIVE_RECONCILIATION")
    )
    live = validate_bundled_json(live_path)
    live_matches = (
        live.get("eventType") == "LIVE"
        and live.get("releaseCommit") == release_commit
        and live.get("releaseVersion") == release_version
        and live.get("organisationId") == me.get("organisationId")
    )
    evidence.record(live_matches, "Live reconciliation provenance", "Selected LIVE evidence matches this tenant and exact local release.")
    evidence.record(
        bool(live.get("passed")) or outcome != "GO",
        "Live reconciliation result",
        f"Live reconciliation passed={bool(live.get('passed'))}; outcome={outcome}.",
    )

    incident_path = resolve_path(
        config_path, required(config, "PILOT_M5_5_INCIDENT_FILE")
    )
    incidents = incident_summary(incident_path)
    open_s1 = incidents["openBySeverity"]["S1"]
    open_s2 = incidents["openBySeverity"]["S2"]
    evidence.record(
        open_s1 == 0 or outcome == "NO_GO",
        "Severity 1 disposition",
        f"Open S1={open_s1}; selected outcome={outcome}.",
    )
    evidence.record(
        (open_s1 + open_s2 == 0) or outcome != "GO",
        "Severity 1/2 Go gate",
        f"Open S1/S2={open_s1 + open_s2}; selected outcome={outcome}.",
    )

    issue_path = resolve_path(
        config_path, required(config, "PILOT_M5_5_KNOWN_ISSUES_FILE")
    )
    issues, open_issues = known_issue_summary(issue_path)
    issues_sha = hashlib.sha256(issue_path.read_bytes()).hexdigest()
    known_reference = (
        f"urn:rabbit-evidence:m5-5:known-issues:{issues_sha}" if open_issues else None
    )
    evidence.record(
        True,
        "Owned Severity 3/4 register",
        f"Open S3={issues['openS3']}; open S4={issues['openS4']}.",
    )

    signed_path = resolve_path(
        config_path, required(config, "PILOT_M5_5_SIGNED_ACCEPTANCE_FILE")
    )
    if signed_path.suffix.lower() != ".pdf" or not signed_path.is_file():
        raise ValueError("PILOT_M5_5_SIGNED_ACCEPTANCE_FILE must be a local PDF.")
    if not 1 <= signed_path.stat().st_size <= 20 * 1024 * 1024:
        raise ValueError("Signed acceptance PDF must be non-empty and no larger than 20 MB.")
    owners = owner_record(config)
    local_data = confirmation(config, "PILOT_M5_5_LOCAL_DATA_CONFIRMED")
    local_only = confirmation(config, "PILOT_M5_5_LOCAL_ONLY_CONFIRMED")
    ownership = confirmation(config, "PILOT_M5_5_OWNERSHIP_ACCEPTED")
    scope_freeze = confirmation(config, "PILOT_M5_5_SCOPE_FREEZE_ACCEPTED")
    evidence.record(local_data and local_only, "Local-only architecture and media", "Local data/media and zero-cost local infrastructure are explicitly confirmed.")
    evidence.record(
        (ownership and scope_freeze) or outcome != "GO",
        "Go handover attestations",
        f"Ownership accepted={ownership}; scope freeze accepted={scope_freeze}.",
    )
    backup_dir = resolve_path(
        config_path, required(config, "PILOT_M5_5_BACKUP_DIRECTORY")
    )
    backup = backup_summary(repo_root, backup_dir)
    media = {
        "primaryDataMedia": required(config, "PILOT_M5_5_PRIMARY_DATA_MEDIA"),
        "separateBackupMedia": required(config, "PILOT_M5_5_BACKUP_MEDIA"),
    }

    run_dir = prepare_run_dir(output, "prepare")
    shutil.copy2(signed_path, run_dir / "signed-institution-acceptance.pdf")
    write_json(run_dir / "readiness-snapshot.json", readiness)
    write_json(run_dir / "operations-snapshot.json", operations)
    write_json(run_dir / "live-reconciliation-summary.json", live)
    write_json(run_dir / "incident-summary.json", incidents)
    write_json(run_dir / "known-issues.json", {"summary": issues, "openIssues": open_issues})
    write_json(run_dir / "checks.json", {"checks": evidence.checks, "failures": evidence.failures, "passed": evidence.failures == 0})
    manifest = {
        "evidenceType": "Rabbit M5.5 approval and handover preparation",
        "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "outcome": outcome,
        "retestBy": retest.isoformat().replace("+00:00", "Z") if retest else None,
        "releaseVersion": release_version,
        "releaseCommit": release_commit,
        "organisationId": me.get("organisationId"),
        "organisationCode": organisation_code,
        "institutionName": institution,
        "owners": owners,
        "decisionReason": required(config, "PILOT_M5_5_DECISION_REASON"),
        "knownIssueCount": issues["openCount"],
        "knownIssuesReference": known_reference,
        "signedAcceptanceSha256": hashlib.sha256(signed_path.read_bytes()).hexdigest(),
        "liveReconciliationSha256": hashlib.sha256(live_path.read_bytes()).hexdigest(),
        "backup": backup,
        "media": media,
        "localDataConfirmed": local_data,
        "localOnlyConfirmed": local_only,
        "ownershipAccepted": ownership,
        "scopeFreezeAccepted": scope_freeze,
        "cloudRuntimeUsed": False,
        "publicEndpointUsed": False,
        "credentialsRecorded": False,
        "passed": evidence.failures == 0,
    }
    write_json(run_dir / "prepare-manifest.json", manifest)
    return run_dir, evidence


def write_decision_payload(run_dir: Path, reference: str, digest: str) -> None:
    manifest = json.loads((run_dir / "prepare-manifest.json").read_text(encoding="utf-8"))
    owners = manifest["owners"]
    payload = {
        "outcome": manifest["outcome"],
        "releaseVersion": manifest["releaseVersion"],
        "releaseCommit": manifest["releaseCommit"],
        "institutionName": manifest["institutionName"],
        **owners,
        "evidenceReference": reference,
        "evidenceSha256": digest,
        "knownIssueCount": manifest["knownIssueCount"],
        "knownIssuesReference": manifest["knownIssuesReference"],
        "decisionReason": manifest["decisionReason"],
        "retestBy": manifest["retestBy"],
        "localDataConfirmed": manifest["localDataConfirmed"],
        "localOnlyConfirmed": manifest["localOnlyConfirmed"],
        "ownershipAccepted": manifest["ownershipAccepted"],
        "scopeFreezeAccepted": manifest["scopeFreezeAccepted"],
    }
    write_json(run_dir / "decision-payload.json", payload)


def finalize(
    config: dict[str, str],
    prepared_manifest_path: Path,
    output: Path,
) -> tuple[Path, Evidence]:
    evidence = Evidence()
    prepared = validate_bundled_json(prepared_manifest_path)
    if not prepared.get("passed"):
        raise ValueError("The selected M5.5 preparation bundle did not pass.")
    prepared_dir = prepared_manifest_path.parent
    prepared_reference = (prepared_dir / "evidence-reference.txt").read_text(encoding="utf-8").strip()
    prepared_digest = hashlib.sha256((prepared_dir / "SHA256SUMS").read_bytes()).hexdigest()
    if not LOCAL_REFERENCE.fullmatch(prepared_reference) or not prepared_reference.endswith(prepared_digest):
        raise ValueError("Prepared evidence reference does not match its checksum bundle.")

    api = RabbitApi(required(config, "PILOT_M5_5_BASE_URL"))
    me = api.login(
        required(config, "PILOT_M5_5_ADMIN_EMAIL"),
        required(config, "PILOT_M5_5_ADMIN_PASSWORD"),
        required(config, "PILOT_M5_5_ORGANISATION_CODE"),
    )
    readiness = api.json("/pilot-readiness")
    match = next(
        (
            item
            for item in readiness.get("decisions", [])
            if item.get("evidenceReference") == prepared_reference
            and item.get("evidenceSha256") == prepared_digest
        ),
        None,
    )
    evidence.record(match is not None, "Immutable decision exists", "Rabbit contains the decision for the exact prepared bundle.")
    if match is None:
        match = {}
    expected = {
        "outcome": prepared.get("outcome"),
        "releaseVersion": prepared.get("releaseVersion"),
        "releaseCommit": prepared.get("releaseCommit"),
        "institutionName": prepared.get("institutionName"),
        "decisionReason": prepared.get("decisionReason"),
        "knownIssueCount": prepared.get("knownIssueCount"),
        "knownIssuesReference": prepared.get("knownIssuesReference"),
        "retestBy": prepared.get("retestBy"),
        **prepared.get("owners", {}),
    }
    mismatches = sorted(key for key, value in expected.items() if match.get(key) != value)
    evidence.record(
        not mismatches,
        "Decision matches approved handover",
        "Every decision and owner field matches the signed preparation."
        if not mismatches
        else "Mismatched fields: " + ", ".join(mismatches),
    )
    expected_signed = prepared.get("outcome") == "GO"
    evidence.record(
        readiness.get("signedOff") is expected_signed,
        "Go lock state",
        f"Expected signedOff={expected_signed}; actual={readiness.get('signedOff')}.",
    )
    audits = api.json(
        "/audit-events",
        query={
            "module": "OPS",
            "action": "PILOT_RELEASE_DECISION",
            "from": str(prepared.get("generatedAtUtc")),
        },
    )
    matching_audits = [
        item
        for item in audits
        if item.get("entityId") == match.get("id")
        and item.get("action") == "PILOT_RELEASE_DECISION"
    ]
    evidence.record(
        len(matching_audits) == 1,
        "Decision audit event",
        f"Matching immutable decision audits={len(matching_audits)}.",
    )
    evidence.record(
        me.get("organisationId") == prepared.get("organisationId"),
        "Final tenant identity",
        "Finalization uses the prepared institution tenant.",
    )
    run_dir = prepare_run_dir(output, "final")
    write_json(run_dir / "decision.json", match)
    write_json(run_dir / "readiness-after-decision.json", readiness)
    write_json(run_dir / "decision-audit.json", matching_audits)
    write_json(
        run_dir / "final-manifest.json",
        {
            "evidenceType": "Rabbit M5.5 final approval and handover",
            "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "preparedEvidenceReference": prepared_reference,
            "preparedEvidenceSha256": prepared_digest,
            "decisionId": match.get("id"),
            "outcome": match.get("outcome"),
            "organisationId": me.get("organisationId"),
            "cloudRuntimeUsed": False,
            "publicEndpointUsed": False,
            "credentialsRecorded": False,
            "passed": evidence.failures == 0,
        },
    )
    write_json(run_dir / "checks.json", {"checks": evidence.checks, "failures": evidence.failures, "passed": evidence.failures == 0})
    return run_dir, evidence


def main() -> int:
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("prepare", "finalize"))
    parser.add_argument("--repo-root", required=True, type=Path)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--prepared-manifest", type=Path)
    args = parser.parse_args()
    config_path = args.config.resolve()
    try:
        config = parse_env(config_path)
        if args.mode == "prepare":
            if args.prepared_manifest is not None:
                raise ValueError("--prepared-manifest is valid only for finalize.")
            run_dir, evidence = prepare(
                args.repo_root.resolve(), config_path, config, args.output
            )
        else:
            if args.prepared_manifest is None:
                raise ValueError("--prepared-manifest is required for finalize.")
            run_dir, evidence = finalize(
                config, args.prepared_manifest.resolve(), args.output
            )
    except Exception as error:
        print(f"M5.5 {args.mode} could not complete: {error}", file=sys.stderr)
        return 1
    reference, digest = finalize_checksums(run_dir, args.mode)
    if args.mode == "prepare":
        write_decision_payload(run_dir, reference, digest)
    print(f"M5.5 {args.mode} evidence: {run_dir}")
    print(f"Evidence reference: {reference}")
    print(f"Evidence SHA-256: {digest}")
    if evidence.failures:
        print(f"M5.5 {args.mode} failed {evidence.failures} check(s).", file=sys.stderr)
        return 1
    print(f"M5.5 {args.mode} checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
