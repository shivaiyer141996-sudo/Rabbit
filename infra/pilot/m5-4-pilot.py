#!/usr/bin/env python3
"""Create local-only Rabbit M5.4 freeze and reconciliation evidence."""

from __future__ import annotations

import argparse
import csv
import hashlib
import ipaddress
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen

M5_4_KEYS = {
    "STAFF_REHEARSAL",
    "LIVE_ASSESSMENT",
    "PILOT_RECONCILIATION",
    "INCIDENT_CLOSURE",
}
M5_4_PREREQUISITES = {
    "IDENTITY",
    "TENANT_ISOLATION",
    "QUESTION_GOVERNANCE",
    "ASSESSMENT_GOVERNANCE",
    "DELIVERY_RECOVERY",
    "EVALUATION_PUBLICATION",
    "REPORTS_EXPORTS",
    "OPERATIONS_OBSERVABILITY",
    "ACCESSIBILITY",
    "MOBILE_WEB",
    "BACKUP_RESTORE",
    "PERFORMANCE",
    "SECURITY_REVIEW",
    "OPERATING_OWNERSHIP",
}
VALID_EVENT_TYPES = {"REHEARSAL", "LIVE"}
VALID_ATTENDANCE = {"YES", "NO"}
VALID_SEVERITIES = {"S1", "S2", "S3", "S4"}
VALID_INCIDENT_STATUSES = {"OPEN", "CLOSED"}
DEMO_DOMAIN = "@demo.rabbit.local"
SAFE_IDENTIFIER = re.compile(r"^[A-Za-z0-9._-]+$")


@dataclass(frozen=True)
class RosterRow:
    email: str
    attended: str
    absence_reason: str


class Evidence:
    def __init__(self) -> None:
        self.checks: list[dict[str, str]] = []

    def record(self, passed: bool, check: str, detail: str) -> None:
        self.checks.append(
            {
                "status": "PASS" if passed else "FAIL",
                "check": check,
                "detail": detail,
            }
        )

    @property
    def failures(self) -> int:
        return sum(item["status"] == "FAIL" for item in self.checks)


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


def required(config: dict[str, str], name: str) -> str:
    value = config.get(name, "").strip()
    upper = value.upper()
    if not value or upper == "REPLACE_ME" or upper.startswith("REPLACE_WITH_"):
        raise ValueError(f"{name} is missing or still contains a placeholder.")
    return value


def positive_int(config: dict[str, str], name: str) -> int:
    value = required(config, name)
    if not value.isdigit() or int(value) < 1:
        raise ValueError(f"{name} must be a positive integer.")
    return int(value)


def canonical_hash(value: Any) -> str:
    encoded = json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def parse_timestamp(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(timezone.utc)


def safe_base_url(value: str) -> str:
    parsed = urlparse(value)
    if parsed.scheme not in {"http", "https"}:
        raise ValueError("PILOT_M5_4_BASE_URL must use HTTP or HTTPS.")
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("PILOT_M5_4_BASE_URL cannot contain credentials, query, or fragment.")
    if parsed.path not in {"", "/"}:
        raise ValueError("PILOT_M5_4_BASE_URL must not contain a path.")
    host = (parsed.hostname or "").lower()
    if host != "localhost":
        try:
            address = ipaddress.ip_address(host)
        except ValueError as error:
            raise ValueError(
                "Use localhost or an explicit loopback/private IP for M5.4."
            ) from error
        if not (address.is_loopback or address.is_private):
            raise ValueError("M5.4 refuses a public network target.")
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
    ) -> tuple[int, dict[str, str], bytes]:
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
                Request(url, data=body, headers=headers, method=method),
                timeout=30,
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
            raise RuntimeError(f"Local Rabbit API is unavailable: {error.reason}") from error

    def json(
        self,
        path: str,
        *,
        method: str = "GET",
        payload: dict[str, Any] | None = None,
        query: dict[str, str] | None = None,
    ) -> Any:
        status, _, body = self.request(
            path,
            method=method,
            payload=payload,
            query=query,
        )
        if status != 200:
            raise RuntimeError(f"Rabbit API {path} returned HTTP {status}.")
        try:
            return json.loads(body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise RuntimeError(f"Rabbit API {path} did not return valid JSON.") from error

    def login(self, email: str, password: str, organisation_code: str) -> dict[str, Any]:
        if email.lower().endswith(DEMO_DOMAIN):
            raise ValueError("M5.4 cannot use a seeded demo administrator.")
        response = self.json(
            "/auth/login",
            method="POST",
            payload={"email": email, "password": password},
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
                raise RuntimeError("Configured organisation is unavailable to the M5.4 Admin.")
            response = self.json(
                "/auth/select-organisation",
                method="POST",
                payload={
                    "selectionToken": response.get("selectionToken"),
                    "organisationId": choice.get("id"),
                },
            )
        if response.get("role") != "ORG_ADMIN" or not response.get("accessToken"):
            raise RuntimeError("M5.4 evidence requires an active Organisation Admin.")
        self.token = str(response["accessToken"])
        me = self.json("/auth/me")
        if me.get("organisationCode") != organisation_code:
            raise RuntimeError("Authenticated organisation does not match the protected config.")
        return me


def resolve_protected_path(config_path: Path, value: str) -> Path:
    path = Path(value).expanduser()
    if not path.is_absolute():
        path = config_path.parent / path
    return path.resolve()


def parse_key_value_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        if "=" not in raw_line:
            continue
        key, value = raw_line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def validate_backup(
    repo_root: Path,
    config_path: Path,
    config: dict[str, str],
    evidence: Evidence,
) -> dict[str, Any]:
    backup_dir = resolve_protected_path(
        config_path, required(config, "PILOT_M5_4_BACKUP_DIRECTORY")
    )
    manifest_path = backup_dir / "manifest.txt"
    if not manifest_path.is_file():
        raise ValueError("Configured M5.4 backup has no manifest.txt.")
    manifest = parse_key_value_file(manifest_path)
    try:
        created_epoch = int(manifest.get("created_at_epoch", "0"))
    except ValueError:
        created_epoch = 0
    age_seconds = int(datetime.now(timezone.utc).timestamp()) - created_epoch
    release_commit = current_commit(repo_root)
    checks = {
        "format": manifest.get("backup_format_version") == "2",
        "age": 0 <= age_seconds <= 24 * 60 * 60,
        "release": manifest.get("release_commit") == release_commit,
        "worktree": manifest.get("worktree_state") == "clean",
        "quiesced": manifest.get("quiesced") == "true",
    }
    evidence.record(
        checks["format"],
        "Backup format",
        f"Backup format version={manifest.get('backup_format_version', 'missing')}.",
    )
    evidence.record(
        checks["age"],
        "Backup recovery point",
        f"Backup age seconds={age_seconds}; maximum=86400.",
    )
    evidence.record(
        checks["release"] and checks["worktree"],
        "Backup release provenance",
        "Backup matches the clean frozen release commit."
        if checks["release"] and checks["worktree"]
        else "Backup commit or worktree state does not match the frozen release.",
    )
    evidence.record(
        checks["quiesced"],
        "Consistent pre-event backup",
        "Backup was captured with Rabbit application access quiesced."
        if checks["quiesced"]
        else "Pre-event backup was not quiesced.",
    )
    return {
        "directoryName": backup_dir.name,
        "createdAt": manifest.get("created_at"),
        "ageSeconds": age_seconds,
        "releaseCommit": manifest.get("release_commit"),
        "manifestSha256": hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
    }


def read_roster(path: Path, reconcile: bool) -> list[RosterRow]:
    rows: list[RosterRow] = []
    with path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        required_columns = {"email", "attended", "absence_reason"}
        if not reader.fieldnames or not required_columns.issubset(reader.fieldnames):
            raise ValueError(
                "Roster must contain email, attended, and absence_reason columns."
            )
        for number, raw in enumerate(reader, start=2):
            email = (raw.get("email") or "").strip().lower()
            attended = (raw.get("attended") or "").strip().upper()
            absence_reason = (raw.get("absence_reason") or "").strip()
            if not email or "@" not in email:
                raise ValueError(f"Roster row {number} has an invalid email.")
            if reconcile and attended not in VALID_ATTENDANCE:
                raise ValueError(
                    f"Roster row {number} must mark attended as yes or no."
                )
            if attended and attended not in VALID_ATTENDANCE:
                raise ValueError(f"Roster row {number} has invalid attendance.")
            if attended == "NO" and not absence_reason:
                raise ValueError(
                    f"Roster row {number} requires an absence reason for non-attendance."
                )
            rows.append(RosterRow(email, attended, absence_reason))
    emails = [row.email for row in rows]
    if len(emails) != len(set(emails)):
        raise ValueError("Roster contains duplicate student emails.")
    if not rows:
        raise ValueError("Roster must contain at least one Student.")
    return rows


def roster_fingerprint(rows: list[RosterRow]) -> str:
    return canonical_hash(sorted(row.email for row in rows))


def participant_key(user_id: str) -> str:
    return hashlib.sha256(f"rabbit-m5.4:{user_id}".encode("utf-8")).hexdigest()[:20]


def question_fingerprints(api: RabbitApi, assessment: dict[str, Any]) -> dict[str, str]:
    result: dict[str, str] = {}
    for question_id in assessment.get("questionIds", []):
        question = api.json(f"/questions/{question_id}")
        result[str(question_id)] = canonical_hash(question)
    return result


def current_commit(repo_root: Path) -> str:
    return subprocess.run(
        ["git", "-C", str(repo_root), "rev-parse", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def common_context(
    api: RabbitApi,
    config: dict[str, str],
    roster: list[RosterRow],
    evidence: Evidence,
) -> dict[str, Any]:
    organisation_code = required(config, "PILOT_M5_4_ORGANISATION_CODE")
    me = api.login(
        required(config, "PILOT_M5_4_ADMIN_EMAIL"),
        required(config, "PILOT_M5_4_ADMIN_PASSWORD"),
        organisation_code,
    )
    expected_institution = required(config, "PILOT_M5_4_INSTITUTION_NAME")
    evidence.record(
        me.get("organisationName") == expected_institution,
        "Institution identity",
        "Authenticated tenant matches the approved institution name."
        if me.get("organisationName") == expected_institution
        else "Authenticated tenant does not match the approved institution name.",
    )

    users = api.json("/users")
    active_users = [item for item in users if item.get("status") == "ACTIVE"]
    evidence.record(
        len(active_users) <= 50,
        "Pilot active-user cap",
        f"Active users={len(active_users)}; hard cap=50.",
    )
    active_demo = [
        item for item in active_users if str(item.get("email", "")).lower().endswith(DEMO_DOMAIN)
    ]
    evidence.record(
        not active_demo,
        "Seeded identities retired",
        "No active seeded demo identity remains."
        if not active_demo
        else f"Active seeded identities={len(active_demo)}.",
    )
    by_email = {str(item.get("email", "")).lower(): item for item in users}
    resolved: list[dict[str, Any]] = []
    for row in roster:
        user = by_email.get(row.email)
        if user is not None:
            resolved.append(user)
    evidence.record(
        len(resolved) == len(roster),
        "Approved roster exists",
        f"Resolved Students={len(resolved)} of roster={len(roster)}.",
    )
    invalid_students = [
        item
        for item in resolved
        if item.get("role") != "STUDENT" or item.get("status") != "ACTIVE"
    ]
    evidence.record(
        not invalid_students,
        "Roster roles and status",
        "Every resolved roster member is an active Student."
        if not invalid_students
        else f"Invalid roster memberships={len(invalid_students)}.",
    )
    maximum_students = positive_int(config, "PILOT_M5_4_MAX_STUDENTS")
    if maximum_students > 30:
        raise ValueError("Release 1.0 M5.4 cannot approve more than 30 Students.")
    evidence.record(
        len(roster) <= maximum_students,
        "Approved Student cohort cap",
        f"Roster Students={len(roster)}; approved cap={maximum_students}.",
    )
    roles = {item.get("role") for item in active_users}
    required_roles = {"ORG_ADMIN", "FACULTY", "REVIEWER"}
    evidence.record(
        required_roles.issubset(roles),
        "Pilot staff roles",
        "Active Admin, Teacher, and Reviewer roles are present."
        if required_roles.issubset(roles)
        else "One or more required pilot staff roles are absent.",
    )
    owners: dict[str, str] = {}
    for owner_key, owner_label in (
        ("PILOT_M5_4_UAT_LEAD", "Institution UAT lead"),
        ("PILOT_M5_4_TECHNICAL_OWNER", "Technical owner"),
        ("PILOT_M5_4_SUPPORT_OWNER", "Live support owner"),
        ("PILOT_M5_4_ROLLBACK_OWNER", "Rollback owner"),
        ("PILOT_M5_4_DATA_PRIVACY_OWNER", "Data/privacy owner"),
        ("PILOT_M5_4_INCIDENT_CHANNEL", "Incident/support channel"),
    ):
        owner = required(config, owner_key)
        owners[owner_key.removeprefix("PILOT_M5_4_").lower()] = owner
        evidence.record(
            len(owner) >= 3,
            owner_label,
            "Named in the protected M5.4 operating record.",
        )

    return {
        "me": me,
        "users": users,
        "resolvedRosterUsers": resolved,
        "owners": owners,
    }


def validate_readiness(
    readiness: dict[str, Any],
    event_type: str,
    evidence: Evidence,
) -> None:
    evidence.record(
        not readiness.get("signedOff"),
        "Pilot register remains open",
        "Institutional sign-off has not been locked before execution.",
    )
    status_by_key = {
        str(item.get("key")): item.get("status")
        for item in readiness.get("checks", [])
        if item.get("mandatory")
    }
    required_keys = set(M5_4_PREREQUISITES)
    if event_type == "LIVE":
        required_keys.add("STAFF_REHEARSAL")
    failed_prerequisites = sorted(
        key for key in required_keys if status_by_key.get(key) != "PASS"
    )
    evidence.record(
        not failed_prerequisites,
        "M5.1-M5.3 prerequisites",
        "Every mandatory prerequisite has passed."
        if not failed_prerequisites
        else "Not passed: " + ", ".join(sorted(failed_prerequisites)),
    )


def validate_assessment(
    assessment: dict[str, Any],
    config: dict[str, str],
    all_users: list[dict[str, Any]],
    resolved_users: list[dict[str, Any]],
    evidence: Evidence,
    *,
    before_event: bool,
) -> None:
    assessment_id = required(config, "PILOT_M5_4_ASSESSMENT_ID")
    evidence.record(
        str(assessment.get("id")) == assessment_id,
        "Assessment identity",
        "API assessment matches the approved assessment ID.",
    )
    evidence.record(
        assessment.get("status") == "SCHEDULED",
        "Assessment schedule state",
        f"Assessment status={assessment.get('status')}.",
    )
    evidence.record(
        assessment.get("attemptsAllowed") == 1,
        "Single controlled attempt",
        f"Attempts allowed={assessment.get('attemptsAllowed')}.",
    )
    minimum_questions = positive_int(config, "PILOT_M5_4_MIN_QUESTIONS")
    maximum_questions = positive_int(config, "PILOT_M5_4_MAX_QUESTIONS")
    question_count = int(assessment.get("questionCount") or 0)
    evidence.record(
        minimum_questions <= question_count <= maximum_questions,
        "Approved question count",
        f"Question count={question_count}; approved range={minimum_questions}-{maximum_questions}.",
    )
    minimum_duration = positive_int(config, "PILOT_M5_4_MIN_DURATION_MINUTES")
    maximum_duration = positive_int(config, "PILOT_M5_4_MAX_DURATION_MINUTES")
    duration = int(assessment.get("durationMinutes") or 0)
    evidence.record(
        minimum_duration <= duration <= maximum_duration,
        "Approved assessment duration",
        f"Duration={duration} minutes; approved range={minimum_duration}-{maximum_duration}.",
    )
    start_at = assessment.get("startAt")
    end_at = assessment.get("endAt")
    valid_window = bool(start_at and end_at)
    if valid_window:
        start = parse_timestamp(str(start_at))
        end = parse_timestamp(str(end_at))
        valid_window = start < end
        if before_event:
            valid_window = valid_window and datetime.now(timezone.utc) < start
    evidence.record(
        valid_window,
        "Assessment delivery window",
        "Schedule is valid and has not opened at freeze time."
        if valid_window and before_event
        else "Schedule timestamps are present and ordered."
        if valid_window
        else "Schedule is missing, invalid, or already open at freeze time.",
    )
    eligible_sections = {str(value) for value in assessment.get("eligibleSectionIds", [])}
    ineligible = [
        item
        for item in resolved_users
        if eligible_sections
        and (not item.get("sectionId") or str(item.get("sectionId")) not in eligible_sections)
    ]
    evidence.record(
        not ineligible,
        "Roster delivery eligibility",
        "Every resolved roster Student is eligible for this schedule."
        if not ineligible
        else f"Ineligible roster Students={len(ineligible)}.",
    )
    roster_user_ids = {str(item.get("userId")) for item in resolved_users}
    eligible_active_student_ids = {
        str(item.get("userId"))
        for item in all_users
        if item.get("role") == "STUDENT"
        and item.get("status") == "ACTIVE"
        and (
            not eligible_sections
            or item.get("sectionId")
            and str(item.get("sectionId")) in eligible_sections
        )
    }
    unexpected_eligible = eligible_active_student_ids - roster_user_ids
    evidence.record(
        not unexpected_eligible,
        "No unapproved eligible Student",
        "Only approved roster Students can access the scheduled assessment."
        if not unexpected_eligible
        else f"Active eligible Students outside roster={len(unexpected_eligible)}.",
    )


def write_json(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def finalize_bundle(
    run_dir: Path,
    event_type: str,
    mode: str,
    evidence: Evidence,
) -> str:
    write_json(
        run_dir / "checks.json",
        {
            "eventType": event_type,
            "mode": mode,
            "checks": evidence.checks,
            "failures": evidence.failures,
            "passed": evidence.failures == 0,
        },
    )
    evidence_files = sorted(
        path for path in run_dir.rglob("*") if path.is_file() and path.name not in {
            "SHA256SUMS",
            "evidence-reference.txt",
        }
    )
    checksum_lines = []
    for path in evidence_files:
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        checksum_lines.append(f"{digest}  {path.relative_to(run_dir).as_posix()}")
    checksum_path = run_dir / "SHA256SUMS"
    checksum_path.write_text("\n".join(checksum_lines) + "\n", encoding="utf-8")
    bundle_digest = hashlib.sha256(checksum_path.read_bytes()).hexdigest()
    run_id = run_dir.name
    reference = (
        f"urn:rabbit-evidence:m5-4:{event_type.lower()}:{mode}:"
        f"{run_id}:{bundle_digest}"
    )
    (run_dir / "evidence-reference.txt").write_text(reference + "\n", encoding="utf-8")
    return reference


def prepare_run_dir(output: Path, event_type: str, mode: str) -> Path:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    run_id = f"rabbit-m5.4-{event_type.lower()}-{mode}-{stamp}"
    if not SAFE_IDENTIFIER.fullmatch(run_id):
        raise RuntimeError("Generated evidence run ID is unsafe.")
    run_dir = output.resolve() / run_id
    run_dir.mkdir(parents=True, exist_ok=False, mode=0o700)
    return run_dir


def freeze(
    repo_root: Path,
    config_path: Path,
    config: dict[str, str],
    output: Path,
) -> tuple[Path, Evidence]:
    evidence = Evidence()
    event_type = required(config, "PILOT_M5_4_EVENT_TYPE").upper()
    if event_type not in VALID_EVENT_TYPES:
        raise ValueError("PILOT_M5_4_EVENT_TYPE must be REHEARSAL or LIVE.")
    roster_path = resolve_protected_path(
        config_path, required(config, "PILOT_M5_4_ROSTER_FILE")
    )
    roster = read_roster(roster_path, reconcile=False)
    run_dir = prepare_run_dir(output, event_type, "freeze")
    api = RabbitApi(required(config, "PILOT_M5_4_BASE_URL"))
    context = common_context(api, config, roster, evidence)
    readiness = api.json("/pilot-readiness")
    validate_readiness(readiness, event_type, evidence)
    operations = api.json("/operations/readiness")
    evidence.record(
        operations.get("overallStatus") == "READY",
        "Local operations readiness",
        f"Operations status={operations.get('overallStatus')}.",
    )
    workflows = operations.get("workflows") or {}
    evidence.record(
        workflows.get("activeAssessmentAttempts") == 0,
        "No pre-existing active attempt",
        f"Active attempts={workflows.get('activeAssessmentAttempts', 'unknown')}.",
    )
    evidence.record(
        workflows.get("pendingResultPublications") == 0,
        "No pre-existing publication backlog",
        "Pending result publications="
        f"{workflows.get('pendingResultPublications', 'unknown')}.",
    )
    flags = {
        str(item.get("key")): item for item in api.json("/feature-flags")
    }
    external_delivery = flags.get("EXTERNAL_DELIVERY", {})
    evidence.record(
        external_delivery.get("enabled") is False
        and external_delivery.get("activeForCurrentUser") is False,
        "Provider email/SMS disabled",
        "External delivery remains disabled for the local-only pilot.",
    )
    required_active_flags = {"PILOT_MODE", "PDF_EXPORTS", "EXCEL_EXPORTS"}
    inactive_required_flags = sorted(
        key
        for key in required_active_flags
        if flags.get(key, {}).get("activeForCurrentUser") is not True
    )
    evidence.record(
        not inactive_required_flags,
        "Required pilot feature flags",
        "Pilot mode plus PDF/Excel exports are active."
        if not inactive_required_flags
        else "Inactive required flags: " + ", ".join(inactive_required_flags),
    )
    assessment_id = required(config, "PILOT_M5_4_ASSESSMENT_ID")
    assessment = api.json(f"/assessments/{assessment_id}")
    validate_assessment(
        assessment,
        config,
        context["users"],
        context["resolvedRosterUsers"],
        evidence,
        before_event=True,
    )
    reviews = api.json(f"/assessments/{assessment_id}/reviews")
    approval_count = sum(item.get("decision") == "APPROVE" for item in reviews)
    evidence.record(
        approval_count > 0,
        "Independent assessment approval",
        f"Recorded approval decisions={approval_count}.",
    )
    fingerprints = question_fingerprints(api, assessment)
    evidence.record(
        len(fingerprints) == int(assessment.get("questionCount") or 0),
        "Question freeze fingerprints",
        f"Fingerprinted approved questions={len(fingerprints)}.",
    )
    release_commit = current_commit(repo_root)
    backup = validate_backup(repo_root, config_path, config, evidence)
    expected_release = required(config, "PILOT_M5_4_RELEASE_VERSION")
    evidence.record(
        operations.get("releaseVersion") == expected_release,
        "Approved release version",
        f"Running release={operations.get('releaseVersion')}; approved={expected_release}.",
    )
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    manifest = {
        "evidenceType": "Rabbit M5.4 institutional pilot freeze",
        "generatedAtUtc": now,
        "eventType": event_type,
        "releaseCommit": release_commit,
        "releaseVersion": operations.get("releaseVersion"),
        "organisationId": context["me"].get("organisationId"),
        "organisationCode": context["me"].get("organisationCode"),
        "institutionName": context["me"].get("organisationName"),
        "assessment": assessment,
        "assessmentFingerprint": canonical_hash(assessment),
        "questionFingerprints": fingerprints,
        "rosterCount": len(roster),
        "rosterFingerprint": roster_fingerprint(roster),
        "participantKeys": sorted(
            participant_key(str(item.get("userId")))
            for item in context["resolvedRosterUsers"]
        ),
        "owners": context["owners"],
        "backup": backup,
        "readiness": [
            {
                "key": item.get("key"),
                "mandatory": item.get("mandatory"),
                "status": item.get("status"),
            }
            for item in readiness.get("checks", [])
        ],
        "operationsStatus": operations.get("overallStatus"),
        "featureFlags": {
            key: {
                "enabled": value.get("enabled"),
                "rolloutPercentage": value.get("rolloutPercentage"),
                "activeForCurrentUser": value.get("activeForCurrentUser"),
            }
            for key, value in sorted(flags.items())
        },
        "assessmentApprovalCount": approval_count,
        "cloudRuntimeUsed": False,
        "publicEndpointUsed": False,
        "credentialsRecorded": False,
        "studentEmailsRecorded": False,
        "passed": evidence.failures == 0,
    }
    write_json(run_dir / "freeze-manifest.json", manifest)
    write_json(run_dir / "operations-snapshot.json", operations)
    write_json(
        run_dir / "assessment-review-summary.json",
        {
            "assessmentId": assessment_id,
            "reviewCount": len(reviews),
            "approvalCount": approval_count,
            "reviews": [
                {
                    "decision": item.get("decision"),
                    "createdAt": item.get("createdAt"),
                }
                for item in reviews
            ],
        },
    )
    return run_dir, evidence


def read_incidents(path: Path, evidence: Evidence) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        required_columns = {
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
        }
        if not reader.fieldnames or not required_columns.issubset(reader.fieldnames):
            raise ValueError("Incident register does not contain the required columns.")
        incidents = [
            {key: (value or "").strip() for key, value in row.items() if key}
            for row in reader
            if any((value or "").strip() for value in row.values())
        ]
    malformed: list[str] = []
    open_critical: list[str] = []
    seen_ids: set[str] = set()
    for index, incident in enumerate(incidents, start=2):
        incident_id = incident.get("incident_id", "")
        severity = incident.get("severity", "").upper()
        status = incident.get("status", "").upper()
        if (
            not incident_id
            or incident_id in seen_ids
            or severity not in VALID_SEVERITIES
            or status not in VALID_INCIDENT_STATUSES
            or not incident.get("summary")
            or not incident.get("owner")
            or not incident.get("workaround")
            or not incident.get("due_date")
            or not incident.get("opened_at")
            or (status == "CLOSED" and not incident.get("closed_at"))
        ):
            malformed.append(str(index))
        if severity in {"S1", "S2"} and status != "CLOSED":
            open_critical.append(incident_id or f"row-{index}")
        if severity in {"S1", "S2"} and not incident.get("defect_id"):
            malformed.append(str(index))
        seen_ids.add(incident_id)
    evidence.record(
        not malformed,
        "Incident register integrity",
        "Every incident has valid severity, status, owner, workaround, due date, timing, and closure data."
        if not malformed
        else "Malformed incident rows: " + ", ".join(sorted(set(malformed))),
    )
    evidence.record(
        not open_critical,
        "Severity 1/2 closure",
        "No Severity 1 or Severity 2 incident remains open."
        if not open_critical
        else "Open S1/S2 incidents: " + ", ".join(open_critical),
    )
    return incidents


def reconcile(
    repo_root: Path,
    config_path: Path,
    config: dict[str, str],
    output: Path,
    freeze_manifest_path: Path,
) -> tuple[Path, Evidence]:
    evidence = Evidence()
    event_type = required(config, "PILOT_M5_4_EVENT_TYPE").upper()
    if event_type not in VALID_EVENT_TYPES:
        raise ValueError("PILOT_M5_4_EVENT_TYPE must be REHEARSAL or LIVE.")
    roster_path = resolve_protected_path(
        config_path, required(config, "PILOT_M5_4_ROSTER_FILE")
    )
    incident_path = resolve_protected_path(
        config_path, required(config, "PILOT_M5_4_INCIDENT_FILE")
    )
    roster = read_roster(roster_path, reconcile=True)
    freeze_manifest = json.loads(freeze_manifest_path.read_text(encoding="utf-8"))
    if not freeze_manifest.get("passed"):
        raise ValueError("The selected freeze bundle did not pass.")
    run_dir = prepare_run_dir(output, event_type, "reconcile")
    api = RabbitApi(required(config, "PILOT_M5_4_BASE_URL"))
    context = common_context(api, config, roster, evidence)
    evidence.record(
        freeze_manifest.get("eventType") == event_type,
        "Freeze event type",
        "Freeze and reconciliation event types match.",
    )
    evidence.record(
        freeze_manifest.get("releaseCommit") == current_commit(repo_root),
        "Frozen release commit",
        "Reconciliation runs against the exact frozen commit.",
    )
    evidence.record(
        freeze_manifest.get("rosterFingerprint") == roster_fingerprint(roster),
        "Frozen roster",
        "The approved roster email set is unchanged.",
    )
    current_participant_keys = sorted(
        participant_key(str(item.get("userId")))
        for item in context["resolvedRosterUsers"]
    )
    evidence.record(
        freeze_manifest.get("participantKeys") == current_participant_keys,
        "Frozen Student identities",
        "Every approved roster email still resolves to the frozen Rabbit user.",
    )
    evidence.record(
        freeze_manifest.get("organisationId") == context["me"].get("organisationId"),
        "Frozen institution tenant",
        "Reconciliation uses the same tenant as the freeze.",
    )
    assessment_id = required(config, "PILOT_M5_4_ASSESSMENT_ID")
    assessment = api.json(f"/assessments/{assessment_id}")
    validate_assessment(
        assessment,
        config,
        context["users"],
        context["resolvedRosterUsers"],
        evidence,
        before_event=False,
    )
    evidence.record(
        freeze_manifest.get("assessmentFingerprint") == canonical_hash(assessment),
        "Frozen assessment configuration",
        "Assessment content, schedule, and settings match the approved freeze.",
    )
    current_questions = question_fingerprints(api, assessment)
    evidence.record(
        freeze_manifest.get("questionFingerprints") == current_questions,
        "Frozen question content",
        "Every question fingerprint matches the approved freeze.",
    )

    user_by_email = {
        str(item.get("email", "")).lower(): item for item in context["resolvedRosterUsers"]
    }
    attended_ids = {
        str(user_by_email[row.email].get("userId"))
        for row in roster
        if row.attended == "YES" and row.email in user_by_email
    }
    absent_ids = {
        str(user_by_email[row.email].get("userId"))
        for row in roster
        if row.attended == "NO" and row.email in user_by_email
    }
    evidence.record(
        len(attended_ids) + len(absent_ids) == len(roster),
        "Attendance disposition",
        f"Attended={len(attended_ids)}; absent={len(absent_ids)}; roster={len(roster)}.",
    )

    monitor = api.json(f"/evaluation/assessments/{assessment_id}/monitor")
    monitor_rows = monitor.get("attempts", [])
    attempt_ids = {str(item.get("attemptId")) for item in monitor_rows}
    attempt_student_ids = [str(item.get("studentUserId")) for item in monitor_rows]
    actual_ids = set(attempt_student_ids)
    evidence.record(
        monitor.get("inProgress") == 0,
        "No unfinished attempts",
        f"In-progress attempts={monitor.get('inProgress')}.",
    )
    evidence.record(
        len(attempt_student_ids) == len(actual_ids),
        "One attempt per attendee",
        f"Attempt rows={len(attempt_student_ids)}; unique Students={len(actual_ids)}.",
    )
    evidence.record(
        actual_ids == attended_ids,
        "Attendance-to-attempt reconciliation",
        f"Attendees={len(attended_ids)}; attempted Students={len(actual_ids)}.",
    )
    evidence.record(
        not (actual_ids & absent_ids),
        "Recorded absences",
        "No recorded absentee has an assessment attempt.",
    )

    evaluation = api.json(f"/evaluation/assessments/{assessment_id}/results")
    result_rows = evaluation.get("results", [])
    result_student_ids = {str(item.get("studentUserId")) for item in result_rows}
    evidence.record(
        result_student_ids == attended_ids,
        "Attendance-to-evaluation reconciliation",
        f"Evaluated Students={len(result_student_ids)}; attendees={len(attended_ids)}.",
    )
    evidence.record(
        evaluation.get("pendingPublicationCount") == 0,
        "Publication backlog",
        f"Pending results={evaluation.get('pendingPublicationCount')}.",
    )
    evidence.record(
        evaluation.get("publishedCount") == len(attended_ids),
        "Published result count",
        f"Published={evaluation.get('publishedCount')}; attendees={len(attended_ids)}.",
    )

    report = api.json(f"/reports/assessments/{assessment_id}")
    report_attempt_ids = {
        str(item.get("attemptId")) for item in report.get("studentResults", [])
    }
    evidence.record(
        report.get("submissions") == len(attended_ids)
        and report_attempt_ids == attempt_ids,
        "Published report reconciliation",
        f"Report submissions={report.get('submissions')}; attendees={len(attended_ids)}.",
    )

    exports: dict[str, dict[str, Any]] = {}
    for extension, expected_prefix in (
        ("csv", b"student"),
        ("pdf", b"%PDF"),
        ("xlsx", b"PK"),
    ):
        suffix = "" if extension == "csv" else f".{extension}"
        status, headers, body = api.request(
            f"/reports/assessments/{assessment_id}/export{suffix}"
        )
        prefix_ok = body.lower().startswith(expected_prefix) if extension == "csv" else body.startswith(expected_prefix)
        passed = status == 200 and prefix_ok and len(body) > 20
        evidence.record(
            passed,
            f"{extension.upper()} report export",
            f"HTTP {status}; bytes={len(body)}.",
        )
        if passed:
            export_path = run_dir / f"assessment-report.{extension}"
            export_path.write_bytes(body)
            exports[extension] = {
                "bytes": len(body),
                "contentType": headers.get("content-type"),
                "sha256": hashlib.sha256(body).hexdigest(),
            }

    freeze_time = str(freeze_manifest.get("generatedAtUtc"))
    delivery_audits = api.json(
        "/audit-events", query={"module": "DEL", "from": freeze_time}
    )
    evaluation_audits = api.json(
        "/audit-events", query={"module": "EVL", "from": freeze_time}
    )
    report_audits = api.json(
        "/audit-events", query={"module": "RPT", "from": freeze_time}
    )
    submitted_audits = {
        str(item.get("entityId"))
        for item in delivery_audits
        if item.get("action") in {"SUBMIT", "AUTO_SUBMIT"}
    }
    published_audits = {
        str(item.get("entityId"))
        for item in evaluation_audits
        if item.get("action") == "PUBLISH_RESULT"
    }
    assessment_publish_audit = any(
        item.get("action") == "PUBLISH_RESULTS"
        and str(item.get("entityId")) == assessment_id
        for item in evaluation_audits
    )
    evidence.record(
        attempt_ids.issubset(submitted_audits),
        "Submission audit reconciliation",
        f"Attempts={len(attempt_ids)}; audited submissions={len(attempt_ids & submitted_audits)}.",
    )
    evidence.record(
        attempt_ids.issubset(published_audits) and assessment_publish_audit,
        "Publication audit reconciliation",
        f"Attempts={len(attempt_ids)}; audited publications={len(attempt_ids & published_audits)}.",
    )
    required_export_actions = {
        "EXPORT_ASSESSMENT_CSV",
        "EXPORT_ASSESSMENT_PDF",
        "EXPORT_ASSESSMENT_XLSX",
    }
    audited_export_actions = {
        str(item.get("action"))
        for item in report_audits
        if str(item.get("entityId")) == assessment_id
    }
    evidence.record(
        required_export_actions.issubset(audited_export_actions),
        "Report export audit reconciliation",
        f"Audited governed export types={len(required_export_actions & audited_export_actions)} of 3.",
    )

    incidents = read_incidents(incident_path, evidence)
    incident_summary = [
        {
            "incidentId": item.get("incident_id"),
            "severity": item.get("severity", "").upper(),
            "status": item.get("status", "").upper(),
            "defectRecorded": bool(item.get("defect_id")),
            "dueDateRecorded": bool(item.get("due_date")),
            "closedAtRecorded": bool(item.get("closed_at")),
        }
        for item in incidents
    ]
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    reconciliation = {
        "evidenceType": "Rabbit M5.4 institutional pilot reconciliation",
        "generatedAtUtc": now,
        "eventType": event_type,
        "releaseCommit": current_commit(repo_root),
        "releaseVersion": freeze_manifest.get("releaseVersion"),
        "organisationId": context["me"].get("organisationId"),
        "assessmentId": assessment_id,
        "freezeManifest": str(freeze_manifest_path.resolve()),
        "rosterCount": len(roster),
        "attendedCount": len(attended_ids),
        "absentCount": len(absent_ids),
        "attemptCount": len(monitor_rows),
        "submittedCount": int(monitor.get("submitted") or 0),
        "autoSubmittedCount": int(monitor.get("autoSubmitted") or 0),
        "inProgressCount": int(monitor.get("inProgress") or 0),
        "evaluatedCount": int(evaluation.get("evaluatedCount") or 0),
        "pendingPublicationCount": int(evaluation.get("pendingPublicationCount") or 0),
        "publishedCount": int(evaluation.get("publishedCount") or 0),
        "reportSubmissionCount": int(report.get("submissions") or 0),
        "participantKeys": sorted(participant_key(value) for value in actual_ids),
        "exports": exports,
        "incidentCount": len(incidents),
        "incidents": incident_summary,
        "cloudRuntimeUsed": False,
        "publicEndpointUsed": False,
        "credentialsRecorded": False,
        "studentEmailsRecordedOutsideRestrictedExports": False,
        "passed": evidence.failures == 0,
    }
    write_json(run_dir / "reconciliation.json", reconciliation)
    write_json(
        run_dir / "audit-reconciliation.json",
        {
            "assessmentId": assessment_id,
            "attemptCount": len(attempt_ids),
            "submissionAuditCount": len(attempt_ids & submitted_audits),
            "publicationAuditCount": len(attempt_ids & published_audits),
            "assessmentPublicationAudit": assessment_publish_audit,
            "governedExportAuditCount": len(
                required_export_actions & audited_export_actions
            ),
        },
    )
    return run_dir, evidence


def main() -> int:
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("freeze", "reconcile"))
    parser.add_argument("--repo-root", required=True, type=Path)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--freeze-manifest", type=Path)
    args = parser.parse_args()
    config_path = args.config.resolve()
    config = parse_env(config_path)
    event_type = required(config, "PILOT_M5_4_EVENT_TYPE").upper()
    try:
        if args.mode == "freeze":
            run_dir, evidence = freeze(
                args.repo_root.resolve(), config_path, config, args.output
            )
        else:
            if args.freeze_manifest is None:
                raise ValueError("--freeze-manifest is required for reconciliation.")
            run_dir, evidence = reconcile(
                args.repo_root.resolve(),
                config_path,
                config,
                args.output,
                args.freeze_manifest.resolve(),
            )
    except Exception as error:
        print(f"M5.4 {args.mode} could not complete: {error}", file=sys.stderr)
        return 1
    reference = finalize_bundle(run_dir, event_type, args.mode, evidence)
    print(f"M5.4 {event_type.lower()} {args.mode} evidence: {run_dir}")
    print(f"Evidence reference: {reference}")
    if evidence.failures:
        print(
            f"M5.4 {args.mode} failed at {evidence.failures} gate(s).",
            file=sys.stderr,
        )
        return 1
    print("Technical reconciliation passed; named institutional review remains required.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
