#!/usr/bin/env python3
"""Focused contracts for the local-only M5.5 closeout workflow."""

from __future__ import annotations

import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

MODULE_PATH = Path(__file__).with_name("m5-5-closeout.py")
SPEC = importlib.util.spec_from_file_location("rabbit_m5_5_closeout", MODULE_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

COMMIT = "5ce4a7a1234567890abcdef1234567890abcdef"


class FakeApi:
    readiness: dict = {}

    def __init__(self, base_url: str) -> None:
        MODULE.safe_base_url(base_url)

    def login(self, email: str, password: str, organisation_code: str) -> dict:
        if not email or not password or organisation_code != "INST":
            raise RuntimeError("invalid fake login")
        return {
            "organisationId": "11111111-1111-1111-1111-111111111111",
            "organisationCode": "INST",
            "organisationName": "Pilot Institution",
        }

    def json(self, path: str, **kwargs):
        if path == "/pilot-readiness":
            return self.readiness
        if path == "/operations/readiness":
            return {"overallStatus": "READY", "releaseVersion": "1.0.0"}
        if path == "/feature-flags":
            return [
                {
                    "key": "EXTERNAL_DELIVERY",
                    "enabled": False,
                    "activeForCurrentUser": False,
                }
            ]
        if path == "/audit-events":
            decision = self.readiness.get("decisions", [{}])[0]
            return [
                {
                    "entityId": decision.get("id"),
                    "action": "PILOT_RELEASE_DECISION",
                }
            ]
        raise AssertionError(f"Unexpected fake API path: {path}")


class M55CloseoutTest(unittest.TestCase):
    def setUp(self) -> None:
        FakeApi.readiness = {
            "mandatoryChecksPassed": True,
            "signedOff": False,
            "checks": [
                {
                    "key": "IDENTITY",
                    "mandatory": True,
                    "status": "PASS",
                    "evidenceUrl": "urn:rabbit-evidence:m5-2:identity:abc12345",
                },
                {
                    "key": "LIVE_ASSESSMENT",
                    "mandatory": True,
                    "status": "PASS",
                    "evidenceUrl": "urn:rabbit-evidence:m5-4:live:abc12345",
                },
            ],
            "decisions": [],
        }

    def test_public_targets_are_refused(self) -> None:
        self.assertEqual(MODULE.safe_base_url("http://127.0.0.1"), "http://127.0.0.1")
        self.assertEqual(MODULE.safe_base_url("http://192.168.1.20"), "http://192.168.1.20")
        with self.assertRaisesRegex(ValueError, "public network"):
            MODULE.safe_base_url("https://8.8.8.8")
        with self.assertRaisesRegex(ValueError, "explicit loopback/private IP"):
            MODULE.safe_base_url("https://rabbit.example.com")

    def test_go_prepare_and_finalize_are_checksum_bound(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            config_path, output = self._fixture(root, outcome="GO")
            config = MODULE.parse_env(config_path)
            with patch.object(MODULE, "RabbitApi", FakeApi), patch.object(
                MODULE, "current_commit", return_value=COMMIT
            ), patch.object(
                MODULE,
                "backup_summary",
                return_value={
                    "directoryName": "backup",
                    "createdAt": "2026-08-31T08:00:00Z",
                    "ageSeconds": 60,
                    "releaseCommit": COMMIT,
                    "manifestSha256": "b" * 64,
                },
            ):
                run_dir, evidence = MODULE.prepare(root, config_path, config, output)
                self.assertEqual(evidence.failures, 0)
                reference, digest = MODULE.finalize_checksums(run_dir, "prepare")
                MODULE.write_decision_payload(run_dir, reference, digest)
                payload = json.loads((run_dir / "decision-payload.json").read_text())
                self.assertEqual(payload["outcome"], "GO")
                self.assertEqual(payload["evidenceReference"], reference)
                self.assertEqual(payload["evidenceSha256"], digest)
                self.assertEqual(payload["knownIssueCount"], 1)

                decision = {
                    "id": "dddddddd-dddd-dddd-dddd-dddddddddddd",
                    **payload,
                    "decidedAt": "2026-08-31T09:10:00Z",
                }
                FakeApi.readiness = {
                    **FakeApi.readiness,
                    "signedOff": True,
                    "decisions": [decision],
                }
                final_dir, final_evidence = MODULE.finalize(
                    config, run_dir / "prepare-manifest.json", output
                )
                self.assertEqual(final_evidence.failures, 0)
                final_reference, _ = MODULE.finalize_checksums(final_dir, "final")
                self.assertTrue(final_reference.startswith("urn:rabbit-evidence:m5-5:final:"))

    def test_go_fails_when_a_mandatory_row_is_not_passed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            config_path, output = self._fixture(root, outcome="GO")
            config = MODULE.parse_env(config_path)
            FakeApi.readiness = {
                **FakeApi.readiness,
                "mandatoryChecksPassed": False,
                "checks": [
                    {
                        "key": "LIVE_ASSESSMENT",
                        "mandatory": True,
                        "status": "FAIL",
                        "evidenceUrl": "urn:rabbit-evidence:m5-4:live:abc12345",
                    }
                ],
            }
            with patch.object(MODULE, "RabbitApi", FakeApi), patch.object(
                MODULE, "current_commit", return_value=COMMIT
            ), patch.object(MODULE, "backup_summary", return_value={}):
                _, evidence = MODULE.prepare(root, config_path, config, output)
            self.assertGreater(evidence.failures, 0)
            failed_checks = {
                item["check"] for item in evidence.checks if item["status"] == "FAIL"
            }
            self.assertIn("Outcome matches mandatory gates", failed_checks)

    def test_known_s3_issue_requires_owner_workaround_and_target(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "issues.csv"
            path.write_text(
                "issue_id,severity,status,summary,owner,workaround,target_date,defect_id,closed_at\n"
                "RAB-42,S3,OPEN,Report wording,,Use CSV,2099-12-31,RAB-42,\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "incomplete or invalid"):
                MODULE.known_issue_summary(path)

    def _fixture(self, root: Path, *, outcome: str) -> tuple[Path, Path]:
        output = root / "output"
        output.mkdir()
        live_dir = root / "live"
        live_dir.mkdir()
        live = {
            "eventType": "LIVE",
            "releaseCommit": COMMIT,
            "releaseVersion": "1.0.0",
            "organisationId": "11111111-1111-1111-1111-111111111111",
            "passed": True,
        }
        live_path = live_dir / "reconciliation.json"
        live_path.write_text(json.dumps(live) + "\n", encoding="utf-8")
        live_sha = hashlib.sha256(live_path.read_bytes()).hexdigest()
        (live_dir / "SHA256SUMS").write_text(
            f"{live_sha}  reconciliation.json\n", encoding="utf-8"
        )
        incidents = root / "incidents.csv"
        incidents.write_text(
            "incident_id,severity,status,summary,owner,workaround,defect_id,due_date,opened_at,closed_at\n",
            encoding="utf-8",
        )
        issues = root / "issues.csv"
        issues.write_text(
            "issue_id,severity,status,summary,owner,workaround,target_date,defect_id,closed_at\n"
            "RAB-99,S4,OPEN,Minor wording,UAT Lead,Known wording,2099-12-31,RAB-99,\n",
            encoding="utf-8",
        )
        signed = root / "signed.pdf"
        signed.write_bytes(b"%PDF-1.4\nSigned local acceptance\n%%EOF\n")
        backup = root / "backup"
        backup.mkdir()
        config_path = root / "m5-5.env"
        config_path.write_text(
            "\n".join(
                [
                    "PILOT_M5_5_BASE_URL=http://127.0.0.1",
                    "PILOT_M5_5_ORGANISATION_CODE=INST",
                    "PILOT_M5_5_INSTITUTION_NAME=Pilot Institution",
                    "PILOT_M5_5_RELEASE_VERSION=1.0.0",
                    "PILOT_M5_5_ADMIN_EMAIL=admin@institution.example",
                    "PILOT_M5_5_ADMIN_PASSWORD=protected-password",
                    f"PILOT_M5_5_OUTCOME={outcome}",
                    "PILOT_M5_5_DECISION_REASON=Institution approves the recorded outcome.",
                    "PILOT_M5_5_RETEST_BY_UTC=",
                    f"PILOT_M5_5_LIVE_RECONCILIATION={live_path}",
                    f"PILOT_M5_5_BACKUP_DIRECTORY={backup}",
                    f"PILOT_M5_5_SIGNED_ACCEPTANCE_FILE={signed}",
                    f"PILOT_M5_5_INCIDENT_FILE={incidents}",
                    f"PILOT_M5_5_KNOWN_ISSUES_FILE={issues}",
                    "PILOT_M5_5_AUTHORISED_BY=Institution Sponsor",
                    "PILOT_M5_5_AUTHORISER_TITLE=Principal",
                    "PILOT_M5_5_UAT_LEAD=Institution UAT Lead",
                    "PILOT_M5_5_TECHNICAL_OWNER=Technical Owner",
                    "PILOT_M5_5_SUPPORT_CONTACT=Support Owner / local phone",
                    "PILOT_M5_5_MONITORING_OWNER=Monitoring Owner",
                    "PILOT_M5_5_BACKUP_RESTORE_OWNER=Backup Owner",
                    "PILOT_M5_5_INCIDENT_OWNER=Incident Owner",
                    "PILOT_M5_5_ROLLBACK_OWNER=Rollback Owner",
                    "PILOT_M5_5_DATA_PRIVACY_OWNER=Privacy Owner",
                    "PILOT_M5_5_HANDOVER_RECIPIENT=Institution Operations",
                    "PILOT_M5_5_PRIMARY_DATA_MEDIA=Designated Rabbit computer",
                    "PILOT_M5_5_BACKUP_MEDIA=Encrypted USB backup A",
                    "PILOT_M5_5_LOCAL_DATA_CONFIRMED=yes",
                    "PILOT_M5_5_LOCAL_ONLY_CONFIRMED=yes",
                    "PILOT_M5_5_OWNERSHIP_ACCEPTED=yes",
                    "PILOT_M5_5_SCOPE_FREEZE_ACCEPTED=yes",
                    "",
                ]
            ),
            encoding="utf-8",
        )
        return config_path, output


if __name__ == "__main__":
    unittest.main()
