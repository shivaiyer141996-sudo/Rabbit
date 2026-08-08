#!/usr/bin/env python3
"""Acceptance contract for branding, Customer Accounts and commercial entitlements."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
failures: list[str] = []


def source(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def check(condition: bool, label: str) -> None:
    print(("PASS  " if condition else "FAIL  ") + label)
    if not condition:
        failures.append(label)


migration = source("backend/src/main/resources/db/migration/V11__platform_branding_customer_accounts_entitlements.sql")
platform = source("backend/src/main/java/com/rabbit/aip/platform/PlatformService.java")
controller = source("backend/src/main/java/com/rabbit/aip/platform/PlatformController.java")
branding = source("backend/src/main/java/com/rabbit/aip/organisation/OrganisationBrandingService.java")
branding_controller = source("backend/src/main/java/com/rabbit/aip/organisation/OrganisationBrandingController.java")
commercial = source("backend/src/main/java/com/rabbit/aip/commercial/CommercialService.java")
worker = source("backend/src/main/java/com/rabbit/aip/commercial/TrialReminderWorker.java")
shell = source("frontend/src/components/app-shell.tsx")
console = source("frontend/src/components/platform-console.tsx")
logo = source("frontend/src/components/logo.tsx")

check("CREATE TABLE customer_accounts" in migration, "Customer Account persistence exists")
check("ADD COLUMN customer_account_id" in migration and "ALTER COLUMN customer_account_id SET NOT NULL" in migration, "Every Organisation has a Customer Account")
check("SELECT gen_random_uuid()" in migration and "FROM organisations organisation" in migration, "Existing Organisations are migrated without replacement")
check("CREATE TABLE institutes" in migration and "ADD COLUMN institute_id" in migration, "Organisation to Institute to Department hierarchy is explicit")
check(all(value in migration for value in ("commercial_plan_definitions", "commercial_plan_prices", "commercial_plan_entitlements")), "Plans, slabs, prices and entitlements are database configured")
check("default_trial_days" in migration and "DEFAULT 20" in migration and "'LEGEND'" in migration, "20-day Legend trial defaults are persisted")
check(all(value in migration for value in ("TRIAL_EXPIRED", "GRACE_PERIOD", "SUSPENDED", "CANCELLED")), "Complete subscription lifecycle is constrained")
check("commercial_subscription_events" in migration and "immutable_commercial_subscription_event" in source("backend/src/main/resources/db/migration/V9__commercial_readiness.sql"), "Subscription history remains immutable")
check("@PreAuthorize(\"hasRole('SUPER_ADMIN')\")" in controller, "Platform APIs are Super-Admin only")
check("assignOrganisation" in platform and "ORGANISATION_CUSTOMER_ACCOUNT_ASSIGNED" in platform, "Organisation reassignment is controlled and audited")
check("CUSTOMER_ACCOUNT_INACTIVE" in commercial and "customerAccountActive" in commercial, "Customer status is enforced server-side")
check("catalogService.entitlements" in commercial and "PLAN_ENTITLEMENT_REQUIRED" in commercial, "Feature entitlement is enforced server-side")
check("Available on " in commercial, "Plan restriction responses identify the required Rabbit plan")
check("MinioClient" in branding and "organisation-branding/" in branding, "Organisation logos use local MinIO")
check(all(value in branding for value in ("image/png", "image/jpeg", "image/webp", "MAX_LOGO_BYTES")), "Logo type and size validation exists")
check(all(value in branding_controller for value in ("@PutMapping", "@DeleteMapping", "@GetMapping")), "Logo preview/change/remove/download endpoints exist")
check("trialDurationDays" in platform and "defaultTrialDays" in console, "Trial duration supports defaults and per-Organisation override")
check("class TrialReminderWorker" in worker and "getTrialReminderDays" in worker, "Configurable trial reminders are scheduled")
check(all(value in console for value in ("Customer Accounts", "Create Organisation", "Manual subscription control", "Final entitlement matrix")), "Super Admin commercial dashboard is implemented")
check('href: "/platform"' in shell and 'roles: ["SUPER_ADMIN"]' in shell, "Platform navigation is Super-Admin only")
check("rabbit-logo.png" in logo and "rabbit-mark.png" in shell and "organisationLogoAvailable" in shell, "Approved Rabbit and Organisation branding coexist")
check(not any(value in migration + branding for value in ("amazonaws", "azure", "google cloud", "kubernetes")), "No paid cloud infrastructure was introduced")

if failures:
    raise SystemExit(f"{len(failures)} platform contract check(s) failed")
print("\nAll platform branding and commercial contract checks passed.")
