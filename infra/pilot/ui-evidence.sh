#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
env_file="${PILOT_UI_ENV_FILE:-${repo_root}/.env.pilot-ui}"
headed=false
project=""

usage() {
  cat <<'EOF'
Usage: ./infra/pilot/ui-evidence.sh [options]

Options:
  --env-file PATH   Protected pilot UI account file (default: .env.pilot-ui)
  --headed          Show the local browser while the evidence run executes
  --project NAME    Run one Playwright project only
  -h, --help        Show this help

The target must be localhost or one explicit private-LAN IPv4 address. The
workflow is read-only: it opens authorised screens, checks accessibility and
overflow, and captures screenshots. Human/state-changing checks remain manual.
EOF
}

while (($# > 0)); do
  case "$1" in
    --env-file)
      (($# >= 2)) || { usage >&2; exit 2; }
      env_file="$2"
      shift 2
      ;;
    --headed)
      headed=true
      shift
      ;;
    --project)
      (($# >= 2)) || { usage >&2; exit 2; }
      project="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

env_value() {
  local key="$1"
  local value
  value="$(awk -v key="${key}" '
    $0 !~ /^[[:space:]]*#/ && index($0, key "=") == 1 {
      sub(/^[^=]*=/, ""); print; exit
    }
  ' "${env_file}" 2>/dev/null || true)"
  value="${value%$'\r'}"
  if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "${value}"
}

is_private_or_loopback_ipv4() {
  local address="$1"
  local a b c d
  IFS=. read -r a b c d <<<"${address}"
  [[ -n "${a:-}" && -n "${b:-}" && -n "${c:-}" && -n "${d:-}" ]] || return 1
  for octet in "$a" "$b" "$c" "$d"; do
    [[ "$octet" =~ ^[0-9]+$ ]] || return 1
    ((10#$octet >= 0 && 10#$octet <= 255)) || return 1
  done
  ((10#$a == 127)) && return 0
  ((10#$a == 10)) && return 0
  ((10#$a == 192 && 10#$b == 168)) && return 0
  ((10#$a == 172 && 10#$b >= 16 && 10#$b <= 31)) && return 0
  return 1
}

[[ -f "${env_file}" ]] || {
  echo "Missing ${env_file}. Copy .env.pilot-ui.example and replace every placeholder." >&2
  exit 1
}
permissions="$(stat -c '%a' "${env_file}" 2>/dev/null || stat -f '%Lp' "${env_file}" 2>/dev/null || printf unknown)"
[[ "${permissions}" == 600 || "${permissions}" == 400 ]] || {
  echo "Pilot UI environment file must use mode 600 or 400; found ${permissions}." >&2
  exit 1
}

required_keys=(
  PILOT_UI_BASE_URL PILOT_UI_EXPECTED_ORG_CODE PILOT_UI_TESTER_NAME
  PILOT_UI_ADMIN_EMAIL PILOT_UI_ADMIN_PASSWORD
  PILOT_UI_ACADEMIC_HEAD_EMAIL PILOT_UI_ACADEMIC_HEAD_PASSWORD
  PILOT_UI_TEACHER_EMAIL PILOT_UI_TEACHER_PASSWORD
  PILOT_UI_REVIEWER_EMAIL PILOT_UI_REVIEWER_PASSWORD
  PILOT_UI_STUDENT_EMAIL PILOT_UI_STUDENT_PASSWORD
)
for key in "${required_keys[@]}"; do
  value="$(env_value "${key}")"
  if [[ -z "${value}" || "${value}" == *REPLACE_ME* || "${value}" == *CHANGE_ME* ]]; then
    echo "${key} must be configured in ${env_file}." >&2
    exit 1
  fi
done

base_url="$(env_value PILOT_UI_BASE_URL)"
if [[ ! "${base_url}" =~ ^http://([^/:]+)(:[0-9]+)?/?$ ]]; then
  echo "PILOT_UI_BASE_URL must be an HTTP origin without a path." >&2
  exit 1
fi
host="${BASH_REMATCH[1]}"
if [[ "${host}" != localhost ]] && ! is_private_or_loopback_ipv4 "${host}"; then
  echo "PILOT_UI_BASE_URL must use localhost or one explicit private-LAN IPv4 address." >&2
  exit 1
fi

command -v node >/dev/null 2>&1 || { echo "Node.js is required." >&2; exit 1; }
command -v npm >/dev/null 2>&1 || { echo "npm is required." >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "curl is required." >&2; exit 1; }
[[ -d "${repo_root}/frontend/node_modules/@playwright/test" ]] || {
  echo "Playwright is not installed. Run make pilot-ui-install once on the designated host." >&2
  exit 1
}

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
evidence_dir="${repo_root}/artifacts/pilot-ui/rabbit-m5.2-${stamp}"
mkdir -p "${evidence_dir}"

if ! curl --fail --silent --show-error \
  "${base_url%/}/api/actuator/health/readiness" >"${evidence_dir}/health.json"; then
  echo "Rabbit readiness endpoint is not healthy at ${base_url}." >&2
  exit 1
fi

export PILOT_UI_ENV_FILE="${env_file}"
export PILOT_UI_OUTPUT_DIR="${evidence_dir}"
export PILOT_UI_HEADED="${headed}"

playwright_args=()
if [[ -n "${project}" ]]; then
  playwright_args+=(--project "${project}")
fi

set +e
(
  cd "${repo_root}/frontend"
  npm run test:pilot-ui -- "${playwright_args[@]}"
)
test_status=$?
set -e

result=PASS
((test_status == 0)) || result=FAIL
commit="$(git -C "${repo_root}" rev-parse HEAD)"
worktree_state=clean
[[ -z "$(git -C "${repo_root}" status --porcelain)" ]] || worktree_state=modified

cat >"${evidence_dir}/manifest.md" <<EOF
# Rabbit M5.2 local UI evidence

- Generated (UTC): ${stamp}
- Automated result: ${result}
- Release commit: ${commit}
- Worktree state: ${worktree_state}
- Target origin: ${base_url}
- Expected organisation: $(env_value PILOT_UI_EXPECTED_ORG_CODE)
- Tester: $(env_value PILOT_UI_TESTER_NAME)
- Browser scope: ${project:-all configured local Chromium projects}
- Credentials: intentionally excluded

This evidence covers read-only route access, serious/critical automated
accessibility findings, document overflow, browser/page errors, reduced-motion
handling, and screenshots. It does not constitute institutional approval and
does not replace state-changing journey checks, Edge/Chrome comparison, 200%
browser zoom, screen-reader review, or a representative physical Android device.
EOF

cp "${repo_root}/docs/M5_2_UI_VALIDATION.md" "${evidence_dir}/manual-validation.md"
(
  cd "${evidence_dir}"
  find . -type f ! -name sha256sums.txt -print0 \
    | sort -z \
    | xargs -0 sha256sum >sha256sums.txt
)

echo "M5.2 evidence: ${evidence_dir}"
echo "Automated result: ${result}"
exit "${test_status}"
