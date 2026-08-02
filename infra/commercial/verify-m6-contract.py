#!/usr/bin/env python3
"""Verify Rabbit M6's dormant local-only commercial contract and activation inputs."""

from __future__ import annotations

import argparse
import ipaddress
import re
import sys
from pathlib import Path


EXPECTED_PRICES = {
    ("BASIC", 50): 59900,
    ("BASIC", 150): 99900,
    ("BASIC", 500): 149900,
    ("PRO", 50): 89900,
    ("PRO", 150): 139900,
    ("PRO", 500): 189900,
    ("LEGEND", 50): 149900,
    ("LEGEND", 150): 199900,
    ("LEGEND", 500): 249900,
}
COMMIT = re.compile(r"^[0-9a-fA-F]{7,40}$")
FINAL_EVIDENCE = re.compile(
    r"^urn:rabbit-evidence:m5-6:final:[A-Za-z0-9][A-Za-z0-9._:-]{20,900}$"
)


def parse_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ValueError(f"Invalid environment line {line_number}: missing '='.")
        key, value = line.split("=", 1)
        key = key.strip()
        if not re.fullmatch(r"[A-Z][A-Z0-9_]*", key):
            raise ValueError(f"Invalid environment key on line {line_number}.")
        values[key] = value.strip().strip('"').strip("'")
    return values


def private_bind_address(value: str) -> bool:
    if value.lower() == "localhost":
        return True
    try:
        address = ipaddress.ip_address(value)
    except ValueError:
        return False
    return address.is_loopback or (address.is_private and not address.is_unspecified)


def require(condition: bool, message: str, failures: list[str]) -> None:
    if condition:
        print(f"PASS  {message}")
    else:
        print(f"FAIL  {message}", file=sys.stderr)
        failures.append(message)


def repository_contract(repo: Path, failures: list[str]) -> None:
    migration = (repo / "backend/src/main/resources/db/migration/V9__commercial_readiness.sql").read_text(
        encoding="utf-8"
    )
    types = (repo / "backend/src/main/java/com/rabbit/aip/commercial/CommercialTypes.java").read_text(
        encoding="utf-8"
    )
    app = (repo / "backend/src/main/resources/application.yml").read_text(encoding="utf-8")
    compose = (repo / "docker-compose.yml").read_text(encoding="utf-8")
    frontend = (repo / "frontend/src/components/commercial-console.tsx").read_text(encoding="utf-8")
    evaluation = (repo / "backend/src/main/java/com/rabbit/aip/evaluation/EvaluationController.java").read_text(
        encoding="utf-8"
    )
    settings = (repo / "backend/src/main/java/com/rabbit/aip/settings/SettingsController.java").read_text(
        encoding="utf-8"
    )
    organisation = (repo / "backend/src/main/java/com/rabbit/aip/organisation/OrganisationController.java").read_text(
        encoding="utf-8"
    )
    commercial_controller = (repo / "backend/src/main/java/com/rabbit/aip/commercial/CommercialController.java").read_text(
        encoding="utf-8"
    )

    for (plan, limit), price in EXPECTED_PRICES.items():
        require(
            f"selected_plan = '{plan}' AND selected_limit = {limit} THEN selected_price = {price}" in migration,
            f"Database locks {plan} / {limit} to {price} paise",
            failures,
        )
    require("INTERVAL '20 days'" in migration, "Database trial window is exactly 20 days", failures)
    require(
        "CREATE TRIGGER enforce_commercial_student_limit" in migration,
        "Database serializes Student-capacity enforcement",
        failures,
    )
    require(
        "immutable_commercial_payment" in migration
        and "immutable_commercial_receipt" in migration
        and "commercial_invoice_state_guard" in migration,
        "Accounting records have database mutation guards",
        failures,
    )
    require("TRIAL_DAYS = 20" in types, "Application trial window is exactly 20 days", failures)
    require(
        "COMMERCIAL_CONTROLS_ENABLED:false" in app,
        "Commercial controls default off in Spring configuration",
        failures,
    )
    require(
        "COMMERCIAL_CONTROLS_ENABLED:-false" in compose,
        "Commercial controls default off in Docker Compose",
        failures,
    )
    require(
        "no payment gateway is connected" in frontend.lower(),
        "Interface states the manual-payment boundary",
        failures,
    )
    require(
        evaluation.count("requireEntitlement(Entitlement.ASSESSMENT_DELIVERY)") == 3,
        "Evaluation mutations require an active assessment entitlement",
        failures,
    )
    require(
        settings.count("requireEntitlement(Entitlement.ASSESSMENT_DELIVERY)") == 5,
        "Settings mutations require an active assessment entitlement",
        failures,
    )
    require(
        "COMMERCIAL_ONBOARDING_REQUIRED" in organisation,
        "Legacy organisation creation cannot bypass commercial onboarding",
        failures,
    )
    require(
        'PostMapping("/subscription/suspend")' in commercial_controller
        and 'PostMapping("/subscription/restore")' in commercial_controller,
        "Reason-gated subscription suspension and restoration are exposed",
        failures,
    )
    dependency_text = "\n".join(
        (repo / path).read_text(encoding="utf-8")
        for path in ("backend/pom.xml", "frontend/package.json")
    ).lower()
    require(
        not re.search(r"stripe|razorpay|cashfree|payu|paypal|aws-sdk|azure-|google-cloud", dependency_text),
        "No payment-gateway or cloud SDK dependency is present",
        failures,
    )
    for relative in (
        "infra/backup/backup.sh",
        "infra/backup/restore-drill.sh",
        "infra/backup/functional-restore-drill.sh",
    ):
        backup_text = (repo / relative).read_text(encoding="utf-8")
        require(
            all(
                table in backup_text
                for table in (
                    "organisation_subscriptions",
                    "commercial_subscription_events",
                    "commercial_invoices",
                    "commercial_payments",
                    "commercial_receipts",
                    "commercial_support_cases",
                )
            ),
            f"{relative} reconciles M6 commercial records",
            failures,
        )


def activation_contract(values: dict[str, str], failures: list[str]) -> None:
    require(
        values.get("COMMERCIAL_CONTROLS_ENABLED", "").lower() == "true",
        "Activation explicitly enables commercial controls",
        failures,
    )
    require(
        COMMIT.fullmatch(values.get("RABBIT_RELEASE_COMMIT", "")) is not None,
        "Activation binds an exact Git release commit",
        failures,
    )
    require(
        FINAL_EVIDENCE.fullmatch(values.get("COMMERCIAL_M5_6_EVIDENCE_REFERENCE", "")) is not None,
        "Activation binds the final local M5.6 evidence reference",
        failures,
    )
    require(
        private_bind_address(values.get("RABBIT_BIND_ADDRESS", "127.0.0.1")),
        "Activation remains on loopback or one private LAN address",
        failures,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, required=True)
    parser.add_argument("--env-file", type=Path)
    args = parser.parse_args()
    repo = args.repo_root.resolve()
    failures: list[str] = []
    try:
        repository_contract(repo, failures)
        if args.env_file:
            activation_contract(parse_env(args.env_file.resolve()), failures)
    except (OSError, ValueError) as error:
        print(f"M6 verification could not complete: {error}", file=sys.stderr)
        return 2
    if failures:
        print(f"\nM6 verification failed with {len(failures)} finding(s).", file=sys.stderr)
        return 1
    print("\nM6 commercial contract verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
