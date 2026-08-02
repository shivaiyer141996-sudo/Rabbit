#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
compose_file="${repo_root}/docker-compose.yml"
failures=0

pass() {
  printf 'PASS  %s\n' "$1"
}

fail() {
  printf 'FAIL  %s\n' "$1" >&2
  failures=$((failures + 1))
}

require_text() {
  local pattern="$1"
  local description="$2"
  if grep -Eq -- "${pattern}" "${compose_file}"; then
    pass "${description}"
  else
    fail "${description}"
  fi
}

service_block() {
  local service="$1"
  awk -v target="${service}" '
    $0 == "  " target ":" { capture = 1; next }
    capture && /^  [a-zA-Z0-9_-]+:$/ { exit }
    capture { print }
  ' "${compose_file}"
}

require_text 'image: postgres:16-alpine' 'Local PostgreSQL 16 is defined'
require_text 'image: redis:[^ ]*-alpine' 'Local Redis is defined'
require_text 'image: rabbitmq:[^ ]*-management-alpine' 'Local RabbitMQ is defined'
require_text 'image: (quay\.io/)?minio/minio:' 'Local MinIO is defined'
require_text 'DATABASE_URL:.*postgres:5432' 'Backend defaults to bundled PostgreSQL'
require_text 'RABBIT_BIND_ADDRESS:-127\.0\.0\.1' 'Web gateway defaults to loopback'
require_text '^  data:$' 'Dedicated data network exists'
require_text '^    internal: true$' 'Data network is Docker-internal'

for service in postgres redis rabbitmq minio; do
  block="$(service_block "${service}")"
  if grep -Eq '^    ports:' <<<"${block}"; then
    fail "${service} publishes a host port in the base stack"
  else
    pass "${service} has no host-published port in the base stack"
  fi
done

for volume in postgres-data redis-data rabbitmq-data minio-data; do
  if grep -Eq "^  ${volume}:$" "${compose_file}"; then
    pass "Persistent volume ${volume} is defined"
  else
    fail "Persistent volume ${volume} is missing"
  fi
done

mapfile -t prohibited_iac < <(
  find "${repo_root}" \
    -path "${repo_root}/.git" -prune -o \
    -path "${repo_root}/frontend/node_modules" -prune -o \
    -path "${repo_root}/backend/target" -prune -o \
    -type f \( \
      -name '*.tf' -o \
      -name 'Pulumi.*' -o \
      -name 'serverless.yml' -o \
      -name 'serverless.yaml' -o \
      -name 'Chart.yaml' -o \
      -name 'kustomization.yaml' \
    \) -print
)
if ((${#prohibited_iac[@]} == 0)); then
  pass 'No cloud/Kubernetes infrastructure-as-code is present'
else
  fail "Prohibited infrastructure-as-code found: ${prohibited_iac[*]}"
fi

dependency_files=(
  "${repo_root}/backend/pom.xml"
  "${repo_root}/frontend/package.json"
)
cloud_dependency_pattern='software\.amazon\.awssdk|com\.amazonaws|azure-|google-cloud|@aws-sdk|"aws-sdk"|@azure/|@google-cloud/|kubernetes-client'
if grep -Eiq -- "${cloud_dependency_pattern}" "${dependency_files[@]}"; then
  fail 'A cloud or Kubernetes SDK is present in application dependencies'
else
  pass 'Application dependencies contain no cloud or Kubernetes SDK'
fi

if grep -R -En --include='*.yml' --include='*.yaml' \
  "(^|[[:space:]\"'])0\\.0\\.0\\.0:" \
  "${repo_root}/docker-compose.yml" \
  "${repo_root}/infra" >/dev/null; then
  fail 'A Compose manifest exposes a service on 0.0.0.0'
else
  pass 'Compose manifests contain no 0.0.0.0 port binding'
fi

if grep -R -Eiq --include='*.yml' --include='*.yaml' --include='*.sh' \
  --exclude='verify-local-only.sh' \
  'trycloudflare|cloudflared|ngrok|localtunnel|serveo' \
  "${repo_root}/.github" "${repo_root}/infra"; then
  fail 'A public-tunnel dependency is present in runtime or automation files'
else
  pass 'Runtime and automation files contain no public-tunnel dependency'
fi

if ((failures > 0)); then
  printf '\nLocal-only architecture verification failed with %d finding(s).\n' "${failures}" >&2
  exit 1
fi

printf '\nLocal-only architecture verification passed.\n'
