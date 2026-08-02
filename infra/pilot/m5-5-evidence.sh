#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
env_file="${repo_root}/.env"
config_file="${repo_root}/.env.pilot-m5-5"
evidence_root="${repo_root}/artifacts/pilot-m5-5"
prepared_manifest=""

usage() {
  cat <<'EOF'
Usage:
  ./infra/pilot/m5-5-evidence.sh prepare [options]
  ./infra/pilot/m5-5-evidence.sh finalize --prepared-manifest FILE [options]

Options:
  --env-file FILE             Local Rabbit Compose environment (default .env)
  --config FILE               Protected M5.5 config (default .env.pilot-m5-5)
  --output DIRECTORY          Local evidence root (default artifacts/pilot-m5-5)
  --prepared-manifest FILE    Passed prepare-manifest.json for finalization

This runner is read-only against Rabbit. It never records the institution's
decision, signs on behalf of a person, changes Pilot readiness, exposes a public
endpoint, or sends evidence to a cloud service.
EOF
}

(($# > 0)) || { usage >&2; exit 2; }
case "$1" in
  prepare|finalize) mode="$1"; shift ;;
  -h|--help) usage; exit 0 ;;
  *) echo "First argument must be prepare or finalize." >&2; usage >&2; exit 2 ;;
esac

while (($# > 0)); do
  case "$1" in
    --env-file) (($# >= 2)) || { usage >&2; exit 2; }; env_file="$2"; shift 2 ;;
    --config) (($# >= 2)) || { usage >&2; exit 2; }; config_file="$2"; shift 2 ;;
    --output) (($# >= 2)) || { usage >&2; exit 2; }; evidence_root="$2"; shift 2 ;;
    --prepared-manifest) (($# >= 2)) || { usage >&2; exit 2; }; prepared_manifest="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ "${mode}" == finalize && -z "${prepared_manifest}" ]]; then
  echo "--prepared-manifest is required for finalization." >&2
  exit 2
fi
if [[ "${mode}" == prepare && -n "${prepared_manifest}" ]]; then
  echo "--prepared-manifest is valid only for finalization." >&2
  exit 2
fi

for command_name in docker git python3 sha256sum; do
  command -v "${command_name}" >/dev/null 2>&1 \
    || { echo "${command_name} is required." >&2; exit 1; }
done
docker info >/dev/null 2>&1 \
  || { echo "Docker Engine is unavailable." >&2; exit 1; }
[[ -z "$(git -C "${repo_root}" status --porcelain)" ]] \
  || { echo "M5.5 evidence requires a clean committed release worktree." >&2; exit 1; }

absolute_file() {
  local path="$1"
  if [[ "${path}" != /* ]]; then path="${repo_root}/${path}"; fi
  [[ -f "${path}" ]] || return 1
  (cd "$(dirname "${path}")" && printf '%s/%s\n' "$PWD" "$(basename "${path}")")
}

env_value() {
  local key="$1"
  awk -v key="${key}" '
    $0 !~ /^[[:space:]]*#/ && index($0, key "=") == 1 {
      sub(/^[^=]*=/, ""); gsub(/\r$/, "");
      if (($0 ~ /^".*"$/) || ($0 ~ /^\047.*\047$/)) $0 = substr($0, 2, length($0) - 2)
      print; exit
    }
  ' "${config_file}"
}

env_file="$(absolute_file "${env_file}")" \
  || { echo "Local pilot .env was not found." >&2; exit 1; }
config_file="$(absolute_file "${config_file}")" \
  || { echo "Protected M5.5 config was not found." >&2; exit 1; }

check_protected_file() {
  local path="$1" label="$2" permissions
  permissions="$(stat -c '%a' "${path}" 2>/dev/null || stat -f '%Lp' "${path}" 2>/dev/null || printf unknown)"
  [[ "${permissions}" == 600 || "${permissions}" == 400 ]] \
    || { echo "${label} must have mode 600 or 400; found ${permissions}." >&2; exit 1; }
  if [[ "${path}" == "${repo_root}/"* ]]; then
    git -C "${repo_root}" check-ignore --quiet "${path}" \
      || { echo "${label} must be ignored by Git." >&2; exit 1; }
  fi
}

check_protected_file "${env_file}" ".env"
check_protected_file "${config_file}" ".env.pilot-m5-5"
if [[ "${mode}" == prepare ]]; then
  for key in PILOT_M5_5_SIGNED_ACCEPTANCE_FILE PILOT_M5_5_INCIDENT_FILE PILOT_M5_5_KNOWN_ISSUES_FILE; do
    protected_file="$(absolute_file "$(env_value "${key}")")" \
      || { echo "${key} does not resolve to a file." >&2; exit 1; }
    check_protected_file "${protected_file}" "${key}"
  done
  backup_directory="$(env_value PILOT_M5_5_BACKUP_DIRECTORY)"
  if [[ "${backup_directory}" != /* ]]; then backup_directory="${repo_root}/${backup_directory}"; fi
  "${repo_root}/infra/backup/verify.sh" "${backup_directory}" >/dev/null
fi

if [[ -n "${prepared_manifest}" ]]; then
  prepared_manifest="$(absolute_file "${prepared_manifest}")" \
    || { echo "Prepared M5.5 manifest was not found." >&2; exit 1; }
  [[ "$(basename "${prepared_manifest}")" == prepare-manifest.json ]] \
    || { echo "--prepared-manifest must point to prepare-manifest.json." >&2; exit 1; }
fi

"${repo_root}/infra/architecture/verify-local-only.sh" >/dev/null
compose=(
  docker compose --env-file "${env_file}"
  -f "${repo_root}/docker-compose.yml"
  -f "${repo_root}/infra/pilot/compose.local-pilot.yml"
)
running_services="$("${compose[@]}" ps --services --status running 2>/dev/null || true)"
for service in postgres redis rabbitmq minio backend frontend nginx; do
  grep -Fxq "${service}" <<<"${running_services}" \
    || { echo "Rabbit service is not running: ${service}" >&2; exit 1; }
done

evidence_root="$(mkdir -p "${evidence_root}" && cd "${evidence_root}" && pwd -P)"
command=(
  python3 "${repo_root}/infra/pilot/m5-5-closeout.py" "${mode}"
  --repo-root "${repo_root}"
  --config "${config_file}"
  --output "${evidence_root}"
)
if [[ -n "${prepared_manifest}" ]]; then
  command+=(--prepared-manifest "${prepared_manifest}")
fi
"${command[@]}"
