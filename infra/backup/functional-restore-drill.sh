#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
env_file="${repo_root}/.env"
config_file="${repo_root}/.env.pilot-m5-3"
evidence_root="${repo_root}/artifacts/pilot-m5-3"
run_id="rabbit-m5.3-$(date -u +%Y%m%dT%H%M%SZ)"
backup_dir=''

usage() {
  cat <<'EOF'
Usage: ./infra/backup/functional-restore-drill.sh <backup-directory> [options]

Options:
  --env-file PATH   Local pilot environment (default: .env)
  --config PATH     Protected M5.3 inputs (default: .env.pilot-m5-3)
  --output PATH     Evidence root (default: artifacts/pilot-m5-3)
  --run-id ID       Existing/new evidence run directory name
  -h, --help        Show help

The drill restores into a uniquely named temporary Compose project, validates
data and non-destructive product journeys, then removes only that temporary project and
its volumes. It never connects the restored application to the live data volumes.
EOF
}

while (($# > 0)); do
  case "$1" in
    --env-file) (($# >= 2)) || { usage >&2; exit 2; }; env_file="$2"; shift 2 ;;
    --config) (($# >= 2)) || { usage >&2; exit 2; }; config_file="$2"; shift 2 ;;
    --output) (($# >= 2)) || { usage >&2; exit 2; }; evidence_root="$2"; shift 2 ;;
    --run-id) (($# >= 2)) || { usage >&2; exit 2; }; run_id="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
    *)
      [[ -z "${backup_dir}" ]] || { echo "Only one backup directory is allowed." >&2; exit 2; }
      backup_dir="$1"
      shift
      ;;
  esac
done

[[ -n "${backup_dir}" ]] || { usage >&2; exit 2; }
[[ "${run_id}" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Unsafe run ID." >&2; exit 2; }
for required_file in "${env_file}" "${config_file}"; do
  [[ -f "${required_file}" ]] || { echo "Missing required file: ${required_file}" >&2; exit 1; }
done
[[ -d "${backup_dir}" ]] || { echo "Backup directory does not exist: ${backup_dir}" >&2; exit 1; }
backup_dir="$(cd "${backup_dir}" && pwd -P)"
"${repo_root}/infra/backup/verify.sh" "${backup_dir}"
[[ -f "${backup_dir}/source-reconciliation.txt" ]] \
  || { echo "M5.3 requires a version 2 backup with source reconciliation." >&2; exit 1; }

for command_name in docker git diff sha256sum; do
  command -v "${command_name}" >/dev/null 2>&1 \
    || { echo "${command_name} is required." >&2; exit 1; }
done
docker info >/dev/null 2>&1 || { echo "Docker Engine is unavailable." >&2; exit 1; }

config_file="$(cd "$(dirname "${config_file}")" && pwd -P)/$(basename "${config_file}")"
evidence_root="$(mkdir -p "${evidence_root}" && cd "${evidence_root}" && pwd -P)"
run_dir="${evidence_root}/${run_id}"
mkdir -p "${run_dir}"
started_epoch="$(date -u +%s)"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
project="rabbitrestore${stamp//[^0-9]/}$$"

file_value() {
  local file="$1" key="$2"
  awk -v key="${key}" '
    $0 !~ /^[[:space:]]*#/ && index($0, key "=") == 1 {
      sub(/^[^=]*=/, ""); gsub(/\r$/, ""); print; exit
    }
  ' "${file}"
}

backup_commit="$(file_value "${backup_dir}/manifest.txt" release_commit)"
current_commit="$(git -C "${repo_root}" rev-parse HEAD)"
[[ "${backup_commit}" == "${current_commit}" ]] \
  || { echo "Backup commit ${backup_commit} does not match current release ${current_commit}." >&2; exit 1; }
[[ "$(file_value "${backup_dir}/manifest.txt" quiesced)" == true ]] \
  || { echo "M5.3 functional recovery requires a quiesced backup." >&2; exit 1; }
release_version="$(file_value "${env_file}" RABBIT_RELEASE_VERSION)"
[[ -n "${release_version}" ]] || { echo "RABBIT_RELEASE_VERSION is required in .env." >&2; exit 1; }
for image in "rabbit-aip-api:${release_version}" "rabbit-aip-web:${release_version}"; do
  docker image inspect "${image}" >/dev/null 2>&1 \
    || { echo "Required local release image is missing: ${image}" >&2; exit 1; }
done

export RABBIT_BIND_ADDRESS=127.0.0.1
export RABBIT_HTTP_PORT=0
export RABBIT_ENVIRONMENT=restore-drill
export RABBIT_RELEASE_VERSION="${release_version}"
export M5_3_CONFIG_FILE="${config_file}"
export M5_3_EVIDENCE_ROOT="${evidence_root}"
export M5_3_EVIDENCE_RUN_ID="${run_id}"
compose=(
  docker compose -p "${project}" --env-file "${env_file}"
  -f "${repo_root}/docker-compose.yml"
  -f "${repo_root}/infra/pilot/compose.local-pilot.yml"
  -f "${repo_root}/infra/backup/compose.functional-restore.yml"
)

cleanup() {
  "${compose[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT
trap 'exit 130' HUP INT TERM

wait_healthy() {
  local service="$1" attempts="${2:-90}" container_id status
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    container_id="$("${compose[@]}" ps -q "${service}" 2>/dev/null || true)"
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
      "${container_id}" 2>/dev/null || true)"
    [[ "${status}" == healthy ]] && return 0
    [[ "${status}" != exited && "${status}" != dead ]] \
      || { echo "Temporary service failed: ${service}" >&2; return 1; }
    sleep 2
  done
  echo "Temporary service did not become healthy: ${service}" >&2
  return 1
}

"${compose[@]}" up --detach --no-build postgres minio >/dev/null
wait_healthy postgres 60
wait_healthy minio 60

"${compose[@]}" exec -T postgres sh -eu -c \
  'pg_restore --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --no-owner --no-privileges --exit-on-error' \
  <"${backup_dir}/postgres.dump"

minio_container="$("${compose[@]}" ps -q minio)"
"${compose[@]}" stop minio >/dev/null
docker run --rm --network none --volumes-from "${minio_container}" \
  --volume "${backup_dir}:/backup:ro" alpine:3.22 \
  sh -euc '
    entries="$(tar -tzf /backup/minio-data.tar.gz)"
    if printf "%s\n" "$entries" | grep -Eq "(^/|(^|/)\.\.(/|$))"; then
      echo "Unsafe path found in MinIO archive." >&2
      exit 1
    fi
    find /data -mindepth 1 -maxdepth 1 -exec rm -rf {} +
    tar -xzf /backup/minio-data.tar.gz -C /
  '
"${compose[@]}" start minio >/dev/null
wait_healthy minio 60

"${compose[@]}" exec -T postgres sh -eu -c \
  'psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --tuples-only --no-align --set ON_ERROR_STOP=1 --command "
      SELECT '\''flyway_version='\'' || COALESCE(MAX(version), '\''none'\'') FROM flyway_schema_history WHERE success;
      SELECT '\''organisations='\'' || COUNT(*) FROM organisations;
      SELECT '\''users='\'' || COUNT(*) FROM user_accounts;
      SELECT '\''memberships='\'' || COUNT(*) FROM organisation_memberships;
      SELECT '\''questions='\'' || COUNT(*) FROM questions;
      SELECT '\''question_options='\'' || COUNT(*) FROM question_options;
      SELECT '\''assessments='\'' || COUNT(*) FROM assessments;
      SELECT '\''assessment_questions='\'' || COUNT(*) FROM assessment_question_ids;
      SELECT '\''attempts='\'' || COUNT(*) FROM assessment_attempts;
      SELECT '\''responses='\'' || COUNT(*) FROM attempt_responses;
      SELECT '\''selected_options='\'' || COUNT(*) FROM response_selected_options;
      SELECT '\''audit_events='\'' || COUNT(*) FROM audit_events;
    "' >"${run_dir}/restored-reconciliation.txt"
restored_minio_files="$(docker run --rm --network none --volumes-from "${minio_container}" \
  alpine:3.22 sh -euc 'find /data -type f | wc -l' | tr -d '[:space:]')"
printf 'minio_files=%s\n' "${restored_minio_files}" >>"${run_dir}/restored-reconciliation.txt"
diff -u "${backup_dir}/source-reconciliation.txt" "${run_dir}/restored-reconciliation.txt" \
  >"${run_dir}/reconciliation.diff" \
  || { cat "${run_dir}/reconciliation.diff" >&2; echo "Restored counts do not reconcile." >&2; exit 1; }

"${compose[@]}" up --detach --no-build redis rabbitmq minio backend frontend nginx >/dev/null
for service in redis rabbitmq minio backend frontend nginx; do
  wait_healthy "${service}" 120
done
"${compose[@]}" --profile m5-3-evidence run --rm --no-deps -T recovery-probe \
  >"${run_dir}/functional-recovery.log" 2>&1
[[ -s "${run_dir}/functional-recovery.json" ]] \
  || { echo "Functional recovery probe did not write evidence." >&2; exit 1; }

finished_epoch="$(date -u +%s)"
created_epoch="$(file_value "${backup_dir}/manifest.txt" created_at_epoch)"
[[ "${created_epoch}" =~ ^[0-9]+$ ]] || { echo "Backup creation epoch is unavailable." >&2; exit 1; }
backup_age_seconds=$((started_epoch - created_epoch))
restore_duration_seconds=$((finished_epoch - started_epoch))
((backup_age_seconds >= 0 && backup_age_seconds <= 86400)) \
  || { echo "Backup is outside the 24-hour RPO." >&2; exit 1; }
((restore_duration_seconds <= 14400)) \
  || { echo "Functional recovery exceeded the four-hour RTO." >&2; exit 1; }

if ! "${compose[@]}" down --volumes --remove-orphans >/dev/null; then
  echo "Temporary recovery project could not be removed cleanly." >&2
  exit 1
fi
trap - EXIT HUP INT TERM

cat >"${run_dir}/functional-restore-drill.txt" <<EOF
Rabbit M5.3 isolated functional restore drill
completed_at_utc=${stamp}
release_commit=${current_commit}
source_backup=${backup_dir}
temporary_compose_project=${project}
live_environment_modified=false
database_reconciliation=pass
minio_reconciliation=pass
functional_login_assets_assessment_result_export_audit=pass
backup_age_seconds=${backup_age_seconds}
rpo_target_seconds=86400
restore_duration_seconds=${restore_duration_seconds}
rto_target_seconds=14400
temporary_resources_removed=true
credentials_recorded=false
EOF
sha256sum "${run_dir}/functional-restore-drill.txt" \
  "${run_dir}/functional-recovery.json" \
  "${run_dir}/restored-reconciliation.txt" \
  >"${run_dir}/functional-restore-drill.sha256"
echo "M5.3 isolated functional restore drill passed."
echo "Evidence: ${run_dir}"
