#!/usr/bin/env python3
"""Prepare and verify Rabbit's local-only Milestone 5.6 release closure."""

from __future__ import annotations

import argparse
import hashlib
import ipaddress
import json
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

LOCAL_REFERENCE = re.compile(
    r"^urn:rabbit-evidence:[A-Za-z0-9][A-Za-z0-9._:-]{7,900}$"
)
COMMIT = re.compile(r"^[0-9a-f]{40}$")
RELEASE_VERSION = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+$")
RELEASE_TAG = re.compile(r"^v[0-9]+\.[0-9]+\.[0-9]+$")
SAFE_GIT_REF = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._/-]{0,200}$")
DEMO_DOMAIN = "@demo.rabbit.local"
SERVICES = ("postgres", "redis", "rabbitmq", "minio", "backend", "frontend", "nginx")
MANDATORY_CHECKS = {
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
    "STAFF_REHEARSAL",
    "LIVE_ASSESSMENT",
    "PILOT_RECONCILIATION",
    "INCIDENT_CLOSURE",
}


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


def write_json(path: Path, value: Any) -> None:
    path.write_text(
        json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


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


def required(config: dict[str, str], key: str) -> str:
    value = config.get(key, "").strip()
    upper = value.upper()
    if not value or upper == "REPLACE_ME" or upper.startswith("REPLACE_WITH_"):
        raise ValueError(f"{key} is missing or still contains a placeholder.")
    return value


def confirmed(config: dict[str, str], key: str) -> bool:
    value = required(config, key).lower()
    if value not in {"yes", "no"}:
        raise ValueError(f"{key} must be yes or no.")
    return value == "yes"


def resolve_path(config_path: Path, value: str) -> Path:
    path = Path(value).expanduser()
    if not path.is_absolute():
        path = config_path.parent / path
    return path.resolve()


def safe_base_url(value: str) -> str:
    parsed = urlparse(value)
    if parsed.scheme not in {"http", "https"}:
        raise ValueError("PILOT_M5_6_BASE_URL must use HTTP or HTTPS.")
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("PILOT_M5_6_BASE_URL cannot contain credentials, query, or fragment.")
    if parsed.path not in {"", "/"}:
        raise ValueError("PILOT_M5_6_BASE_URL cannot contain a path.")
    host = (parsed.hostname or "").lower()
    if host != "localhost":
        try:
            address = ipaddress.ip_address(host)
        except ValueError as error:
            raise ValueError(
                "Use localhost or an explicit loopback/private IP for M5.6."
            ) from error
        if not (address.is_loopback or address.is_private):
            raise ValueError("M5.6 refuses a public network target.")
    return value.rstrip("/")


def run(command: list[str], *, cwd: Path | None = None) -> str:
    return subprocess.run(
        command,
        cwd=cwd,
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def git(repo_root: Path, *args: str) -> str:
    return run(["git", "-C", str(repo_root), *args])


def read_checksum_file(bundle: Path) -> dict[str, str]:
    checksum_path = bundle / "SHA256SUMS"
    if not checksum_path.is_file():
        raise ValueError(f"Evidence bundle has no SHA256SUMS: {bundle}")
    expected: dict[str, str] = {}
    for number, raw in enumerate(
        checksum_path.read_text(encoding="utf-8").splitlines(), 1
    ):
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9._/-]+)", raw)
        if not match:
            raise ValueError(f"Malformed SHA256SUMS row {number} in {bundle.name}.")
        relative = Path(match.group(2))
        if relative.is_absolute() or ".." in relative.parts:
            raise ValueError("Evidence checksum contains an unsafe path.")
        target = (bundle / relative).resolve()
        if bundle.resolve() not in target.parents or not target.is_file():
            raise ValueError(f"Evidence file is missing or unsafe: {relative}")
        actual = sha256_file(target)
        if actual != match.group(1):
            raise ValueError(f"Evidence checksum mismatch: {relative}")
        if relative.as_posix() in expected:
            raise ValueError(f"Duplicate evidence checksum: {relative}")
        expected[relative.as_posix()] = actual
    return expected


def bundled_json(path: Path, checksums: dict[str, str] | None = None) -> dict[str, Any]:
    protected = checksums if checksums is not None else read_checksum_file(path.parent)
    relative = path.relative_to(path.parent).as_posix()
    if relative not in protected:
        raise ValueError(f"{path.name} is not protected by its bundle checksum.")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError(f"{path.name} is not valid JSON.") from error
    if not isinstance(value, dict):
        raise ValueError(f"{path.name} must contain a JSON object.")
    return value


def bundle_identity(bundle: Path, prefix: str) -> tuple[str, str]:
    checksum_path = bundle / "SHA256SUMS"
    digest = hashlib.sha256(checksum_path.read_bytes()).hexdigest()
    reference_path = bundle / "evidence-reference.txt"
    if not reference_path.is_file():
        raise ValueError(f"Evidence bundle has no evidence-reference.txt: {bundle}")
    reference = reference_path.read_text(encoding="utf-8").strip()
    if not LOCAL_REFERENCE.fullmatch(reference):
        raise ValueError("Evidence reference is not a safe local Rabbit URN.")
    if not reference.startswith(prefix) or not reference.endswith(digest):
        raise ValueError("Evidence reference does not match its checksum manifest.")
    return reference, digest


def load_m5_5_evidence(
    prepared_manifest_path: Path,
    final_manifest_path: Path,
    evidence: Evidence,
) -> dict[str, Any]:
    prepared_checksums = read_checksum_file(prepared_manifest_path.parent)
    final_checksums = read_checksum_file(final_manifest_path.parent)
    prepared = bundled_json(prepared_manifest_path, prepared_checksums)
    final = bundled_json(final_manifest_path, final_checksums)
    decision = bundled_json(final_manifest_path.parent / "decision.json", final_checksums)
    readiness = bundled_json(
        final_manifest_path.parent / "readiness-after-decision.json", final_checksums
    )
    final_checks = bundled_json(final_manifest_path.parent / "checks.json", final_checksums)
    prepared_reference, prepared_digest = bundle_identity(
        prepared_manifest_path.parent, "urn:rabbit-evidence:m5-5:prepare:"
    )
    final_reference, final_digest = bundle_identity(
        final_manifest_path.parent, "urn:rabbit-evidence:m5-5:final:"
    )

    evidence.record(
        prepared.get("passed") is True and prepared.get("outcome") == "GO",
        "M5.5 preparation outcome",
        f"Passed={prepared.get('passed')}; outcome={prepared.get('outcome')}.",
    )
    evidence.record(
        final.get("passed") is True
        and final.get("outcome") == "GO"
        and final_checks.get("passed") is True
        and int(final_checks.get("failures") or 0) == 0,
        "M5.5 finalization",
        f"Passed={final.get('passed')}; outcome={final.get('outcome')}; failures={final_checks.get('failures')}.",
    )
    evidence.record(
        final.get("preparedEvidenceReference") == prepared_reference
        and final.get("preparedEvidenceSha256") == prepared_digest,
        "M5.5 preparation/final binding",
        "Final evidence identifies the exact protected preparation bundle.",
    )
    evidence.record(
        decision.get("id") == final.get("decisionId")
        and decision.get("outcome") == "GO"
        and decision.get("evidenceReference") == prepared_reference
        and decision.get("evidenceSha256") == prepared_digest,
        "Immutable Go decision",
        "The final bundle contains the exact Go decision for its protected preparation.",
    )
    evidence.record(
        readiness.get("signedOff") is True
        and readiness.get("mandatoryChecksPassed") is True,
        "Go lock and mandatory gate",
        f"signedOff={readiness.get('signedOff')}; mandatoryChecksPassed={readiness.get('mandatoryChecksPassed')}.",
    )
    checks = {str(item.get("key")): item for item in readiness.get("checks", [])}
    missing = sorted(MANDATORY_CHECKS - checks.keys())
    nonpassing = sorted(
        key
        for key in MANDATORY_CHECKS
        if key in checks
        and (
            checks[key].get("mandatory") is not True
            or checks[key].get("status") != "PASS"
            or not LOCAL_REFERENCE.fullmatch(str(checks[key].get("evidenceUrl") or ""))
        )
    )
    evidence.record(
        not missing and not nonpassing,
        "Complete M5.1-M5.4 evidence set",
        "All mandatory checks passed with local Rabbit evidence."
        if not missing and not nonpassing
        else f"Missing={missing}; non-passing/invalid={nonpassing}.",
    )
    evidence.record(
        all(
            prepared.get(key) is expected
            for key, expected in {
                "localDataConfirmed": True,
                "localOnlyConfirmed": True,
                "ownershipAccepted": True,
                "scopeFreezeAccepted": True,
                "cloudRuntimeUsed": False,
                "publicEndpointUsed": False,
                "credentialsRecorded": False,
            }.items()
        ),
        "M5.5 architecture and handover attestations",
        "Local data, ownership, scope freeze, and no-cloud attestations are locked.",
    )
    evidence.record(
        final.get("cloudRuntimeUsed") is False
        and final.get("publicEndpointUsed") is False
        and final.get("credentialsRecorded") is False,
        "M5.5 final local-only boundary",
        "Finalization records no cloud runtime, public endpoint, or credentials.",
    )
    return {
        "prepared": prepared,
        "final": final,
        "decision": decision,
        "readiness": readiness,
        "preparedReference": prepared_reference,
        "preparedDigest": prepared_digest,
        "finalReference": final_reference,
        "finalDigest": final_digest,
    }


class RabbitApi:
    def __init__(self, base_url: str) -> None:
        self.root = f"{safe_base_url(base_url)}/gateway/backend"
        self.token: str | None = None

    def json(
        self,
        path: str,
        *,
        method: str = "GET",
        payload: dict[str, Any] | None = None,
    ) -> Any:
        headers = {"Accept": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        body = None
        if payload is not None:
            headers["Content-Type"] = "application/json"
            body = json.dumps(payload).encode("utf-8")
        try:
            with urlopen(
                Request(
                    f"{self.root}{path}",
                    data=body,
                    headers=headers,
                    method=method,
                ),
                timeout=30,
            ) as response:
                status, raw = response.status, response.read()
        except HTTPError as error:
            status, raw = error.code, error.read()
        except URLError as error:
            raise RuntimeError(f"Local Rabbit API is unavailable: {error.reason}") from error
        if status != 200:
            raise RuntimeError(f"Rabbit API {path} returned HTTP {status}.")
        try:
            return json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise RuntimeError(f"Rabbit API {path} did not return valid JSON.") from error

    def login(self, email: str, password: str, organisation_code: str) -> dict[str, Any]:
        if email.lower().endswith(DEMO_DOMAIN):
            raise ValueError("M5.6 cannot use a seeded demo administrator.")
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
                raise RuntimeError("Configured organisation is unavailable to the M5.6 Admin.")
            response = self.json(
                "/auth/select-organisation",
                method="POST",
                payload={
                    "selectionToken": response.get("selectionToken"),
                    "organisationId": choice.get("id"),
                },
            )
        if response.get("role") != "ORG_ADMIN" or not response.get("accessToken"):
            raise RuntimeError("M5.6 requires an active Organisation Admin.")
        self.token = str(response["accessToken"])
        me = self.json("/auth/me")
        if me.get("organisationCode") != organisation_code:
            raise RuntimeError("Authenticated organisation does not match M5.6 config.")
        return me


def validate_git_prepare(
    repo_root: Path,
    main_ref: str,
    release_tag: str,
    expected_commit: str,
    evidence: Evidence,
) -> dict[str, Any]:
    if not SAFE_GIT_REF.fullmatch(main_ref):
        raise ValueError("PILOT_M5_6_MAIN_REF is not a safe Git reference.")
    head = git(repo_root, "rev-parse", "HEAD")
    branch = git(repo_root, "branch", "--show-current")
    clean = not git(repo_root, "status", "--porcelain")
    evidence.record(COMMIT.fullmatch(head) is not None, "Exact Git commit", f"HEAD={head}.")
    evidence.record(head == expected_commit, "Go decision commit", "HEAD matches the immutable Go decision.")
    evidence.record(clean, "Clean release worktree", f"Branch={branch}; clean={clean}.")
    try:
        base = git(repo_root, "rev-parse", "--verify", main_ref)
    except subprocess.CalledProcessError as error:
        raise ValueError(f"Configured main reference is unavailable: {main_ref}") from error
    ancestor = subprocess.run(
        ["git", "-C", str(repo_root), "merge-base", "--is-ancestor", base, head],
        check=False,
    ).returncode == 0
    evidence.record(
        ancestor,
        "Fast-forward main ancestry",
        f"{main_ref}={base}; release={head}; fast-forward={ancestor}.",
    )
    tag_exists = subprocess.run(
        ["git", "-C", str(repo_root), "rev-parse", "--verify", "--quiet", f"refs/tags/{release_tag}"],
        check=False,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    ).returncode == 0
    evidence.record(
        not tag_exists,
        "Release tag not pre-created",
        f"{release_tag} remains absent until the protected prepare bundle is reviewed.",
    )
    return {"head": head, "branch": branch, "mainRef": main_ref, "mainCommit": base}


def inspect_runtime(
    repo_root: Path,
    env_file: Path,
    config: dict[str, str],
    expected_commit: str,
    expected_version: str,
    decision: dict[str, Any],
    organisation_id: str,
    evidence: Evidence,
) -> dict[str, Any]:
    environment = parse_env(env_file)
    evidence.record(
        environment.get("RABBIT_RELEASE_COMMIT") == expected_commit,
        "Runtime commit configuration",
        "Protected local environment names the exact Go commit.",
    )
    evidence.record(
        environment.get("RABBIT_RELEASE_VERSION") == expected_version,
        "Runtime version configuration",
        f"Configured={environment.get('RABBIT_RELEASE_VERSION')}; expected={expected_version}.",
    )
    compose = [
        "docker",
        "compose",
        "--env-file",
        str(env_file),
        "-f",
        str(repo_root / "docker-compose.yml"),
        "-f",
        str(repo_root / "infra/pilot/compose.local-pilot.yml"),
    ]
    running = set(run([*compose, "ps", "--services", "--status", "running"]).splitlines())
    missing = sorted(set(SERVICES) - running)
    evidence.record(not missing, "Seven-service local runtime", "All services run locally." if not missing else f"Missing={missing}.")
    images: list[dict[str, Any]] = []
    for service in SERVICES:
        container_id = run([*compose, "ps", "-q", service])
        if not container_id:
            continue
        inspected = json.loads(run(["docker", "inspect", container_id]))[0]
        image_id = inspected.get("Image")
        image_details = json.loads(run(["docker", "image", "inspect", str(image_id)]))[0]
        labels = inspected.get("Config", {}).get("Labels") or {}
        item = {
            "service": service,
            "imageReference": inspected.get("Config", {}).get("Image"),
            "imageId": image_id,
            "sizeBytes": int(image_details.get("Size") or 0),
            "releaseVersionLabel": labels.get("org.opencontainers.image.version"),
            "releaseCommitLabel": labels.get("org.opencontainers.image.revision"),
        }
        images.append(item)
        if service in {"backend", "frontend"}:
            evidence.record(
                item["releaseCommitLabel"] == expected_commit
                and item["releaseVersionLabel"] == expected_version,
                f"{service.title()} image provenance",
                f"revision={item['releaseCommitLabel']}; version={item['releaseVersionLabel']}.",
            )

    api = RabbitApi(required(config, "PILOT_M5_6_BASE_URL"))
    me = api.login(
        required(config, "PILOT_M5_6_ADMIN_EMAIL"),
        required(config, "PILOT_M5_6_ADMIN_PASSWORD"),
        required(config, "PILOT_M5_6_ORGANISATION_CODE"),
    )
    readiness = api.json("/pilot-readiness")
    live_decision = next(
        (item for item in readiness.get("decisions", []) if item.get("id") == decision.get("id")),
        None,
    )
    evidence.record(
        me.get("organisationId") == organisation_id
        and readiness.get("signedOff") is True
        and live_decision is not None
        and live_decision.get("outcome") == "GO"
        and live_decision.get("releaseCommit") == expected_commit,
        "Live immutable Go state",
        "The running local tenant still contains the exact locked Go decision.",
    )
    operations = api.json("/operations/readiness")
    evidence.record(
        operations.get("overallStatus") == "READY"
        and operations.get("releaseVersion") == expected_version,
        "Live operations readiness",
        f"status={operations.get('overallStatus')}; version={operations.get('releaseVersion')}.",
    )
    flags = {str(item.get("key")): item for item in api.json("/feature-flags")}
    external = flags.get("EXTERNAL_DELIVERY", {})
    evidence.record(
        external.get("enabled") is False
        and external.get("activeForCurrentUser") is False,
        "External delivery remains disabled",
        "Provider-backed email/SMS is still disabled for the local release.",
    )
    return {
        "organisationId": me.get("organisationId"),
        "operations": {
            "overallStatus": operations.get("overallStatus"),
            "releaseVersion": operations.get("releaseVersion"),
        },
        "services": sorted(running),
        "images": images,
    }


def prepare_run_dir(output: Path, mode: str) -> Path:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    run_dir = output.resolve() / f"rabbit-m5.6-{mode}-{stamp}"
    run_dir.mkdir(parents=True, exist_ok=False, mode=0o700)
    return run_dir


def migration_checksums(repo_root: Path, target: Path) -> None:
    lines = []
    for path in sorted((repo_root / "backend/src/main/resources/db/migration").glob("V*.sql")):
        lines.append(f"{sha256_file(path)}  {path.name}")
    target.write_text("\n".join(lines) + "\n", encoding="utf-8")


def export_release_artifacts(
    repo_root: Path,
    run_dir: Path,
    version: str,
    expected_commit: str,
    runtime: dict[str, Any],
) -> None:
    subprocess.run(
        [
            "git",
            "-C",
            str(repo_root),
            "archive",
            "--format=tar.gz",
            f"--prefix=Rabbit-{version}/",
            f"--output={run_dir / f'Rabbit-{version}-source.tar.gz'}",
            expected_commit,
        ],
        check=True,
    )
    subprocess.run(
        [
            "git",
            "-C",
            str(repo_root),
            "bundle",
            "create",
            str(run_dir / f"Rabbit-{version}.bundle"),
            "HEAD",
        ],
        check=True,
    )
    bundle_heads = git(
        repo_root, "bundle", "list-heads", str(run_dir / f"Rabbit-{version}.bundle")
    )
    if not any(line.startswith(f"{expected_commit} ") for line in bundle_heads.splitlines()):
        raise RuntimeError("Exported Git bundle does not identify the Go-approved commit.")
    image_references = sorted(
        {
            str(item.get("imageReference"))
            for item in runtime.get("images", [])
            if item.get("imageReference")
        }
    )
    if len(image_references) != len(SERVICES):
        raise RuntimeError("The local runtime image set is incomplete or ambiguous.")
    expected_image_ids = {
        str(item["imageReference"]): str(item["imageId"])
        for item in runtime.get("images", [])
    }
    for reference, expected_id in expected_image_ids.items():
        actual_id = run(
            ["docker", "image", "inspect", "--format", "{{.Id}}", reference]
        )
        if actual_id != expected_id:
            raise RuntimeError(f"Runtime image changed before export: {reference}")
    subprocess.run(
        [
            "docker",
            "image",
            "save",
            "--output",
            str(run_dir / f"Rabbit-{version}-runtime-images.tar"),
            *image_references,
        ],
        check=True,
    )
    if git(repo_root, "rev-parse", "HEAD") != expected_commit \
            or git(repo_root, "status", "--porcelain"):
        raise RuntimeError("Git state changed during the protected release export.")
    for reference, expected_id in expected_image_ids.items():
        actual_id = run(
            ["docker", "image", "inspect", "--format", "{{.Id}}", reference]
        )
        if actual_id != expected_id:
            raise RuntimeError(f"Runtime image changed during export: {reference}")
    migration_checksums(repo_root, run_dir / "migration-SHA256SUMS")


def seal_bundle(run_dir: Path, mode: str) -> tuple[str, str]:
    files = sorted(
        path
        for path in run_dir.rglob("*")
        if path.is_file() and path.name not in {"SHA256SUMS", "evidence-reference.txt"}
    )
    lines = [
        f"{sha256_file(path)}  {path.relative_to(run_dir).as_posix()}"
        for path in files
    ]
    checksum_path = run_dir / "SHA256SUMS"
    checksum_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    digest = hashlib.sha256(checksum_path.read_bytes()).hexdigest()
    reference = f"urn:rabbit-evidence:m5-6:{mode}:{run_dir.name}:{digest}"
    (run_dir / "evidence-reference.txt").write_text(reference + "\n", encoding="utf-8")
    return reference, digest


def prepare(
    repo_root: Path,
    env_file: Path,
    config_path: Path,
    config: dict[str, str],
) -> tuple[Path, Evidence]:
    evidence = Evidence()
    version = required(config, "PILOT_M5_6_RELEASE_VERSION")
    tag = required(config, "PILOT_M5_6_RELEASE_TAG")
    main_ref = required(config, "PILOT_M5_6_MAIN_REF")
    if not RELEASE_VERSION.fullmatch(version) or tag != f"v{version}" or not RELEASE_TAG.fullmatch(tag):
        raise ValueError("M5.6 release version/tag must use matching X.Y.Z and vX.Y.Z values.")
    prepared_path = resolve_path(
        config_path, required(config, "PILOT_M5_6_M5_5_PREPARED_MANIFEST")
    )
    final_path = resolve_path(
        config_path, required(config, "PILOT_M5_6_M5_5_FINAL_MANIFEST")
    )
    m55 = load_m5_5_evidence(prepared_path, final_path, evidence)
    decision = m55["decision"]
    commit = str(decision.get("releaseCommit") or "").lower()
    evidence.record(
        COMMIT.fullmatch(commit) is not None
        and decision.get("releaseVersion") == version
        and m55["prepared"].get("releaseCommit") == commit
        and m55["prepared"].get("releaseVersion") == version,
        "Release identity",
        f"version={version}; commit={commit}.",
    )
    git_state = validate_git_prepare(repo_root, main_ref, tag, commit, evidence)
    runtime = inspect_runtime(
        repo_root,
        env_file,
        config,
        commit,
        version,
        decision,
        str(m55["final"].get("organisationId") or ""),
        evidence,
    )
    local_only = confirmed(config, "PILOT_M5_6_LOCAL_ONLY_CONFIRMED")
    export_images = confirmed(config, "PILOT_M5_6_EXPORT_RUNTIME_IMAGES")
    release_owner = required(config, "PILOT_M5_6_RELEASE_OWNER")
    verification_owner = required(config, "PILOT_M5_6_VERIFICATION_OWNER")
    media = required(config, "PILOT_M5_6_RELEASE_MEDIA")
    evidence.record(
        local_only and export_images,
        "Local release distribution",
        "Complete runtime images will be exported to approved local media without a registry.",
    )
    evidence.record(
        all(len(value) >= 3 for value in (release_owner, verification_owner, media)),
        "Named release handover",
        "Release owner, independent verifier, and local media are named.",
    )
    output = resolve_path(config_path, required(config, "PILOT_M5_6_RELEASE_OUTPUT"))
    output.mkdir(parents=True, exist_ok=True, mode=0o700)
    if output == repo_root or repo_root in output.parents:
        raise ValueError("M5.6 release output must be outside the Git worktree.")
    unique_images = {
        str(item.get("imageId")): int(item.get("sizeBytes") or 0)
        for item in runtime.get("images", [])
        if item.get("imageId")
    }
    estimated_bytes = sum(unique_images.values()) + 512 * 1024 * 1024
    free_bytes = shutil.disk_usage(output).free
    evidence.record(
        free_bytes >= estimated_bytes,
        "Local release-media capacity",
        f"Free={free_bytes} bytes; conservative requirement={estimated_bytes} bytes.",
    )
    run_dir = prepare_run_dir(output, "prepare")
    write_json(run_dir / "runtime-snapshot.json", runtime)
    write_json(
        run_dir / "decision-summary.json",
        {
            "id": decision.get("id"),
            "outcome": decision.get("outcome"),
            "releaseVersion": decision.get("releaseVersion"),
            "releaseCommit": decision.get("releaseCommit"),
            "institutionName": decision.get("institutionName"),
            "knownIssueCount": decision.get("knownIssueCount"),
            "decidedAt": decision.get("decidedAt"),
            "authorisedBy": decision.get("authorisedBy"),
            "technicalOwner": decision.get("technicalOwner"),
            "supportContact": decision.get("supportContact"),
            "rollbackOwner": decision.get("rollbackOwner"),
        },
    )
    if evidence.failures == 0:
        try:
            export_release_artifacts(repo_root, run_dir, version, commit, runtime)
        except (OSError, subprocess.CalledProcessError, RuntimeError) as error:
            for partial in (
                run_dir / f"Rabbit-{version}-source.tar.gz",
                run_dir / f"Rabbit-{version}.bundle",
                run_dir / f"Rabbit-{version}-runtime-images.tar",
                run_dir / "migration-SHA256SUMS",
            ):
                partial.unlink(missing_ok=True)
            evidence.record(
                False,
                "Complete local release export",
                f"Source/runtime image export failed: {error}.",
            )
        else:
            evidence.record(
                True,
                "Complete local release export",
                "Source, Git history, migrations, and all seven images were exported locally.",
            )
    command_text = f"""Rabbit M5.6 manual release commands — DO NOT RUN UNTIL THIS BUNDLE IS REVIEWED

git fetch origin --prune --tags
git switch main
git merge --ff-only {commit}
git tag -a {tag} {commit} -m \"Rabbit AiP Release {version}\"
git push origin main
git push origin {tag}

After the verification-only tag workflow is green, fetch origin/main and the tag,
then run make pilot-m5-6-verify-tag with this release-manifest.json.
No command above publishes a container image or deploys Rabbit to a cloud runtime.
    """
    (run_dir / "manual-release-commands.txt").write_text(command_text, encoding="utf-8")
    write_json(
        run_dir / "checks.json",
        {"checks": evidence.checks, "failures": evidence.failures, "passed": evidence.failures == 0},
    )
    manifest = {
        "evidenceType": "Rabbit M5.6 Release 1.0 closure preparation",
        "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "releaseVersion": version,
        "releaseTag": tag,
        "releaseCommit": commit,
        "releaseBranch": git_state["branch"],
        "mainRef": main_ref,
        "mainCommitBeforeRelease": git_state["mainCommit"],
        "organisationId": m55["final"].get("organisationId"),
        "institutionName": decision.get("institutionName"),
        "decisionId": decision.get("id"),
        "m5_5PreparedEvidenceReference": m55["preparedReference"],
        "m5_5PreparedEvidenceSha256": m55["preparedDigest"],
        "m5_5FinalEvidenceReference": m55["finalReference"],
        "m5_5FinalEvidenceSha256": m55["finalDigest"],
        "releaseOwner": release_owner,
        "verificationOwner": verification_owner,
        "releaseMedia": media,
        "runtimeImagesExported": evidence.failures == 0 and export_images,
        "registryUsed": False,
        "cloudRuntimeUsed": False,
        "publicEndpointUsed": False,
        "credentialsRecorded": False,
        "mainUpdated": False,
        "tagCreated": False,
        "passed": evidence.failures == 0,
    }
    write_json(run_dir / "release-manifest.json", manifest)
    return run_dir, evidence


def validate_git_final(
    repo_root: Path,
    manifest: dict[str, Any],
    evidence: Evidence,
) -> dict[str, Any]:
    commit = str(manifest.get("releaseCommit") or "")
    tag = str(manifest.get("releaseTag") or "")
    main_ref = str(manifest.get("mainRef") or "")
    head = git(repo_root, "rev-parse", "HEAD")
    branch = git(repo_root, "branch", "--show-current")
    local_main = git(repo_root, "rev-parse", "refs/heads/main")
    remote_main = git(repo_root, "rev-parse", "--verify", main_ref)
    tag_type = git(repo_root, "cat-file", "-t", f"refs/tags/{tag}")
    tag_commit = git(repo_root, "rev-parse", f"refs/tags/{tag}^{{}}")
    clean = not git(repo_root, "status", "--porcelain")
    evidence.record(
        branch == "main" and clean,
        "Clean main checkout",
        f"branch={branch}; clean={clean}.",
    )
    evidence.record(
        head == commit and local_main == commit and remote_main == commit,
        "Fast-forwarded main",
        f"HEAD={head}; local main={local_main}; {main_ref}={remote_main}.",
    )
    evidence.record(
        tag_type == "tag" and tag_commit == commit,
        "Annotated Release 1.0 tag",
        f"tag={tag}; type={tag_type}; commit={tag_commit}.",
    )
    return {
        "branch": branch,
        "commit": commit,
        "tag": tag,
        "tagType": tag_type,
        "tagCommit": tag_commit,
        "mainRef": main_ref,
        "mainCommit": remote_main,
    }


def verify_tag(
    repo_root: Path,
    config_path: Path,
    config: dict[str, str],
    prepared_manifest_path: Path,
) -> tuple[Path, Evidence]:
    evidence = Evidence()
    prepared_checksums = read_checksum_file(prepared_manifest_path.parent)
    prepared = bundled_json(prepared_manifest_path, prepared_checksums)
    prepared_checks = bundled_json(
        prepared_manifest_path.parent / "checks.json", prepared_checksums
    )
    prepared_reference, prepared_digest = bundle_identity(
        prepared_manifest_path.parent, "urn:rabbit-evidence:m5-6:prepare:"
    )
    evidence.record(
        prepared.get("passed") is True
        and prepared.get("registryUsed") is False
        and prepared.get("cloudRuntimeUsed") is False
        and prepared.get("publicEndpointUsed") is False
        and prepared.get("runtimeImagesExported") is True,
        "Protected M5.6 preparation",
        "The checksummed preparation passed with complete local images and no cloud distribution.",
    )
    version = str(prepared.get("releaseVersion") or "")
    required_artifacts = {
        f"Rabbit-{version}-source.tar.gz",
        f"Rabbit-{version}.bundle",
        f"Rabbit-{version}-runtime-images.tar",
        "migration-SHA256SUMS",
        "runtime-snapshot.json",
        "decision-summary.json",
        "manual-release-commands.txt",
        "checks.json",
        "release-manifest.json",
    }
    missing_artifacts = sorted(required_artifacts - prepared_checksums.keys())
    evidence.record(
        prepared_checks.get("passed") is True
        and int(prepared_checks.get("failures") or 0) == 0
        and not missing_artifacts,
        "Complete protected release package",
        "All mandatory local release artifacts are checksummed."
        if not missing_artifacts
        else f"Missing checksummed artifacts: {missing_artifacts}.",
    )
    evidence.record(
        prepared.get("releaseVersion") == required(config, "PILOT_M5_6_RELEASE_VERSION")
        and prepared.get("releaseTag") == required(config, "PILOT_M5_6_RELEASE_TAG")
        and prepared.get("mainRef") == required(config, "PILOT_M5_6_MAIN_REF"),
        "Final release inputs",
        "Version, tag, and main reference match the protected M5.6 configuration.",
    )
    git_state = validate_git_final(repo_root, prepared, evidence)
    output = resolve_path(config_path, required(config, "PILOT_M5_6_RELEASE_OUTPUT"))
    run_dir = prepare_run_dir(output, "final")
    write_json(
        run_dir / "checks.json",
        {"checks": evidence.checks, "failures": evidence.failures, "passed": evidence.failures == 0},
    )
    write_json(run_dir / "tag-verification.json", git_state)
    write_json(
        run_dir / "final-manifest.json",
        {
            "evidenceType": "Rabbit M5.6 Release 1.0 closure finalization",
            "generatedAtUtc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "preparedEvidenceReference": prepared_reference,
            "preparedEvidenceSha256": prepared_digest,
            "releaseVersion": prepared.get("releaseVersion"),
            "releaseTag": prepared.get("releaseTag"),
            "releaseCommit": prepared.get("releaseCommit"),
            "decisionId": prepared.get("decisionId"),
            "releaseOwner": prepared.get("releaseOwner"),
            "verificationOwner": prepared.get("verificationOwner"),
            "registryUsed": False,
            "cloudRuntimeUsed": False,
            "publicEndpointUsed": False,
            "credentialsRecorded": False,
            "mainUpdated": evidence.failures == 0,
            "tagCreated": evidence.failures == 0,
            "passed": evidence.failures == 0,
        },
    )
    return run_dir, evidence


def main() -> int:
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("prepare", "verify-tag"))
    parser.add_argument("--repo-root", required=True, type=Path)
    parser.add_argument("--env-file", required=True, type=Path)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--prepared-manifest", type=Path)
    args = parser.parse_args()
    repo_root = args.repo_root.resolve()
    config_path = args.config.resolve()
    try:
        config = parse_env(config_path)
        if args.mode == "prepare":
            if args.prepared_manifest is not None:
                raise ValueError("--prepared-manifest is valid only for verify-tag.")
            run_dir, evidence = prepare(
                repo_root, args.env_file.resolve(), config_path, config
            )
        else:
            if args.prepared_manifest is None:
                raise ValueError("--prepared-manifest is required for verify-tag.")
            run_dir, evidence = verify_tag(
                repo_root, config_path, config, args.prepared_manifest.resolve()
            )
    except Exception as error:
        print(f"M5.6 {args.mode} could not complete: {error}", file=sys.stderr)
        return 1
    reference, digest = seal_bundle(run_dir, "prepare" if args.mode == "prepare" else "final")
    print(f"M5.6 {args.mode} evidence: {run_dir}")
    print(f"Evidence reference: {reference}")
    print(f"Evidence SHA-256: {digest}")
    if evidence.failures:
        print(f"M5.6 {args.mode} failed {evidence.failures} check(s).", file=sys.stderr)
        return 1
    print(f"M5.6 {args.mode} checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
