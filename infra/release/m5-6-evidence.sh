#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
env_file="${repo_root}/.env"
config_file="${repo_root}/.env.pilot-m5-6"
prepared_manifest=""

usage() {
  cat <<'EOF'
Usage:
  ./infra/release/m5-6-evidence.sh prepare [options]
  ./infra/release/m5-6-evidence.sh verify-tag --prepared-manifest FILE [options]

Options:
  --env-file FILE             Protected local Rabbit environment (default .env)
  --config FILE               Protected M5.6 config (default .env.pilot-m5-6)
  --prepared-manifest FILE    Passed M5.6 release-manifest.json for tag verification

Prepare is read-only against Rabbit. It validates the exact Go decision and
exports source plus all seven active runtime images to approved local media.
Verify-tag only verifies the human-performed fast-forward and annotated tag.
Neither mode merges, tags, pushes, deploys, or publishes a container image.
EOF
}

(($# > 0)) || { usage >&2; exit 2; }
case "$1" in
  prepare|verify-tag) mode="$1"; shift ;;
  -h|--help) usage; exit 0 ;;
  *) echo "First argument must be prepare or verify-tag." >&2; usage >&2; exit 2 ;;
esac

while (($# > 0)); do
  case "$1" in
    --env-file) (($# >= 2)) || { usage >&2; exit 2; }; env_file="$2"; shift 2 ;;
    --config) (($# >= 2)) || { usage >&2; exit 2; }; config_file="$2"; shift 2 ;;
    --prepared-manifest) (($# >= 2)) || { usage >&2; exit 2; }; prepared_manifest="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ "${mode}" == verify-tag && -z "${prepared_manifest}" ]]; then
  echo "--prepared-manifest is required for verify-tag." >&2
  exit 2
fi
if [[ "${mode}" == prepare && -n "${prepared_manifest}" ]]; then
  echo "--prepared-manifest is valid only for verify-tag." >&2
  exit 2
fi

for command_name in git python3 sha256sum; do
  command -v "${command_name}" >/dev/null 2>&1 \
    || { echo "${command_name} is required." >&2; exit 1; }
done
if [[ "${mode}" == prepare ]]; then
  command -v docker >/dev/null 2>&1 || { echo "docker is required." >&2; exit 1; }
  docker info >/dev/null 2>&1 || { echo "Docker Engine is unavailable." >&2; exit 1; }
fi

[[ -z "$(git -C "${repo_root}" status --porcelain)" ]] \
  || { echo "M5.6 requires a clean committed release worktree." >&2; exit 1; }

absolute_file() {
  local path="$1"
  if [[ "${path}" != /* ]]; then path="${repo_root}/${path}"; fi
  [[ -f "${path}" ]] || return 1
  (cd "$(dirname "${path}")" && printf '%s/%s\n' "$PWD" "$(basename "${path}")")
}

env_file="$(absolute_file "${env_file}")" \
  || { echo "Protected local Rabbit .env was not found." >&2; exit 1; }
config_file="$(absolute_file "${config_file}")" \
  || { echo "Protected M5.6 config was not found." >&2; exit 1; }

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
check_protected_file "${config_file}" ".env.pilot-m5-6"

if [[ -n "${prepared_manifest}" ]]; then
  prepared_manifest="$(absolute_file "${prepared_manifest}")" \
    || { echo "Prepared M5.6 release manifest was not found." >&2; exit 1; }
  [[ "$(basename "${prepared_manifest}")" == release-manifest.json ]] \
    || { echo "--prepared-manifest must point to release-manifest.json." >&2; exit 1; }
fi

"${repo_root}/infra/architecture/verify-local-only.sh" >/dev/null

command=(
  python3 "${repo_root}/infra/release/m5-6-closure.py" "${mode}"
  --repo-root "${repo_root}"
  --env-file "${env_file}"
  --config "${config_file}"
)
if [[ -n "${prepared_manifest}" ]]; then
  command+=(--prepared-manifest "${prepared_manifest}")
fi
"${command[@]}"
