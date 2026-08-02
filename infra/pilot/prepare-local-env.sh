#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
bind_address="${1:-127.0.0.1}"
env_file="${2:-${repo_root}/.env}"

usage() {
  cat >&2 <<'EOF'
Usage: ./infra/pilot/prepare-local-env.sh [private-lan-ip] [env-file]

Examples:
  ./infra/pilot/prepare-local-env.sh
  ./infra/pilot/prepare-local-env.sh 192.168.1.25

The default is loopback-only. A LAN address must be an explicit RFC1918 IPv4
address assigned to the designated Rabbit host. The script never accepts
0.0.0.0 or a public IP and never overwrites an existing environment file.
EOF
  exit 2
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

[[ "${bind_address}" != "--help" && "${bind_address}" != "-h" ]] || usage
if ! is_private_or_loopback_ipv4 "${bind_address}"; then
  echo "Bind address must be loopback or one explicit RFC1918 IPv4 address." >&2
  exit 2
fi
if [[ -e "${env_file}" ]]; then
  echo "Refusing to overwrite existing environment file: ${env_file}" >&2
  exit 1
fi
if ! command -v openssl >/dev/null 2>&1; then
  echo "OpenSSL is required to generate local pilot secrets." >&2
  exit 1
fi

env_parent="$(dirname "${env_file}")"
mkdir -p "${env_parent}"
umask 077
temporary_file="$(mktemp "${env_file}.tmp.XXXXXX")"
trap 'rm -f "${temporary_file}"' EXIT HUP INT TERM

postgres_password="$(openssl rand -hex 24)"
jwt_secret="$(openssl rand -hex 48)"
minio_password="$(openssl rand -hex 24)"
rabbitmq_password="$(openssl rand -hex 24)"
release_commit="$(git -C "${repo_root}" rev-parse --short=12 HEAD 2>/dev/null || printf 'unversioned')"
portal_origin="http://${bind_address}"

cat >"${temporary_file}" <<EOF
# Generated local-only Rabbit pilot environment. Do not commit this file.
# Created for bind address ${bind_address}; infrastructure remains Docker-internal.
POSTGRES_DB=rabbit
POSTGRES_USER=rabbit_runtime
POSTGRES_PASSWORD=${postgres_password}

DATABASE_MAX_POOL_SIZE=20
DATABASE_MIN_IDLE=2
DATABASE_CONNECTION_TIMEOUT_MS=10000
DATABASE_VALIDATION_TIMEOUT_MS=3000
DATABASE_MAX_LIFETIME_MS=1800000
DATABASE_MIGRATION_LOCATIONS=classpath:db/migration

JWT_SECRET=${jwt_secret}
JWT_ACCESS_TTL_MINUTES=15
JWT_REFRESH_TTL_DAYS=7
LOGIN_MAX_FAILED_ATTEMPTS=5
LOGIN_LOCK_DURATION=PT30M
INVITATION_TTL=PT72H
INVITATION_ACTIVATION_BASE_URL=${portal_origin}/activate

MINIO_ROOT_USER=rabbit_assets
MINIO_ROOT_PASSWORD=${minio_password}
MINIO_BUCKET=question-assets
MINIO_ENDPOINT=http://minio:9000

RABBITMQ_DEFAULT_USER=rabbit_events
RABBITMQ_DEFAULT_PASS=${rabbitmq_password}
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672

REDIS_HOST=redis
REDIS_PORT=6379

NEXT_PUBLIC_API_BASE_URL=/gateway/backend
BACKEND_INTERNAL_URL=http://backend:8080/api/v1
SESSION_COOKIE_SECURE=false
SPRING_PROFILES_ACTIVE=docker

RABBIT_BIND_ADDRESS=${bind_address}
RABBIT_HTTP_PORT=80
RABBIT_RELEASE_VERSION=1.0.0-pilot-${release_commit}
RABBIT_ENVIRONMENT=pilot-local
RATE_LIMIT_ENABLED=true
RATE_LIMIT_ANONYMOUS_PER_MINUTE=20
RATE_LIMIT_AUTHENTICATED_PER_MINUTE=300
CORS_ALLOWED_ORIGINS=${portal_origin},http://127.0.0.1,http://localhost

# M5.1 evidence inputs. Replace every placeholder before preflight can pass.
PILOT_HOST_OWNER=REPLACE_ME
PILOT_SUPPORT_CONTACT=REPLACE_ME
PILOT_ROLLBACK_OWNER=REPLACE_ME
PILOT_BACKUP_OWNER=REPLACE_ME
PILOT_BACKUP_DIRECTORY=REPLACE_WITH_SEPARATE_LOCAL_DEVICE_PATH
EOF

chmod 600 "${temporary_file}"
mv "${temporary_file}" "${env_file}"
trap - EXIT HUP INT TERM

echo "Created protected local pilot environment: ${env_file}"
echo "Rabbit web bind: ${portal_origin}"
echo "Edit only the PILOT_* ownership and separate-backup placeholders before preflight."
echo "Create .env.pilot-m5-3 from its example only when M5.3 evidence is due."
echo "No secret values were printed."
