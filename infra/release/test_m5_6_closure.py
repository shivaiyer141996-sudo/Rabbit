#!/usr/bin/env python3
"""Focused contracts for Rabbit's local-only M5.6 release closure."""

from __future__ import annotations

import hashlib
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("m5-6-closure.py")
SPEC = importlib.util.spec_from_file_location("rabbit_m5_6_closure", MODULE_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

COMMIT = "a" * 40


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value) + "\n", encoding="utf-8")


def seal(bundle: Path, prefix: str) -> tuple[str, str]:
    files = sorted(
        path for path in bundle.iterdir() if path.is_file() and path.name != "SHA256SUMS"
    )
    lines = [
        f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}" for path in files
    ]
    checksum = bundle / "SHA256SUMS"
    checksum.write_text("\n".join(lines) + "\n", encoding="utf-8")
    digest = hashlib.sha256(checksum.read_bytes()).hexdigest()
    reference = f"{prefix}{bundle.name}:{digest}"
    (bundle / "evidence-reference.txt").write_text(reference + "\n", encoding="utf-8")
    return reference, digest


class M56ClosureTest(unittest.TestCase):
    def test_complete_go_evidence_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            prepared, final = self._fixture(Path(temporary))
            evidence = MODULE.Evidence()
            loaded = MODULE.load_m5_5_evidence(prepared, final, evidence)
            self.assertEqual(evidence.failures, 0)
            self.assertEqual(loaded["decision"]["releaseCommit"], COMMIT)
            self.assertTrue(loaded["readiness"]["signedOff"])

    def test_no_go_cannot_close_release(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            prepared, final = self._fixture(Path(temporary), outcome="NO_GO")
            evidence = MODULE.Evidence()
            MODULE.load_m5_5_evidence(prepared, final, evidence)
            failed = {item["check"] for item in evidence.checks if item["status"] == "FAIL"}
            self.assertIn("M5.5 preparation outcome", failed)
            self.assertIn("Immutable Go decision", failed)

    def test_missing_mandatory_evidence_blocks_release(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            prepared, final = self._fixture(Path(temporary), omit="LIVE_ASSESSMENT")
            evidence = MODULE.Evidence()
            MODULE.load_m5_5_evidence(prepared, final, evidence)
            failed = {item["check"] for item in evidence.checks if item["status"] == "FAIL"}
            self.assertIn("Complete M5.1-M5.4 evidence set", failed)

    def test_tampered_final_evidence_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            prepared, final = self._fixture(Path(temporary))
            final.write_text("{}\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                MODULE.load_m5_5_evidence(prepared, final, MODULE.Evidence())

    def test_public_target_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "public network"):
            MODULE.safe_base_url("https://8.8.8.8")

    def test_final_git_state_accepts_only_exact_annotated_tag(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary) / "repo"
            repo.mkdir()
            self._git(repo, "init", "--initial-branch=main")
            self._git(repo, "config", "user.name", "Rabbit Test")
            self._git(repo, "config", "user.email", "rabbit@test.local")
            (repo / "README.md").write_text("Rabbit\n", encoding="utf-8")
            self._git(repo, "add", "README.md")
            self._git(repo, "commit", "-m", "release")
            commit = self._git(repo, "rev-parse", "HEAD")
            self._git(repo, "update-ref", "refs/remotes/origin/main", commit)
            self._git(repo, "tag", "-a", "v1.0.0", "-m", "Rabbit 1.0.0")
            evidence = MODULE.Evidence()
            state = MODULE.validate_git_final(
                repo,
                {
                    "releaseCommit": commit,
                    "releaseTag": "v1.0.0",
                    "mainRef": "origin/main",
                },
                evidence,
            )
            self.assertEqual(evidence.failures, 0)
            self.assertEqual(state["tagType"], "tag")

            self._git(repo, "tag", "-d", "v1.0.0")
            self._git(repo, "tag", "v1.0.0")
            evidence = MODULE.Evidence()
            MODULE.validate_git_final(
                repo,
                {
                    "releaseCommit": commit,
                    "releaseTag": "v1.0.0",
                    "mainRef": "origin/main",
                },
                evidence,
            )
            self.assertGreater(evidence.failures, 0)

    def _git(self, repo: Path, *args: str) -> str:
        return subprocess.run(
            ["git", "-C", str(repo), *args],
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()

    def _fixture(
        self,
        root: Path,
        *,
        outcome: str = "GO",
        omit: str | None = None,
    ) -> tuple[Path, Path]:
        prepared_dir = root / "rabbit-m5.5-prepare-test"
        prepared_dir.mkdir()
        prepared_manifest = prepared_dir / "prepare-manifest.json"
        write_json(
            prepared_manifest,
            {
                "passed": True,
                "outcome": outcome,
                "releaseVersion": "1.0.0",
                "releaseCommit": COMMIT,
                "localDataConfirmed": True,
                "localOnlyConfirmed": True,
                "ownershipAccepted": True,
                "scopeFreezeAccepted": True,
                "cloudRuntimeUsed": False,
                "publicEndpointUsed": False,
                "credentialsRecorded": False,
            },
        )
        prepared_reference, prepared_digest = seal(
            prepared_dir, "urn:rabbit-evidence:m5-5:prepare:"
        )

        final_dir = root / "rabbit-m5.5-final-test"
        final_dir.mkdir()
        decision_id = "11111111-1111-1111-1111-111111111111"
        decision = {
            "id": decision_id,
            "outcome": outcome,
            "releaseVersion": "1.0.0",
            "releaseCommit": COMMIT,
            "evidenceReference": prepared_reference,
            "evidenceSha256": prepared_digest,
        }
        readiness = {
            "signedOff": outcome == "GO",
            "mandatoryChecksPassed": True,
            "checks": [
                {
                    "key": key,
                    "mandatory": True,
                    "status": "PASS",
                    "evidenceUrl": f"urn:rabbit-evidence:m5-test:{key.lower()}",
                }
                for key in sorted(MODULE.MANDATORY_CHECKS)
                if key != omit
            ],
        }
        write_json(final_dir / "decision.json", decision)
        write_json(final_dir / "readiness-after-decision.json", readiness)
        write_json(final_dir / "checks.json", {"passed": True, "failures": 0})
        final_manifest = final_dir / "final-manifest.json"
        write_json(
            final_manifest,
            {
                "passed": True,
                "outcome": outcome,
                "decisionId": decision_id,
                "organisationId": "22222222-2222-2222-2222-222222222222",
                "preparedEvidenceReference": prepared_reference,
                "preparedEvidenceSha256": prepared_digest,
                "cloudRuntimeUsed": False,
                "publicEndpointUsed": False,
                "credentialsRecorded": False,
            },
        )
        seal(final_dir, "urn:rabbit-evidence:m5-5:final:")
        return prepared_manifest, final_manifest


if __name__ == "__main__":
    unittest.main()
