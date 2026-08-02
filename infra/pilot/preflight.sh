#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
env_file="${repo_root}/.env"
evidence_parent="${repo_root}/artifacts/pilot-preflight"
runtime_check=false

usage() {
  cat <<'EOF'
Usage: ./infra/pilot/preflight.sh [options]

Options:
  --env-file PATH   Pilot environment file (default: .env)
  --output PATH     Parent directory for evidence
  --runtime         Require a running seven-service stack and verify health
  -h, --help        Show this help

The script is read-only with respect to Rabbit data. It writes a redacted host,
configuration, and runtime evidence bundle under artifacts/ by default.
EOF
}

while (($# > 0)); do
  case "$1" in
    --env-file)
      (($# >= 2)) || { usage >&2; exit 2; }
      env_file="$2"
      shift 2
      ;;
    --output)
      (($# >= 2)) || { usage >&2; exit 2; }
      evidence_parent="$2"
      shift 2
      ;;
    --runtime)
      runtime_check=true
      shift
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

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
evidence_dir="${evidence_parent}/rabbit-m5.1-${stamp}"
mkdir -p "${evidence_dir}"
report_file="${evidence_dir}/preflight.md"
inventory_file="${evidence_dir}/release-manifest.txt"
architecture_file="${evidence_dir}/architecture-check.txt"
failures=0
warnings=0

escape_markdown() {
  local value="$1"
  value="${value//$'\n'/ }"
  value="${value//|/\\|}"
  printf '%s' "${value}"
}

record() {
  local status="$1"
  local check="$2"
  local detail="$3"
  printf '| %s | %s | %s |\n' \
    "$(escape_markdown "${status}")" \
    "$(escape_markdown "${check}")" \
    "$(escape_markdown "${detail}")" >>"${report_file}"
  case "${status}" in
    FAIL) failures=$((failures + 1)) ;;
    WARN) warnings=$((warnings + 1)) ;;
  esac
}

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

secret_check() {
  local key="$1"
  local minimum_length="$2"
  local value lower
  value="$(env_value "${key}")"
  lower="${value,,}"
  if ((${#value} < minimum_length)) \
    || [[ "${lower}" == *replace* ]] \
    || [[ "${lower}" == *change-me* ]] \
    || [[ "${lower}" == *rabbit_local* ]] \
    || [[ "${lower}" == *password* ]]; then
    record FAIL "Secret: ${key}" "Missing, placeholder, known default, or shorter than ${minimum_length} characters."
  else
    record PASS "Secret: ${key}" "Present and passes local strength checks; value redacted."
  fi
}

cat >"${report_file}" <<EOF
# Rabbit M5.1 local pilot preflight

- Generated (UTC): ${stamp}
- Evidence type: local host, configuration, isolation, persistence, and runtime readiness
- Data policy: secret values are never included

| Status | Check | Evidence |
| --- | --- | --- |
EOF

if [[ -f "${env_file}" ]]; then
  record PASS 'Pilot environment file' "Found ${env_file}."
else
  record FAIL 'Pilot environment file' "Missing ${env_file}; run ./infra/pilot/prepare-local-env.sh."
fi

if [[ -f "${env_file}" ]]; then
  if git -C "${repo_root}" check-ignore --quiet "${env_file}" 2>/dev/null; then
    record PASS 'Secret-file Git exclusion' 'Environment file is ignored by Git.'
  else
    record FAIL 'Secret-file Git exclusion' 'Environment file is not ignored by Git.'
  fi

  permissions="$(stat -c '%a' "${env_file}" 2>/dev/null || stat -f '%Lp' "${env_file}" 2>/dev/null || printf unknown)"
  if [[ "${permissions}" == '600' || "${permissions}" == '400' ]]; then
    record PASS 'Secret-file permissions' "Mode ${permissions}."
  else
    record FAIL 'Secret-file permissions' "Expected mode 600 or 400; found ${permissions}."
  fi

  secret_check POSTGRES_PASSWORD 24
  secret_check JWT_SECRET 48
  secret_check MINIO_ROOT_PASSWORD 24
  secret_check RABBITMQ_DEFAULT_PASS 24

  bind_address="$(env_value RABBIT_BIND_ADDRESS)"
  if is_private_or_loopback_ipv4 "${bind_address}"; then
    record PASS 'Web bind address' "Explicit local address ${bind_address}."
    if [[ "${bind_address}" != 127.* ]]; then
      if { command -v ip >/dev/null 2>&1 \
          && ip -o address show 2>/dev/null | grep -Eq "[[:space:]]${bind_address}/"; } \
        || { command -v ifconfig >/dev/null 2>&1 \
          && ifconfig 2>/dev/null | grep -Eq "inet[[:space:]]+${bind_address}([[:space:]]|$)"; }; then
        record PASS 'LAN address ownership' 'The selected private address is assigned to this host.'
      else
        record FAIL 'LAN address ownership' 'The selected private address is not assigned to this host.'
      fi
    fi
  else
    record FAIL 'Web bind address' 'Must be loopback or one explicit RFC1918 IPv4 address; 0.0.0.0/public addresses are forbidden.'
  fi

  database_url="$(env_value DATABASE_URL)"
  if [[ -z "${database_url}" || "${database_url}" == jdbc:postgresql://postgres:* ]]; then
    record PASS 'Database location' 'Bundled Docker PostgreSQL is selected.'
  else
    record FAIL 'Database location' 'M5 must not use an external or managed database.'
  fi

  if [[ "$(env_value REDIS_HOST)" == 'redis' \
      && "$(env_value RABBITMQ_HOST)" == 'rabbitmq' \
      && "$(env_value MINIO_ENDPOINT)" == 'http://minio:9000' ]]; then
    record PASS 'Supporting service locations' 'Redis, RabbitMQ, and MinIO use bundled Docker services.'
  else
    record FAIL 'Supporting service locations' 'M5 must use the bundled local Redis, RabbitMQ, and MinIO services.'
  fi

  if [[ "$(env_value RABBIT_ENVIRONMENT)" == 'pilot-local' ]]; then
    record PASS 'Environment identity' 'RABBIT_ENVIRONMENT is pilot-local.'
  else
    record FAIL 'Environment identity' 'RABBIT_ENVIRONMENT must be pilot-local.'
  fi

  origins="$(env_value CORS_ALLOWED_ORIGINS)"
  origin_failure=false
  IFS=',' read -r -a origin_values <<<"${origins}"
  for origin in "${origin_values[@]}"; do
    origin_host="${origin#*://}"
    origin_host="${origin_host%%/*}"
    origin_host="${origin_host%%:*}"
    if [[ "${origin_host}" == 'localhost' ]] || is_private_or_loopback_ipv4 "${origin_host}"; then
      continue
    fi
    origin_failure=true
  done
  if [[ -n "${origins}" && "${origins}" != *'*'* && "${origin_failure}" == false ]]; then
    record PASS 'Allowed browser origins' 'Every origin is loopback or on the private LAN.'
  else
    record FAIL 'Allowed browser origins' 'Origins must be explicit loopback/private-LAN values without wildcards.'
  fi

  for owner_key in PILOT_HOST_OWNER PILOT_SUPPORT_CONTACT PILOT_ROLLBACK_OWNER PILOT_BACKUP_OWNER; do
    owner_value="$(env_value "${owner_key}")"
    if [[ -n "${owner_value}" && "${owner_value}" != *REPLACE* ]]; then
      record PASS "Owner: ${owner_key}" 'Named in the protected local environment file.'
    else
      record FAIL "Owner: ${owner_key}" 'A reachable human owner is required.'
    fi
  done

  backup_directory="$(env_value PILOT_BACKUP_DIRECTORY)"
  if [[ -n "${backup_directory}" && "${backup_directory}" != *REPLACE* && -d "${backup_directory}" ]]; then
    repo_device="$(stat -c '%d' "${repo_root}" 2>/dev/null || stat -f '%d' "${repo_root}" 2>/dev/null || printf unknown)"
    backup_device="$(stat -c '%d' "${backup_directory}" 2>/dev/null || stat -f '%d' "${backup_directory}" 2>/dev/null || printf unknown)"
    if [[ "${repo_device}" != unknown && "${backup_device}" != unknown && "${repo_device}" != "${backup_device}" ]]; then
      record PASS 'Separate backup device' 'Backup directory exists on a different filesystem device.'
    else
      record FAIL 'Separate backup device' 'Backup path must be on an external disk, USB drive, or second approved computer—not the Rabbit host disk.'
    fi
  else
    record FAIL 'Separate backup device' 'Set PILOT_BACKUP_DIRECTORY to an existing approved separate local device.'
  fi
fi

cpu_count="$(getconf _NPROCESSORS_ONLN 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || printf 0)"
if [[ "${cpu_count}" =~ ^[0-9]+$ ]] && ((cpu_count >= 4)); then
  record PASS 'Host CPU' "${cpu_count} logical CPUs available."
else
  record FAIL 'Host CPU' "At least 4 logical CPUs are required; detected ${cpu_count}."
fi

if [[ -r /proc/meminfo ]]; then
  memory_mb="$(awk '/^MemTotal:/ { print int($2 / 1024); exit }' /proc/meminfo)"
else
  memory_bytes="$(sysctl -n hw.memsize 2>/dev/null || printf 0)"
  memory_mb=$((memory_bytes / 1024 / 1024))
fi
if [[ "${memory_mb}" =~ ^[0-9]+$ ]] && ((memory_mb >= 8192)); then
  record PASS 'Host memory' "${memory_mb} MiB available; 16384 MiB remains recommended."
else
  record FAIL 'Host memory' "At least 8192 MiB is required; detected ${memory_mb:-0} MiB."
fi

disk_mb="$(df -Pk "${repo_root}" | awk 'NR == 2 { print int($4 / 1024); exit }')"
if [[ "${disk_mb}" =~ ^[0-9]+$ ]] && ((disk_mb >= 30720)); then
  record PASS 'Host free disk' "${disk_mb} MiB free on the Rabbit filesystem."
else
  record FAIL 'Host free disk' "At least 30720 MiB free is required; detected ${disk_mb:-0} MiB."
fi

release_commit="$(git -C "${repo_root}" rev-parse HEAD 2>/dev/null || printf unversioned)"
release_branch="$(git -C "${repo_root}" branch --show-current 2>/dev/null || printf unknown)"
if [[ -z "$(git -C "${repo_root}" status --porcelain 2>/dev/null)" ]]; then
  record PASS 'Release worktree' "Clean at ${release_commit}."
else
  record FAIL 'Release worktree' 'Commit or remove pending changes before capturing pilot evidence.'
fi

if "${repo_root}/infra/architecture/verify-local-only.sh" >"${architecture_file}" 2>&1; then
  record PASS 'Local-only architecture policy' 'Required local services, network isolation, and dependency boundary pass.'
else
  record FAIL 'Local-only architecture policy' "See $(basename "${architecture_file}")."
fi

docker_version='unavailable'
compose_version='unavailable'
runtime_summary='not requested'
runtime_images='not-captured'
if command -v docker >/dev/null 2>&1; then
  docker_version="$(docker --version 2>/dev/null || printf unavailable)"
  compose_version="$(docker compose version 2>/dev/null || printf unavailable)"
  if docker info >/dev/null 2>&1; then
    record PASS 'Docker Engine' "${docker_version}."
    record PASS 'Docker Compose' "${compose_version}."
    if [[ -f "${env_file}" ]] && docker compose \
      --env-file "${env_file}" \
      -f "${repo_root}/docker-compose.yml" \
      -f "${repo_root}/infra/pilot/compose.local-pilot.yml" \
      config --quiet; then
      record PASS 'Merged pilot configuration' 'Base and local-pilot Compose files validate.'
    else
      record FAIL 'Merged pilot configuration' 'Docker Compose configuration validation failed.'
    fi
  else
    record FAIL 'Docker Engine' 'Docker is installed but the engine is unavailable.'
  fi
else
  record FAIL 'Docker Engine' 'Docker Engine/Desktop and Compose are required on the designated host.'
fi

if [[ "${runtime_check}" == true ]]; then
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 && [[ -f "${env_file}" ]]; then
    compose=(docker compose --env-file "${env_file}" -f "${repo_root}/docker-compose.yml" -f "${repo_root}/infra/pilot/compose.local-pilot.yml")
    running_services="$("${compose[@]}" ps --services --status running 2>/dev/null || true)"
    runtime_failure=false
    for service in postgres redis rabbitmq minio backend frontend nginx; do
      if grep -Fxq "${service}" <<<"${running_services}"; then
        record PASS "Running service: ${service}" 'Container is running.'
      else
        record FAIL "Running service: ${service}" 'Container is not running.'
        runtime_failure=true
      fi
    done
    bind_address="$(env_value RABBIT_BIND_ADDRESS)"
    http_port="$(env_value RABBIT_HTTP_PORT)"
    http_port="${http_port:-80}"
    if command -v curl >/dev/null 2>&1 \
      && curl --fail --silent --show-error \
        "http://${bind_address}:${http_port}/api/actuator/health/readiness" \
        >"${evidence_dir}/readiness.json"; then
      record PASS 'Rabbit readiness endpoint' 'Gateway and backend report ready.'
    else
      record FAIL 'Rabbit readiness endpoint' 'Readiness could not be confirmed through the bound gateway.'
      runtime_failure=true
    fi
    for mapping in 'postgres 5432' 'redis 6379' 'rabbitmq 5672' 'rabbitmq 15672' 'minio 9000' 'minio 9001'; do
      read -r service container_port <<<"${mapping}"
      if "${compose[@]}" port "${service}" "${container_port}" 2>/dev/null | grep -q .; then
        record FAIL "Private port: ${service}/${container_port}" 'Infrastructure port is published to the host.'
        runtime_failure=true
      else
        record PASS "Private port: ${service}/${container_port}" 'Docker-internal only.'
      fi
    done
    active_demo_users="$(
      "${compose[@]}" exec -T postgres sh -eu -c \
        'psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
          --tuples-only --no-align \
          --command "SELECT COUNT(*) FROM user_accounts \
            WHERE email LIKE '\''%@demo.rabbit.local'\'' AND status = '\''ACTIVE'\''"' \
        2>/dev/null | tr -d '[:space:]' || printf unknown
    )"
    if [[ "${active_demo_users}" == '0' ]]; then
      record PASS 'Demo identity retirement' 'No seeded demo account remains active.'
    else
      record FAIL 'Demo identity retirement' "${active_demo_users} seeded demo account(s) remain active; activate a replacement administrator, then run make pilot-retire-demo-users."
      runtime_failure=true
    fi
    "${compose[@]}" ps >"${evidence_dir}/compose-ps.txt" 2>&1 || true
    "${compose[@]}" images >"${evidence_dir}/compose-images.txt" 2>&1 || true
    runtime_images=''
    for service in postgres redis rabbitmq minio backend frontend nginx; do
      container_id="$("${compose[@]}" ps -q "${service}" 2>/dev/null || true)"
      if [[ -n "${container_id}" ]]; then
        image_reference="$(docker inspect --format '{{.Config.Image}}' "${container_id}" 2>/dev/null || printf unknown)"
        image_id="$(docker inspect --format '{{.Image}}' "${container_id}" 2>/dev/null || printf unknown)"
        runtime_images+="${service}=${image_reference}@${image_id};"
      fi
    done
    runtime_summary="$([[ "${runtime_failure}" == false ]] && printf passed || printf failed)"
  else
    record FAIL 'Runtime verification' 'Runtime checks requested, but Docker or the protected environment file is unavailable.'
    runtime_summary='unavailable'
  fi
else
  record WARN 'Runtime verification' 'Not requested. Run with --runtime after make pilot-up.'
fi

cat >"${inventory_file}" <<EOF
Rabbit M5.1 local release manifest
generated_at_utc=${stamp}
release_commit=${release_commit}
release_branch=${release_branch}
host=$(uname -a 2>/dev/null || printf unknown)
cpu_logical=${cpu_count}
memory_mib=${memory_mb:-0}
free_disk_mib=${disk_mb:-0}
docker=${docker_version}
compose=${compose_version}
runtime_check=${runtime_summary}
runtime_images=${runtime_images}
architecture_policy=docs/LOCAL_INFRASTRUCTURE_POLICY.md
database_mode=bundled-local-postgresql-16
storage_mode=local-docker-named-volumes
cloud_runtime=disabled
EOF

printf '\n## Result\n\n- Failures: %d\n- Warnings: %d\n' "${failures}" "${warnings}" >>"${report_file}"
(
  cd "${evidence_dir}"
  for evidence_file in *; do
    [[ "${evidence_file}" == 'SHA256SUMS' || ! -f "${evidence_file}" ]] && continue
    sha256sum "${evidence_file}"
  done | sort >SHA256SUMS
)

echo "Preflight evidence: ${evidence_dir}"
if ((failures > 0)); then
  echo "M5.1 preflight failed with ${failures} mandatory finding(s)." >&2
  exit 1
fi
echo "M5.1 preflight passed with ${warnings} warning(s)."
