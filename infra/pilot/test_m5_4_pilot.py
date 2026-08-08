#!/usr/bin/env python3
"""Focused contracts for the local M5.4 pilot evidence runner."""

from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import threading
import unittest
from copy import deepcopy
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

MODULE_PATH = Path(__file__).with_name("m5-4-pilot.py")
SPEC = importlib.util.spec_from_file_location("rabbit_m5_4_pilot", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Could not load M5.4 pilot module.")
pilot = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = pilot
SPEC.loader.exec_module(pilot)


class RabbitHandler(BaseHTTPRequestHandler):
    assessment = {
        "id": "assessment-1",
        "title": "M5.4 rehearsal",
        "code": "M54-REHEARSAL",
        "type": "PRACTICE",
        "subjectId": "subject-1",
        "subjectIds": ["subject-1"],
        "durationMinutes": 2,
        "status": "SCHEDULED",
        "totalMarks": 2,
        "questionCount": 2,
        "shuffleQuestions": True,
        "shuffleOptions": True,
        "partialMarking": False,
        "attemptsAllowed": 1,
        "startAt": (datetime.now(timezone.utc) + timedelta(hours=2)).isoformat(),
        "endAt": (datetime.now(timezone.utc) + timedelta(hours=3)).isoformat(),
        "questionIds": ["question-1", "question-2"],
        "eligibleSectionIds": ["section-1"],
        "createdAt": "2026-08-01T00:00:00Z",
        "updatedAt": "2026-08-01T01:00:00Z",
    }

    def log_message(self, _format: str, *_args: object) -> None:
        return

    def _json(self, value: object, status: int = 200) -> None:
        body = json.dumps(value).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler contract
        length = int(self.headers.get("Content-Length", "0"))
        if length:
            self.rfile.read(length)
        if self.path.endswith("/auth/login"):
            self._json(
                {
                    "requiresOrganisationSelection": False,
                    "accessToken": "local-test-token",
                    "role": "ORG_ADMIN",
                }
            )
            return
        self._json({"message": "not found"}, 404)

    def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler contract
        parsed = urlparse(self.path)
        path = parsed.path.removeprefix("/gateway/backend")
        query = parse_qs(parsed.query)
        if path == "/auth/me":
            self._json(
                {
                    "userId": "admin-1",
                    "email": "admin@institution.invalid",
                    "organisationId": "organisation-1",
                    "organisationCode": "INST",
                    "organisationName": "Institution One",
                    "role": "ORG_ADMIN",
                }
            )
        elif path == "/users":
            self._json(
                [
                    self._user("admin-1", "admin@institution.invalid", "ORG_ADMIN"),
                    self._user("teacher-1", "teacher@institution.invalid", "FACULTY"),
                    self._user("reviewer-1", "reviewer@institution.invalid", "REVIEWER"),
                    self._user("student-1", "student1@institution.invalid", "STUDENT"),
                    self._user("student-2", "student2@institution.invalid", "STUDENT"),
                ]
            )
        elif path == "/pilot-readiness":
            checks = [
                {"key": key, "mandatory": True, "status": "PASS"}
                for key in sorted(pilot.M5_4_PREREQUISITES)
            ]
            checks.extend(
                {"key": key, "mandatory": True, "status": "NOT_RUN"}
                for key in sorted(pilot.M5_4_KEYS)
            )
            self._json({"signedOff": False, "checks": checks})
        elif path == "/operations/readiness":
            self._json(
                {
                    "overallStatus": "READY",
                    "releaseVersion": "1.0.0",
                    "workflows": {
                        "activeAssessmentAttempts": 0,
                        "pendingResultPublications": 0,
                    },
                }
            )
        elif path == "/feature-flags":
            self._json(
                [
                    {
                        "key": key,
                        "enabled": enabled,
                        "rolloutPercentage": 100 if enabled else 0,
                        "activeForCurrentUser": enabled,
                    }
                    for key, enabled in (
                        ("PILOT_MODE", True),
                        ("PDF_EXPORTS", True),
                        ("EXCEL_EXPORTS", True),
                        ("EXTERNAL_DELIVERY", False),
                    )
                ]
            )
        elif path == "/assessments/assessment-1":
            self._json(self.assessment)
        elif path == "/assessments/assessment-1/reviews":
            self._json(
                [{"decision": "APPROVE", "createdAt": "2026-08-01T01:00:00Z"}]
            )
        elif path in {"/questions/question-1", "/questions/question-2"}:
            question_id = path.rsplit("/", 1)[-1]
            self._json(
                {
                    "id": question_id,
                    "code": question_id.upper(),
                    "status": "APPROVED",
                    "stem": f"Stem for {question_id}",
                    "options": [{"id": f"{question_id}-option", "correct": True}],
                }
            )
        elif path == "/evaluation/assessments/assessment-1/monitor":
            self._json(
                {
                    "assessmentId": "assessment-1",
                    "totalAttempts": 1,
                    "inProgress": 0,
                    "submitted": 1,
                    "autoSubmitted": 0,
                    "attempts": [
                        {
                            "attemptId": "attempt-1",
                            "studentUserId": "student-1",
                            "attemptStatus": "SUBMITTED",
                        }
                    ],
                }
            )
        elif path == "/evaluation/assessments/assessment-1/results":
            self._json(
                {
                    "evaluatedCount": 1,
                    "pendingPublicationCount": 0,
                    "publishedCount": 1,
                    "results": [
                        {
                            "attemptId": "attempt-1",
                            "studentUserId": "student-1",
                            "publicationStatus": "PUBLISHED",
                        }
                    ],
                }
            )
        elif path == "/reports/assessments/assessment-1":
            self._json(
                {
                    "assessmentId": "assessment-1",
                    "submissions": 1,
                    "studentResults": [{"attemptId": "attempt-1"}],
                }
            )
        elif path == "/reports/assessments/assessment-1/export":
            self._binary(b"student,assessment,score\nStudent One,Rehearsal,2\n", "text/csv")
        elif path == "/reports/assessments/assessment-1/export.pdf":
            self._binary(b"%PDF-1.7\nlocal-test-pdf-evidence", "application/pdf")
        elif path == "/reports/assessments/assessment-1/export.xlsx":
            self._binary(b"PK\x03\x04local-test-xlsx-evidence", "application/zip")
        elif path == "/audit-events" and query.get("module") == ["DEL"]:
            self._json(
                [
                    {
                        "action": "SUBMIT",
                        "entityType": "AssessmentAttempt",
                        "entityId": "attempt-1",
                    }
                ]
            )
        elif path == "/audit-events" and query.get("module") == ["EVL"]:
            self._json(
                [
                    {
                        "action": "PUBLISH_RESULT",
                        "entityType": "AssessmentAttempt",
                        "entityId": "attempt-1",
                    },
                    {
                        "action": "PUBLISH_RESULTS",
                        "entityType": "Assessment",
                        "entityId": "assessment-1",
                    },
                ]
            )
        elif path == "/audit-events" and query.get("module") == ["RPT"]:
            self._json(
                [
                    {
                        "action": action,
                        "entityType": "Assessment",
                        "entityId": "assessment-1",
                    }
                    for action in (
                        "EXPORT_ASSESSMENT_CSV",
                        "EXPORT_ASSESSMENT_PDF",
                        "EXPORT_ASSESSMENT_XLSX",
                    )
                ]
            )
        else:
            self._json({"message": f"not found: {path}"}, 404)

    @staticmethod
    def _user(user_id: str, email: str, role: str) -> dict[str, object]:
        return {
            "userId": user_id,
            "membershipId": f"membership-{user_id}",
            "email": email,
            "firstName": role.title(),
            "lastName": "User",
            "role": role,
            "status": "ACTIVE",
            "sectionId": "section-1" if role == "STUDENT" else None,
        }

    def _binary(self, value: bytes, content_type: str) -> None:
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(value)))
        self.end_headers()
        self.wfile.write(value)


class M54PilotTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), RabbitHandler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls) -> None:
        cls.server.shutdown()
        cls.thread.join(timeout=5)
        cls.server.server_close()

    def test_private_target_freeze_and_reconciliation_contract(self) -> None:
        repo_root = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            roster = root / ".pilot-m5-4-roster.csv"
            incidents = root / ".pilot-m5-4-incidents.csv"
            backup = root / "rabbit-test-backup"
            config_path = root / ".env.pilot-m5-4"
            output = root / "evidence"
            roster.write_text(
                "email,attended,absence_reason\n"
                "student1@institution.invalid,,\n"
                "student2@institution.invalid,,\n",
                encoding="utf-8",
            )
            incidents.write_text(
                "incident_id,severity,status,summary,owner,workaround,defect_id,"
                "due_date,opened_at,closed_at\n",
                encoding="utf-8",
            )
            backup.mkdir()
            backup.joinpath("manifest.txt").write_text(
                "backup_format_version=2\n"
                "created_at=local-test\n"
                f"created_at_epoch={int(datetime.now(timezone.utc).timestamp())}\n"
                f"release_commit={pilot.current_commit(repo_root)}\n"
                "worktree_state=clean\n"
                "quiesced=true\n",
                encoding="utf-8",
            )
            config_path.write_text(
                self._config(roster, incidents, backup), encoding="utf-8"
            )
            config = pilot.parse_env(config_path)
            freeze_dir, freeze_evidence = pilot.freeze(
                repo_root, config_path, config, output
            )
            freeze_reference = pilot.finalize_bundle(
                freeze_dir, "REHEARSAL", "freeze", freeze_evidence
            )
            self.assertEqual(0, freeze_evidence.failures)
            self.assertTrue(freeze_reference.startswith("urn:rabbit-evidence:m5-4:"))

            roster.write_text(
                "email,attended,absence_reason\n"
                "student1@institution.invalid,yes,\n"
                "student2@institution.invalid,no,Approved absence\n",
                encoding="utf-8",
            )
            reconcile_dir, reconcile_evidence = pilot.reconcile(
                repo_root,
                config_path,
                config,
                output,
                freeze_dir / "freeze-manifest.json",
            )
            reconcile_reference = pilot.finalize_bundle(
                reconcile_dir, "REHEARSAL", "reconcile", reconcile_evidence
            )
            self.assertEqual(0, reconcile_evidence.failures)
            self.assertTrue(reconcile_reference.startswith("urn:rabbit-evidence:m5-4:"))
            result = json.loads(
                (reconcile_dir / "reconciliation.json").read_text(encoding="utf-8")
            )
            self.assertEqual(2, result["rosterCount"])
            self.assertEqual(1, result["attendedCount"])
            self.assertEqual(1, result["publishedCount"])

    def test_public_or_ambiguous_target_is_rejected(self) -> None:
        for value in ("https://8.8.8.8", "https://rabbit.example.com"):
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    pilot.safe_base_url(value)

    def test_live_freeze_requires_accepted_staff_rehearsal(self) -> None:
        checks = [
            {"key": key, "mandatory": True, "status": "PASS"}
            for key in sorted(pilot.M5_4_PREREQUISITES)
        ]
        checks.extend(
            {"key": key, "mandatory": True, "status": "NOT_RUN"}
            for key in sorted(pilot.M5_4_KEYS)
        )
        blocked = pilot.Evidence()
        pilot.validate_readiness(
            {"signedOff": False, "checks": checks}, "LIVE", blocked
        )
        self.assertEqual(1, blocked.failures)
        staff = next(item for item in checks if item["key"] == "STAFF_REHEARSAL")
        staff["status"] = "PASS"
        accepted = pilot.Evidence()
        pilot.validate_readiness(
            {"signedOff": False, "checks": checks}, "LIVE", accepted
        )
        self.assertEqual(0, accepted.failures)

    def test_unapproved_student_in_an_eligible_section_blocks_freeze(self) -> None:
        assessment = deepcopy(RabbitHandler.assessment)
        config = {
            "PILOT_M5_4_ASSESSMENT_ID": "assessment-1",
            "PILOT_M5_4_MIN_QUESTIONS": "2",
            "PILOT_M5_4_MAX_QUESTIONS": "3",
            "PILOT_M5_4_MIN_DURATION_MINUTES": "1",
            "PILOT_M5_4_MAX_DURATION_MINUTES": "3",
        }
        roster_user = RabbitHandler._user(
            "student-1", "student1@institution.invalid", "STUDENT"
        )
        unapproved_user = RabbitHandler._user(
            "student-3", "student3@institution.invalid", "STUDENT"
        )
        evidence = pilot.Evidence()
        pilot.validate_assessment(
            assessment,
            config,
            [roster_user, unapproved_user],
            [roster_user],
            evidence,
            before_event=True,
        )
        self.assertEqual(1, evidence.failures)
        failed_labels = {
            item["check"] for item in evidence.checks if item["status"] == "FAIL"
        }
        self.assertEqual({"No unapproved eligible Student"}, failed_labels)

    def _config(self, roster: Path, incidents: Path, backup: Path) -> str:
        port = self.server.server_address[1]
        return f"""PILOT_M5_4_EVENT_TYPE=REHEARSAL
PILOT_M5_4_BASE_URL=http://127.0.0.1:{port}
PILOT_M5_4_ORGANISATION_CODE=INST
PILOT_M5_4_INSTITUTION_NAME=Institution One
PILOT_M5_4_RELEASE_VERSION=1.0.0
PILOT_M5_4_ASSESSMENT_ID=assessment-1
PILOT_M5_4_ADMIN_EMAIL=admin@institution.invalid
PILOT_M5_4_ADMIN_PASSWORD=local-test-password
PILOT_M5_4_ROSTER_FILE={roster}
PILOT_M5_4_INCIDENT_FILE={incidents}
PILOT_M5_4_BACKUP_DIRECTORY={backup}
PILOT_M5_4_MIN_QUESTIONS=2
PILOT_M5_4_MAX_QUESTIONS=3
PILOT_M5_4_MIN_DURATION_MINUTES=1
PILOT_M5_4_MAX_DURATION_MINUTES=3
PILOT_M5_4_MAX_STUDENTS=2
PILOT_M5_4_UAT_LEAD=UAT Lead
PILOT_M5_4_TECHNICAL_OWNER=Technical Owner
PILOT_M5_4_SUPPORT_OWNER=Support Owner
PILOT_M5_4_ROLLBACK_OWNER=Rollback Owner
PILOT_M5_4_DATA_PRIVACY_OWNER=Privacy Owner
PILOT_M5_4_INCIDENT_CHANNEL=Local pilot support channel
"""


if __name__ == "__main__":
    unittest.main()
