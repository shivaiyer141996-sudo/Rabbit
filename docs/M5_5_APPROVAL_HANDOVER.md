# M5.5 final approval and local handover

M5.5 records the institution's final Rabbit Release 1.0 pilot decision and
hands the zero-cost local installation to named human owners. It supports three
truthful outcomes: **Go**, **Conditional Retest**, and **No-Go**.

Repository tooling being present is not acceptance. A named institution must
review the actual M5.1–M5.4 evidence, sign the local acceptance record, and enter
the decision through Rabbit. Automation cannot sign or choose an outcome.

## Decision behaviour

| Outcome | Required state | Effect |
| --- | --- | --- |
| Go | Every mandatory readiness row passes; no S1/S2 is open; all local-data, ownership, and scope attestations are accepted | Creates an immutable decision and institutional sign-off; permanently locks the readiness register |
| Conditional Retest | A future retest deadline and reason are recorded; no expansion is permitted | Creates an immutable decision snapshot; keeps the register open for bounded fixes and retest evidence |
| No-Go | A reason and signed institutional record are provided | Creates an immutable decision snapshot; keeps release expansion blocked and the register available for a separately approved future cycle |

Each decision captures the exact release commit/version, institution and UAT
signatories, technical/support/monitoring/backup/incident/rollback/privacy
owners, handover recipient, known-issue count, local evidence reference and
SHA-256, decision reason, gate-count snapshot, and attestations. The database
rejects updates or deletion of decision rows. Only a Go also creates the existing
one-per-institution sign-off lock.

## Prerequisites

Before preparing the M5.5 decision:

- run and review M5.1 host, M5.2 browser/human, M5.3 technical/recovery, and M5.4
  rehearsal/live evidence on the designated local computer;
- ensure the selected LIVE `reconciliation.json` is protected by its original
  `SHA256SUMS` and belongs to the exact current release/tenant;
- create a clean, quiesced, checksum-verified local backup no older than 24 hours;
- close every S1/S2 for Go, and preserve the complete incident register;
- put every open S3/S4 in the known-issue register with a named owner, safe
  workaround, target date, and defect ID;
- name the institution signatory, UAT lead, all operating owners, and handover
  recipient; and
- keep Rabbit healthy on the private local address with email/SMS disabled.

## Prepare protected local inputs

From the repository root:

```bash
cp .env.pilot-m5-5.example .env.pilot-m5-5
cp infra/pilot/templates/m5-5-known-issues.csv.example .pilot-m5-5-known-issues.csv
cp infra/pilot/templates/m5-5-acceptance-record.md /path/on/approved/local/media/
chmod 600 .env.pilot-m5-5 .pilot-m5-5-known-issues.csv
```

Complete the acceptance template with the institution, convert/print it, obtain
the required human signatures, and retain a signed PDF as
`.pilot-m5-5-signed-acceptance.pdf` or another Git-ignored local path. The runner
rejects a missing/non-PDF/empty file and never uploads it.

The known-issue CSV header is:

```csv
issue_id,severity,status,summary,owner,workaround,target_date,defect_id,closed_at
```

Use only S3 or S4. An open row requires every field except `closed_at`; a closed
row requires `closed_at`. Target dates use `YYYY-MM-DD` and cannot already be
overdue. Keep the header with no rows when there are no known S3/S4 issues.

Replace every placeholder in `.env.pilot-m5-5`. Use `yes` or `no` for the four
attestations. Go requires all four to be `yes`; every outcome requires the
local-data and local-only confirmations to be `yes`.

## 1. Prepare the decision bundle

```bash
make pilot-m5-5-prepare
```

Preparation is read-only against Rabbit. It validates:

- local/private target, active non-demo Organisation Admin, tenant, exact commit,
  release version, operations state, and disabled external delivery;
- every passing readiness row uses a checksummed local Rabbit evidence reference;
- outcome eligibility against mandatory checks and open S1/S2 incidents;
- selected LIVE reconciliation provenance/result;
- current verified backup, signed acceptance PDF, owners, local media labels,
  owned S3/S4 register, and all architecture attestations.

A successful run creates a directory such as:

```text
artifacts/pilot-m5-5/rabbit-m5.5-prepare-<UTC>/
```

Review `checks.json`, `prepare-manifest.json`, the copied signed PDF, and
`decision-payload.json`. The latter contains the exact values to enter in
Rabbit, including the `urn:rabbit-evidence:m5-5:prepare:...` reference and
SHA-256. `decision-payload.json` is a convenience entry file; the protected
source evidence is listed in `SHA256SUMS`, whose digest is stored in Rabbit.

Do not continue if preparation exits non-zero. Preserve the failed evidence and
correct the underlying decision/evidence; never weaken a gate or edit a checksum.

## 2. Record the human decision

1. Sign in as the real Organisation Admin on the private local Rabbit address.
2. Open **Pilot readiness**.
3. Compare every displayed field with the signed acceptance PDF and generated
   `decision-payload.json`.
4. Select Go, Conditional Retest, or No-Go. Conditional Retest requires the same
   future deadline as the signed record.
5. Check the four attestations only when the institution has accepted them.
6. Use the explicit final confirmation and record the immutable decision.

Rabbit writes `PILOT_RELEASE_DECISION` to the audit log for all outcomes. Go also
writes `PILOT_SIGN_OFF` and locks every readiness row. Conditional Retest and
No-Go never authorise expansion and leave the register open for governed follow-up.

## 3. Finalize and reconcile the recorded decision

```bash
make pilot-m5-5-finalize \
  PILOT_M5_5_PREPARED_MANIFEST=artifacts/pilot-m5-5/rabbit-m5.5-prepare-<UTC>/prepare-manifest.json
```

Finalization re-verifies the preparation checksums, finds the immutable decision
by exact evidence reference and SHA-256, compares every decision/owner field,
verifies the expected Go lock state and exactly one decision audit event, and
writes a separate checksummed final bundle. It never changes Rabbit.

The final `urn:rabbit-evidence:m5-5:final:...` reference is the closeout evidence
for the institutional handover register. Retain the preparation bundle, final
bundle, signed PDF, current backup, and issue/incident registers only on the
approved primary/separate local media.

## Acceptance boundary

M5.5 passes only when:

- `prepare` and `finalize` both pass on the designated host;
- the institution's signed PDF matches the immutable Rabbit decision;
- a Go decision exists if release expansion is requested;
- all named owners acknowledge their runbook responsibility; and
- the local-only/data/media/scope confirmations remain true.

A Conditional Retest or No-Go is a valid, honest M5.5 decision record, but it is
not permission to expand Rabbit. A Go is approval for the frozen Release 1.0
pilot boundary only—not cloud migration, production-scale infrastructure, or
post–Release 1.0 features.
