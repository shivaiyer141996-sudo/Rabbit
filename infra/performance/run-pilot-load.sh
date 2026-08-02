#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
env_file="${repo_root}/.env"
config_file="${repo_root}/.env.pilot-m5-3"
evidence_root="${repo_root}/artifacts/pilot-m5-3"
run_id="rabbit-m5.3-$(date -u +%Y%m%dT%H%M%SZ)"

usage() {
  cat <<'EOF'
Usage: ./infra/performance/run-pilot-load.sh [options]

Options:
  --env-file PATH   Local pilot environment (default: .env)
  --config PATH     Protected M5.3 inputs (default: .env.pilot-m5-3)
  --output PATH     Evidence root (default: artifacts/pilot-m5-3)
  --run-id ID       Existing/new evidence run directory name
  -h, --help        Show help
EOF
}

while (($# > 0)); do
  case "$1" in
    --env-file) (($# >= 2)) || { usage >&2; exit 2; }; env_file="$2"; shift 2 ;;
    --config) (($# >= 2)) || { usage >&2; exit 2; }; config_file="$2"; shift 2 ;;
    --output) (($# >= 2)) || { usage >&2; exit 2; }; evidence_root="$2"; shift 2 ;;
    --run-id) (($# >= 2)) || { usage >&2; exit 2; }; run_id="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ "${run_id}" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Unsafe run ID." >&2; exit 2; }
for required_file in "${env_file}" "${config_file}"; do
  [[ -f "${required_file}" ]] || { echo "Missing required file: ${required_file}" >&2; exit 1; }
done
for command_name in docker git; do
  command -v "${command_name}" >/dev/null 2>&1 \
    || { echo "${command_name} is required." >&2; exit 1; }
done
docker info >/dev/null 2>&1 || { echo "Docker Engine is unavailable." >&2; exit 1; }

config_file="$(cd "$(dirname "${config_file}")" && pwd -P)/$(basename "${config_file}")"
evidence_root="$(mkdir -p "${evidence_root}" && cd "${evidence_root}" && pwd -P)"
run_dir="${evidence_root}/${run_id}"
mkdir -p "${run_dir}"

permissions="$(stat -c '%a' "${config_file}" 2>/dev/null || stat -f '%Lp' "${config_file}" 2>/dev/null || printf unknown)"
if [[ "${permissions}" != 600 && "${permissions}" != 400 ]]; then
  echo "Protected M5.3 config must have mode 600 or 400; found ${permissions}." >&2
  exit 1
fi
if ! git -C "${repo_root}" check-ignore --quiet "${config_file}"; then
  echo "Protected M5.3 config is not ignored by Git." >&2
  exit 1
fi

env_value() {
  local key="$1"
  awk -v key="${key}" '
    $0 !~ /^[[:space:]]*#/ && index($0, key "=") == 1 {
      sub(/^[^=]*=/, ""); gsub(/\r$/, ""); print; exit
    }
  ' "${env_file}"
}

authenticated_limit="$(env_value RATE_LIMIT_AUTHENTICATED_PER_MINUTE)"
authenticated_limit="${authenticated_limit:-300}"
[[ "${authenticated_limit}" =~ ^[1-9][0-9]*$ ]] \
  || { echo "RATE_LIMIT_AUTHENTICATED_PER_MINUTE must be a positive integer." >&2; exit 1; }

export M5_3_CONFIG_FILE="${config_file}"
export M5_3_EVIDENCE_ROOT="${evidence_root}"
compose=(
  docker compose --env-file "${env_file}"
  -f "${repo_root}/docker-compose.yml"
  -f "${repo_root}/infra/pilot/compose.local-pilot.yml"
  -f "${repo_root}/infra/performance/compose.pilot-load.yml"
)

running_services="$("${compose[@]}" ps --services --status running 2>/dev/null || true)"
for service in postgres redis rabbitmq minio backend frontend nginx; do
  grep -Fxq "${service}" <<<"${running_services}" \
    || { echo "Rabbit service is not running: ${service}" >&2; exit 1; }
done

cat >"${run_dir}/performance-metadata.txt" <<EOF
Rabbit M5.3 local performance evidence
generated_at_utc=$(date -u +%Y%m%dT%H%M%SZ)
release_commit=$(git -C "${repo_root}" rev-parse HEAD)
profile=approved-concurrent-students-plus-50-percent
target=internal-backend-on-local-compose-app-network
credentials=protected-file-mounted-read-only-and-not-recorded
EOF

set +e
"${compose[@]}" --profile m5-3-evidence run --rm --no-deps -T \
  -e RABBIT_M5_3_CONFIG=/run/secrets/m5-3.env \
  -e "RABBIT_EVIDENCE_RUN_ID=${run_id}" \
  -e "RABBIT_AUTHENTICATED_RATE_LIMIT=${authenticated_limit}" \
  pilot-load run /scripts/assessment-read-load.js \
  2>&1 | tee "${run_dir}/performance.log"
load_status=${PIPESTATUS[0]}
set -e

if ((load_status != 0)); then
  echo "M5.3 performance thresholds failed. Evidence: ${run_dir}" >&2
  exit "${load_status}"
fi
for required_result in performance-summary.json performance-raw-summary.json; do
  [[ -s "${run_dir}/${required_result}" ]] \
    || { echo "k6 did not write ${required_result}." >&2; exit 1; }
done

echo "M5.3 performance thresholds passed."
echo "Evidence: ${run_dir}"
