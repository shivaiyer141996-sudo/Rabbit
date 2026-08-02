# M5.6 Release 1.0 closure

M5.6 closes the approved Rabbit Release 1.0 pilot into one exact commit, a
fast-forwarded `main`, an annotated `v1.0.0` tag, and a complete checksummed
local release package. It does not add product scope.

Repository tooling being present is not M5.6 acceptance. Preparation is blocked
until M5.1–M5.5 have genuinely passed on the designated computer and Rabbit
contains the institution's immutable **Go** decision for the exact current commit.
Conditional Retest and No-Go can never produce a Release 1.0 closure.

## Local-only release boundary

- Rabbit continues to run through Docker Compose with local PostgreSQL, Redis,
  RabbitMQ, and MinIO.
- Release images are built and retained on the designated computer.
- The release package exports all seven active runtime images to approved
  separate local media; no container registry is required.
- The tag workflow verifies source, tests, image builds, critical
  vulnerabilities, main ancestry, and architecture policy. It cannot publish an
  image or deploy Rabbit.
- The package includes source and repository bundles, image archives, migration
  checksums, runtime provenance, the M5.5 decision summary, and SHA-256 integrity.
- It excludes credentials, the PostgreSQL/MinIO data backup, student data, and
  the signed institutional PDF. Those remain in their already-approved local
  M5.5/backup locations.

The API and web images carry OCI `version` and `revision` labels. M5.6 refuses
release when either image label differs from the immutable Go decision's version
or 40-character Git commit.

## Prerequisites

Before M5.6 preparation:

1. Run the designated host on the final clean M5.6 commit using the approved
   local `.env`.
2. Set `RABBIT_RELEASE_VERSION=1.0.0` and
   `RABBIT_RELEASE_COMMIT=<the exact 40-character commit>` before the final image
   build and M5.5 decision.
3. Complete M5.5 `prepare`, record a human **Go** decision in Rabbit, and complete
   M5.5 `finalize`.
4. Confirm every mandatory Pilot readiness row passes with a checksummed local
   `urn:rabbit-evidence:...` reference.
5. Keep `origin/main` as an ancestor of the release commit so the release can use
   a fast-forward. Do not create `v1.0.0` in advance.
6. Name a release owner, a different verification owner, and an approved separate
   local disk/USB location with sufficient space for all runtime images.
7. Confirm both the feature-branch and exact tag GitHub Actions runs are green.

## 1. Prepare the protected inputs

From the repository root:

```bash
cp .env.pilot-m5-6.example .env.pilot-m5-6
chmod 600 .env.pilot-m5-6
```

Replace every placeholder. Point the two M5.5 paths to their passed
`prepare-manifest.json` and `final-manifest.json`. `PILOT_M5_6_RELEASE_OUTPUT`
must be outside the Git worktree and should be on the approved separate local
media. The configured non-demo Organisation Admin is used only to re-read the
locked decision, operations state, and disabled external-delivery flag.

## 2. Prepare the local release package

```bash
make pilot-m5-6-prepare
```

Preparation is non-mutating against Rabbit and Git. It verifies:

- both M5.5 bundles and their cross-bound evidence references/checksums;
- Go outcome, sign-off lock, exact decision, exact tenant, and all mandatory
  M5.1–M5.4 rows;
- no cloud runtime, public endpoint, credential capture, or external delivery;
- clean current Git commit, `origin/main` ancestry, and an unused release tag;
- all seven local services plus API/web image revision and version labels;
- live Rabbit operations readiness and the same immutable Go decision; and
- named release ownership, separate local media, and full local-image export.

A passing run produces a directory similar to:

```text
<approved-local-media>/rabbit-m5.6-prepare-<UTC>/
```

Key files are:

- `release-manifest.json` and `checks.json`;
- `Rabbit-1.0.0-source.tar.gz` and `Rabbit-1.0.0.bundle`;
- `Rabbit-1.0.0-runtime-images.tar` for PostgreSQL, Redis, RabbitMQ, MinIO,
  API, web, and Nginx;
- `runtime-snapshot.json`, `decision-summary.json`, and
  `migration-SHA256SUMS`;
- `manual-release-commands.txt`; and
- `SHA256SUMS` plus the local M5.6 evidence reference.

Review every file and retain the package. Do not run commands from a failed
bundle and do not edit a checksum.

## 3. Human fast-forward and annotated tag

The named release owner and independent verifier compare the bundle with the
M5.5 Go record. Only then use the exact commands recorded in
`manual-release-commands.txt`.

The permitted Git operation is a fast-forward of `main` to the approved commit,
followed by one annotated `v1.0.0` tag on that same commit. A merge commit,
rebased commit, force push, moved tag, or later source change invalidates the Go
decision and requires a new evidence cycle.

Pushing `v1.0.0` runs verification only. The workflow has read-only repository
permission, builds/scans images without pushing them, and has no registry login
or package-write permission.

## 4. Verify the final main/tag state

After both exact GitHub Actions runs are green, fetch the remote refs, check out
`main`, and run:

```bash
make pilot-m5-6-verify-tag \
  PILOT_M5_6_PREPARED_MANIFEST=<passed-release-manifest.json>
```

Finalization requires a clean local `main`, `HEAD`, `refs/heads/main`,
`origin/main`, and the peeled annotated `v1.0.0` tag all to resolve to the same
Go-approved commit. It rechecks the entire prepared package before producing a
separate checksummed `rabbit-m5.6-final-<UTC>` bundle.

The runner never performs the merge, tag, push, or rollback itself.

## Local reinstall and rollback material

The release image archive can be restored without a container registry:

```bash
docker image load --input Rabbit-1.0.0-runtime-images.tar
```

The Git bundle and source archive preserve the exact tracked Release 1.0 source.
The live local PostgreSQL/MinIO data must be restored only through the existing
guarded backup/restore runbook; it is intentionally not part of the software
release package.

## Acceptance boundary

M5.6 passes only when:

- M5.5 is an immutable Go for the exact M5.6 commit and institution;
- the designated local host produces a passing M5.6 prepare bundle;
- branch and tag verification runs are green for that exact commit;
- `main` and annotated `v1.0.0` resolve to that exact commit without force or
  history rewrite;
- a passing M5.6 final bundle is retained on approved separate local media; and
- the institution's local data, backup, and operating ownership remain intact.

Until these conditions are met, Release 1.0 is not formally closed and Milestone
6 commercial rollout must not be enabled for real institutions.
