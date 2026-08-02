#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
env_file="${repo_root}/.env"
config_file="${repo_root}/.env.pilot-m5-3"
evidence_root="${repo_root}/artifacts/pilot-m5-3"

usage() {
  cat <<'EOF'
Usage: ./infra/pilot/m5-3-evidence.sh [options]

Options:
  --env-file PATH   Local pilot environment (default: .env)
  --config PATH     Protected M5.3 inputs (default: .env.pilot-m5-3)
  --output PATH     Evidence root (default: artifacts/pilot-m5-3)
  -h, --help        Show help

Runs local host preflight, approved-load performance, security review, a quiesced
backup plus isolated functional restore, and a non-mutating rollback tabletop.
No cloud service, public endpoint, CI runner, or live-data restore is used.
EOF
}

while (($# > 0)); do
  case "$1" in
    --env-file) (($# >= 2)) || { usage >&2; exit 2; }; env_file="$2"; shift 2 ;;
    --config) (($# >= 2)) || { usage >&2; exit 2; }; config_file="$2"; shift 2 ;;
    --output) (($# >= 2)) || { usage >&2; exit 2; }; evidence_root="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

for required_file in "${env_file}" "${config_file}"; do
  [[ -f "${required_file}" ]] || { echo "Missing required file: ${required_file}" >&2; exit 1; }
done
for command_name in docker git sha256sum; do
  command -v "${command_name}" >/dev/null 2>&1 \
    || { echo "${command_name} is required." >&2; exit 1; }
done
docker info >/dev/null 2>&1 || { echo "Docker Engine is unavailable." >&2; exit 1; }
[[ -z "$(git -C "${repo_root}" status --porcelain)" ]] \
  || { echo "M5.3 evidence requires a clean committed release worktree." >&2; exit 1; }

for protected_file in "${env_file}" "${config_file}"; do
  permissions="$(stat -c '%a' "${protected_file}" 2>/dev/null || stat -f '%Lp' "${protected_file}" 2>/dev/null || printf unknown)"
  [[ "${permissions}" == 600 || "${permissions}" == 400 ]] \
    || { echo "$(basename "${protected_file}") must have mode 600 or 400." >&2; exit 1; }
  git -C "${repo_root}" check-ignore --quiet "${protected_file}" \
    || { echo "$(basename "${protected_file}") must be ignored by Git." >&2; exit 1; }
done

evidence_root="$(mkdir -p "${evidence_root}" && cd "${evidence_root}" && pwd -P)"
run_id="rabbit-m5.3-$(date -u +%Y%m%dT%H%M%SZ)"
run_dir="${evidence_root}/${run_id}"
mkdir -p "${run_dir}"
failures=0
backup_dir=''

declare -A results
run_step() {
  local key="$1" log_file="$2"
  shift 2
  set +e
  "$@" >"${run_dir}/${log_file}" 2>&1
  local status=$?
  set -e
  if ((status == 0)); then
    results["${key}"]=PASS
  else
    results["${key}"]=FAIL
    failures=$((failures + 1))
  fi
  return 0
}

run_step preflight preflight-step.log \
  "${repo_root}/infra/pilot/preflight.sh" \
  --env-file "${env_file}" --output "${run_dir}/m5.1-preflight" --runtime

run_step performance performance-step.log \
  "${repo_root}/infra/performance/run-pilot-load.sh" \
  --env-file "${env_file}" --config "${config_file}" \
  --output "${evidence_root}" --run-id "${run_id}"

run_step security security-step.log \
  "${repo_root}/infra/security/pilot-security-review.sh" \
  --env-file "${env_file}" --config "${config_file}" \
  --output "${evidence_root}" --run-id "${run_id}"

if [[ "${results[preflight]}" == PASS ]]; then
  set +e
  backup_output="$("${repo_root}/infra/backup/backup.sh" --quiesce 2>&1)"
  backup_status=$?
  set -e
  printf '%s\n' "${backup_output}" >"${run_dir}/backup-step.log"
  backup_dir="$(sed -n 's/^Backup completed: //p' <<<"${backup_output}" | tail -n 1)"
  if ((backup_status == 0)) && [[ -d "${backup_dir}" ]]; then
    results[backup]=PASS
  else
    results[backup]=FAIL
    failures=$((failures + 1))
  fi
else
  results[backup]=BLOCKED
  printf 'Backup blocked because M5.1 host/runtime preflight failed.\n' >"${run_dir}/backup-step.log"
  failures=$((failures + 1))
fi

if [[ "${results[backup]}" == PASS ]]; then
  run_step recovery recovery-step.log \
    "${repo_root}/infra/backup/functional-restore-drill.sh" "${backup_dir}" \
    --env-file "${env_file}" --config "${config_file}" \
    --output "${evidence_root}" --run-id "${run_id}"
else
  results[recovery]=BLOCKED
  printf 'Recovery blocked because the quiesced backup failed.\n' >"${run_dir}/recovery-step.log"
  failures=$((failures + 1))
fi

run_step rollback rollback-step.log \
  "${repo_root}/infra/pilot/rollback-rehearsal.sh" \
  --env-file "${env_file}" --config "${config_file}" \
  --output "${evidence_root}" --run-id "${run_id}"

cat >"${run_dir}/m5-3-summary.md" <<EOF
# Rabbit M5.3 performance, security, and recovery evidence

- Generated (UTC): $(date -u +%Y%m%dT%H%M%SZ)
- Release commit: $(git -C "${repo_root}" rev-parse HEAD)
- Architecture: zero-cost local Docker Compose
- Public/cloud runtime used: No
- Live database restored or overwritten: No
- Quiesced backup: $(basename "${backup_dir:-unavailable}")

| Gate | Result |
| --- | --- |
| M5.1 host/runtime prerequisite | ${results[preflight]} |
| Approved student load + 50% headroom | ${results[performance]} |
| Local security review | ${results[security]} |
| Quiesced PostgreSQL/MinIO backup | ${results[backup]} |
| Isolated functional restore, RPO, and RTO | ${results[recovery]} |
| Named rollback tabletop | ${results[rollback]} |

## Acceptance boundary

This bundle is technical evidence, not institutional approval. A named tester must
review it, record the Performance, Security review, Backup/restore, and Operating
ownership rows in Rabbit's Pilot readiness screen, and reference the evidence
through an institution-approved local location. Any failed or blocked row is a
No-Go until corrected and rerun. Do not upload the protected environment files.
EOF

cat >"${run_dir}/m5-3-manifest.txt" <<EOF
Rabbit M5.3 evidence manifest
release_commit=$(git -C "${repo_root}" rev-parse HEAD)
preflight=${results[preflight]}
performance=${results[performance]}
security=${results[security]}
backup=${results[backup]}
recovery=${results[recovery]}
rollback=${results[rollback]}
failures=${failures}
local_compose_only=true
cloud_runtime=false
public_endpoint=false
live_restore=false
secrets_included=false
EOF

(
  cd "${run_dir}"
  find . -type f ! -name SHA256SUMS -print0 \
    | sort -z \
    | while IFS= read -r -d '' evidence_file; do
        sha256sum "${evidence_file}"
      done >SHA256SUMS
)

echo "M5.3 evidence: ${run_dir}"
if ((failures > 0)); then
  echo "M5.3 failed or blocked at ${failures} gate(s)." >&2
  exit 1
fi
echo "M5.3 technical evidence passed. Human pilot-register review remains required."
