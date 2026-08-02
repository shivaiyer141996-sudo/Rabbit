#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
env_file="${repo_root}/.env"
config_file="${repo_root}/.env.pilot-m5-3"
evidence_root="${repo_root}/artifacts/pilot-m5-3"
run_id="rabbit-m5.3-$(date -u +%Y%m%dT%H%M%SZ)"

usage() {
  cat <<'EOF'
Usage: ./infra/security/pilot-security-review.sh [options]

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
for command_name in docker git sha256sum; do
  command -v "${command_name}" >/dev/null 2>&1 \
    || { echo "${command_name} is required." >&2; exit 1; }
done
docker info >/dev/null 2>&1 || { echo "Docker Engine is unavailable." >&2; exit 1; }

config_file="$(cd "$(dirname "${config_file}")" && pwd -P)/$(basename "${config_file}")"
evidence_root="$(mkdir -p "${evidence_root}" && cd "${evidence_root}" && pwd -P)"
run_dir="${evidence_root}/${run_id}"
mkdir -p "${run_dir}"
report_file="${run_dir}/security-review.md"
failures=0
scan_tmp="$(mktemp -d /tmp/rabbit-m5-3-scan.XXXXXX)"

cleanup() {
  case "${scan_tmp}" in
    /tmp/rabbit-m5-3-scan.*) rm -rf -- "${scan_tmp}" ;;
  esac
}
trap cleanup EXIT
trap 'exit 130' HUP INT TERM

escape_markdown() {
  local value="$1"
  value="${value//$'\n'/ }"
  value="${value//|/\\|}"
  printf '%s' "${value}"
}

record() {
  local status="$1" check="$2" detail="$3"
  printf '| %s | %s | %s |\n' \
    "$(escape_markdown "${status}")" \
    "$(escape_markdown "${check}")" \
    "$(escape_markdown "${detail}")" >>"${report_file}"
  [[ "${status}" != FAIL ]] || failures=$((failures + 1))
}

file_value() {
  local file="$1" key="$2" value
  value="$(awk -v key="${key}" '
    $0 !~ /^[[:space:]]*#/ && index($0, key "=") == 1 {
      sub(/^[^=]*=/, ""); gsub(/\r$/, ""); print; exit
    }
  ' "${file}")"
  if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "${value}"
}

cat >"${report_file}" <<EOF
# Rabbit M5.3 local security review

- Generated (UTC): $(date -u +%Y%m%dT%H%M%SZ)
- Release commit: $(git -C "${repo_root}" rev-parse HEAD)
- Reviewer: $(escape_markdown "$(file_value "${config_file}" PILOT_SECURITY_REVIEWER)")
- Scope: local Compose isolation, runtime controls, HTTP controls, secret containment, and critical image vulnerabilities
- Secret policy: secret values are never written to evidence

| Status | Check | Evidence |
| --- | --- | --- |
EOF

reviewer="$(file_value "${config_file}" PILOT_SECURITY_REVIEWER)"
if [[ -n "${reviewer}" && "${reviewer}" != *REPLACE* ]]; then
  record PASS 'Named security reviewer' 'Recorded in the protected M5.3 configuration.'
else
  record FAIL 'Named security reviewer' 'PILOT_SECURITY_REVIEWER is required.'
fi

for protected_file in "${env_file}" "${config_file}"; do
  permissions="$(stat -c '%a' "${protected_file}" 2>/dev/null || stat -f '%Lp' "${protected_file}" 2>/dev/null || printf unknown)"
  if [[ "${permissions}" == 600 || "${permissions}" == 400 ]]; then
    record PASS "Protected file: $(basename "${protected_file}")" "Mode ${permissions}."
  else
    record FAIL "Protected file: $(basename "${protected_file}")" "Expected mode 600 or 400; found ${permissions}."
  fi
  if git -C "${repo_root}" check-ignore --quiet "${protected_file}"; then
    record PASS "Git exclusion: $(basename "${protected_file}")" 'Ignored by Git.'
  else
    record FAIL "Git exclusion: $(basename "${protected_file}")" 'File is not ignored by Git.'
  fi
done

secret_leak=false
for key in POSTGRES_PASSWORD JWT_SECRET MINIO_ROOT_PASSWORD RABBITMQ_DEFAULT_PASS; do
  value="$(file_value "${env_file}" "${key}")"
  if [[ -z "${value}" || "${value}" == *REPLACE* ]]; then
    secret_leak=true
  elif git -C "${repo_root}" grep --quiet -F -e "${value}" -- . 2>/dev/null; then
    secret_leak=true
  fi
done
for key in RABBIT_LOAD_STUDENT_PASSWORD RABBIT_RECOVERY_ADMIN_PASSWORD RABBIT_RECOVERY_STUDENT_PASSWORD; do
  value="$(file_value "${config_file}" "${key}")"
  if [[ -z "${value}" || "${value}" == *REPLACE* ]]; then
    secret_leak=true
  elif git -C "${repo_root}" grep --quiet -F -e "${value}" -- . 2>/dev/null; then
    secret_leak=true
  fi
done
if [[ "${secret_leak}" == false ]]; then
  record PASS 'Generated secret containment' 'Configured secret values do not occur in tracked files.'
else
  record FAIL 'Generated secret containment' 'A required secret is missing/placeholder or a configured value occurs in tracked files.'
fi

if [[ -z "$(git -C "${repo_root}" status --porcelain)" ]]; then
  record PASS 'Release worktree' 'Clean committed release state.'
else
  record FAIL 'Release worktree' 'Commit or remove pending changes before evidence capture.'
fi

if "${repo_root}/infra/architecture/verify-local-only.sh" >"${run_dir}/architecture-check.txt" 2>&1; then
  record PASS 'Local-only architecture policy' 'Repository architecture guard passed.'
else
  record FAIL 'Local-only architecture policy' 'See architecture-check.txt.'
fi

export M5_3_EVIDENCE_ROOT="${evidence_root}"
export M5_3_EVIDENCE_RUN_ID="${run_id}"
export M5_3_SCAN_ROOT="${scan_tmp}"
compose=(
  docker compose --env-file "${env_file}"
  -f "${repo_root}/docker-compose.yml"
  -f "${repo_root}/infra/pilot/compose.local-pilot.yml"
  -f "${repo_root}/infra/security/compose.pilot-security.yml"
)
running_services="$("${compose[@]}" ps --services --status running 2>/dev/null || true)"
for service in postgres redis rabbitmq minio backend frontend nginx; do
  if grep -Fxq "${service}" <<<"${running_services}"; then
    record PASS "Running service: ${service}" 'Container is running.'
  else
    record FAIL "Running service: ${service}" 'Container is not running.'
  fi
done

for mapping in 'postgres 5432' 'redis 6379' 'rabbitmq 5672' 'rabbitmq 15672' 'minio 9000' 'minio 9001'; do
  read -r service container_port <<<"${mapping}"
  if "${compose[@]}" port "${service}" "${container_port}" 2>/dev/null | grep -q .; then
    record FAIL "Private port: ${service}/${container_port}" 'Infrastructure port is published to the host.'
  else
    record PASS "Private port: ${service}/${container_port}" 'Docker-internal only.'
  fi
done

for service in postgres redis rabbitmq minio backend frontend nginx; do
  container_id="$("${compose[@]}" ps -q "${service}" 2>/dev/null || true)"
  if [[ -z "${container_id}" ]]; then
    continue
  fi
  privileged="$(docker inspect --format '{{.HostConfig.Privileged}}' "${container_id}")"
  if [[ "${privileged}" == false ]]; then
    record PASS "Unprivileged container: ${service}" 'Privileged mode is disabled.'
  else
    record FAIL "Unprivileged container: ${service}" 'Privileged mode is enabled.'
  fi
done
for service in backend frontend; do
  container_id="$("${compose[@]}" ps -q "${service}" 2>/dev/null || true)"
  readonly="$(docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "${container_id}" 2>/dev/null || printf false)"
  if [[ "${readonly}" == true ]]; then
    record PASS "Read-only application root: ${service}" 'Runtime root filesystem is read-only.'
  else
    record FAIL "Read-only application root: ${service}" 'Runtime root filesystem is writable.'
  fi
done

if "${compose[@]}" --profile m5-3-evidence run --rm --no-deps -T security-probe \
    >"${run_dir}/security-http.log" 2>&1; then
  if [[ -s "${run_dir}/security-http.json" ]]; then
    record PASS 'Local HTTP security probe' 'Headers, CORS, anonymous access, TRACE, and rate limiting passed.'
  else
    record FAIL 'Local HTTP security probe' 'Probe returned success without a JSON evidence file.'
  fi
else
  record FAIL 'Local HTTP security probe' 'See security-http.json and security-http.log.'
fi

for service in backend frontend; do
  container_id="$("${compose[@]}" ps -q "${service}" 2>/dev/null || true)"
  image_reference="$(docker inspect --format '{{.Config.Image}}' "${container_id}" 2>/dev/null || true)"
  if [[ -z "${image_reference}" ]]; then
    record FAIL "Critical image scan: ${service}" 'Active image reference is unavailable.'
    continue
  fi
  if ! docker image save --output "${scan_tmp}/${service}.tar" "${image_reference}" \
      >"${run_dir}/image-save-${service}.log" 2>&1; then
    record FAIL "Critical image scan: ${service}" 'Active local image could not be exported for isolated scanning.'
    continue
  fi
  if "${compose[@]}" --profile m5-3-evidence run --rm --no-deps -T security-scan \
      image --scanners vuln --severity CRITICAL --exit-code 1 \
      --format json --output "/evidence/${run_id}/trivy-${service}.json" \
      --input "/scan/${service}.tar" >"${run_dir}/trivy-${service}.log" 2>&1; then
    if [[ -s "${run_dir}/trivy-${service}.json" ]]; then
      record PASS "Critical image scan: ${service}" "No critical vulnerability reported for the active local image."
    else
      record FAIL "Critical image scan: ${service}" 'Scanner returned success without a JSON evidence file.'
    fi
  else
    record FAIL "Critical image scan: ${service}" "Finding or scanner failure; see trivy-${service}.json/log."
  fi
done

cleanup
trap - EXIT HUP INT TERM

printf '\n## Result\n\n- Failures: %d\n' "${failures}" >>"${report_file}"
(
  cd "${run_dir}"
  for evidence_file in *; do
    [[ "${evidence_file}" == SHA256SUMS || ! -f "${evidence_file}" ]] && continue
    sha256sum "${evidence_file}"
  done | sort >SHA256SUMS
)

echo "Security evidence: ${run_dir}"
if ((failures > 0)); then
  echo "M5.3 security review failed with ${failures} finding(s)." >&2
  exit 1
fi
echo "M5.3 security review passed."
