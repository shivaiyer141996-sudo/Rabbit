#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
env_file="${repo_root}/.env"
config_file="${repo_root}/.env.pilot-m5-4"
evidence_root="${repo_root}/artifacts/pilot-m5-4"
mode=''
freeze_manifest=''

usage() {
  cat <<'EOF'
Usage: ./infra/pilot/m5-4-evidence.sh freeze [options]
       ./infra/pilot/m5-4-evidence.sh reconcile --freeze-manifest PATH [options]

Options:
  --env-file PATH         Local pilot environment (default: .env)
  --config PATH           Protected M5.4 inputs (default: .env.pilot-m5-4)
  --output PATH           Evidence root (default: artifacts/pilot-m5-4)
  --freeze-manifest PATH  Passed freeze-manifest.json; required for reconcile
  -h, --help              Show help

The command targets only loopback or an explicit private-LAN Rabbit address. It
never creates users, changes an assessment, publishes results, signs the pilot,
uses a public endpoint, or sends evidence to a cloud service.
EOF
}

(($# > 0)) || { usage >&2; exit 2; }
case "$1" in
  freeze|reconcile) mode="$1"; shift ;;
  -h|--help) usage; exit 0 ;;
  *) echo "First argument must be freeze or reconcile." >&2; usage >&2; exit 2 ;;
esac

while (($# > 0)); do
  case "$1" in
    --env-file) (($# >= 2)) || { usage >&2; exit 2; }; env_file="$2"; shift 2 ;;
    --config) (($# >= 2)) || { usage >&2; exit 2; }; config_file="$2"; shift 2 ;;
    --output) (($# >= 2)) || { usage >&2; exit 2; }; evidence_root="$2"; shift 2 ;;
    --freeze-manifest) (($# >= 2)) || { usage >&2; exit 2; }; freeze_manifest="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ "${mode}" == reconcile && -z "${freeze_manifest}" ]]; then
  echo "--freeze-manifest is required for reconciliation." >&2
  exit 2
fi
if [[ "${mode}" == freeze && -n "${freeze_manifest}" ]]; then
  echo "--freeze-manifest is valid only for reconciliation." >&2
  exit 2
fi

for required_file in "${env_file}" "${config_file}"; do
  [[ -f "${required_file}" ]] \
    || { echo "Missing required file: ${required_file}" >&2; exit 1; }
done
for command_name in docker git python3 sha256sum; do
  command -v "${command_name}" >/dev/null 2>&1 \
    || { echo "${command_name} is required." >&2; exit 1; }
done
docker info >/dev/null 2>&1 \
  || { echo "Docker Engine is unavailable." >&2; exit 1; }
[[ -z "$(git -C "${repo_root}" status --porcelain)" ]] \
  || { echo "M5.4 evidence requires a clean committed release worktree." >&2; exit 1; }

absolute_file() {
  local path="$1"
  if [[ "${path}" != /* ]]; then
    path="${repo_root}/${path}"
  fi
  [[ -f "${path}" ]] || return 1
  (cd "$(dirname "${path}")" && printf '%s/%s\n' "$PWD" "$(basename "${path}")")
}

absolute_directory() {
  local path="$1"
  if [[ "${path}" != /* ]]; then
    path="${repo_root}/${path}"
  fi
  [[ -d "${path}" ]] || return 1
  (cd "${path}" && pwd -P)
}

env_value() {
  local key="$1"
  awk -v key="${key}" '
    $0 !~ /^[[:space:]]*#/ && index($0, key "=") == 1 {
      sub(/^[^=]*=/, ""); gsub(/\r$/, "");
      if (($0 ~ /^".*"$/) || ($0 ~ /^\047.*\047$/)) {
        $0 = substr($0, 2, length($0) - 2)
      }
      print; exit
    }
  ' "${config_file}"
}

config_file="$(absolute_file "${config_file}")" \
  || { echo "Protected M5.4 config was not found." >&2; exit 1; }
env_file="$(absolute_file "${env_file}")" \
  || { echo "Local pilot environment was not found." >&2; exit 1; }

check_protected_file() {
  local path="$1" label="$2" permissions
  permissions="$(stat -c '%a' "${path}" 2>/dev/null \
    || stat -f '%Lp' "${path}" 2>/dev/null \
    || printf unknown)"
  [[ "${permissions}" == 600 || "${permissions}" == 400 ]] \
    || { echo "${label} must have mode 600 or 400; found ${permissions}." >&2; exit 1; }
  git -C "${repo_root}" check-ignore --quiet "${path}" \
    || { echo "${label} must be ignored by Git." >&2; exit 1; }
}

check_protected_file "${env_file}" ".env"
check_protected_file "${config_file}" ".env.pilot-m5-4"

for key in PILOT_M5_4_ROSTER_FILE PILOT_M5_4_INCIDENT_FILE; do
  configured_path="$(env_value "${key}")"
  if [[ "${key}" == PILOT_M5_4_INCIDENT_FILE && "${mode}" == freeze ]]; then
    continue
  fi
  protected_path="$(absolute_file "${configured_path}")" \
    || { echo "${key} does not resolve to a file." >&2; exit 1; }
  check_protected_file "${protected_path}" "${key}"
done

if [[ "${mode}" == freeze ]]; then
  backup_directory="$(absolute_directory "$(env_value PILOT_M5_4_BACKUP_DIRECTORY)")" \
    || { echo "PILOT_M5_4_BACKUP_DIRECTORY does not resolve to a directory." >&2; exit 1; }
  "${repo_root}/infra/backup/verify.sh" "${backup_directory}" >/dev/null
fi

if [[ -n "${freeze_manifest}" ]]; then
  freeze_manifest="$(absolute_file "${freeze_manifest}")" \
    || { echo "Freeze manifest was not found." >&2; exit 1; }
  [[ "$(basename "${freeze_manifest}")" == freeze-manifest.json ]] \
    || { echo "--freeze-manifest must point to freeze-manifest.json." >&2; exit 1; }
  checksum_file="$(dirname "${freeze_manifest}")/SHA256SUMS"
  [[ -f "${checksum_file}" ]] \
    || { echo "Freeze bundle has no SHA256SUMS." >&2; exit 1; }
  (
    cd "$(dirname "${freeze_manifest}")"
    sha256sum --check --quiet SHA256SUMS
  ) || { echo "Freeze bundle checksum verification failed." >&2; exit 1; }
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
  python3 "${repo_root}/infra/pilot/m5-4-pilot.py" "${mode}"
  --repo-root "${repo_root}"
  --config "${config_file}"
  --output "${evidence_root}"
)
if [[ -n "${freeze_manifest}" ]]; then
  command+=(--freeze-manifest "${freeze_manifest}")
fi
"${command[@]}"
