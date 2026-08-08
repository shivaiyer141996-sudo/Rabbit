#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
cd "${repo_root}"
backup_parent=''
quiesce=false

usage() {
  cat >&2 <<'EOF'
Usage: ./infra/backup/backup.sh [backup-parent-directory] [--quiesce]

--quiesce briefly stops Rabbit's API, web, and gateway while PostgreSQL and
MinIO are captured. M5.3 requires this mode so source reconciliation is stable.
Any service stopped by the script is restarted on success or failure.
EOF
}

while (($# > 0)); do
  case "$1" in
    --quiesce) quiesce=true; shift ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "Unknown option: $1" >&2; usage; exit 2 ;;
    *)
      [[ -z "${backup_parent}" ]] || { echo "Only one backup directory is allowed." >&2; exit 2; }
      backup_parent="$1"
      shift
      ;;
  esac
done

if [[ -z "${backup_parent}" && -f "${repo_root}/.env" ]]; then
  backup_parent="$(sed -n 's/^PILOT_BACKUP_DIRECTORY=//p' "${repo_root}/.env" | tail -n 1)"
fi
case "${backup_parent}" in
  \"*\") backup_parent="${backup_parent#\"}"; backup_parent="${backup_parent%\"}" ;;
  \'*\') backup_parent="${backup_parent#\'}"; backup_parent="${backup_parent%\'}" ;;
esac
if [[ -z "${backup_parent}" \
    || "${backup_parent}" == / \
    || "${backup_parent}" == REPLACE_WITH_SEPARATE_LOCAL_DEVICE_PATH ]]; then
  echo "Refusing an empty, root, or placeholder backup destination." >&2
  exit 2
fi
for command_name in docker git sha256sum; do
  command -v "${command_name}" >/dev/null 2>&1 \
    || { echo "${command_name} is required." >&2; exit 1; }
done
docker info >/dev/null 2>&1 || { echo "Docker Engine is unavailable." >&2; exit 1; }

mkdir -p "${backup_parent}"
backup_parent="$(cd "${backup_parent}" && pwd -P)"
chmod 700 "${backup_parent}" 2>/dev/null || true
work_dir="$(mktemp -d "${backup_parent}/.rabbit-backup.XXXXXX")"
stopped_services=()

compose=(docker compose)

cleanup() {
  local status=$?
  trap - EXIT
  if ((${#stopped_services[@]} > 0)); then
    "${compose[@]}" start "${stopped_services[@]}" >/dev/null 2>&1 || status=1
  fi
  case "${work_dir}" in
    "${backup_parent}"/.rabbit-backup.*) rm -rf -- "${work_dir}" ;;
  esac
  exit "${status}"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

wait_healthy() {
  local service="$1" container_id status
  for _ in $(seq 1 90); do
    container_id="$("${compose[@]}" ps -q "${service}" 2>/dev/null || true)"
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
      "${container_id}" 2>/dev/null || true)"
    [[ "${status}" == healthy || "${status}" == running ]] && return 0
    [[ "${status}" != exited && "${status}" != dead ]] || return 1
    sleep 2
  done
  return 1
}

for service in postgres minio; do
  [[ -n "$("${compose[@]}" ps -q "${service}" 2>/dev/null)" ]] \
    || { echo "Rabbit service is unavailable: ${service}" >&2; exit 1; }
done

if [[ "${quiesce}" == true ]]; then
  for service in backend frontend nginx; do
    if [[ -n "$("${compose[@]}" ps -q --status running "${service}" 2>/dev/null)" ]]; then
      stopped_services+=("${service}")
    fi
  done
  if ((${#stopped_services[@]} > 0)); then
    "${compose[@]}" stop "${stopped_services[@]}" >/dev/null
  fi
fi

started_epoch="$(date -u +%s)"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_dir="${backup_parent}/rabbit-${stamp}"
[[ ! -e "${backup_dir}" ]] || { echo "Backup destination already exists: ${backup_dir}" >&2; exit 1; }
postgres_db="$("${compose[@]}" exec -T postgres sh -eu -c 'printf %s "$POSTGRES_DB"')"

"${compose[@]}" exec -T postgres \
  sh -eu -c 'pg_dump --format=custom --no-owner --no-privileges \
    --username "$POSTGRES_USER" "$POSTGRES_DB"' \
  >"${work_dir}/postgres.dump"

minio_container="$("${compose[@]}" ps -q minio)"
[[ -n "${minio_container}" ]] || { echo "MinIO container is not running." >&2; exit 1; }
docker run --rm --volumes-from "${minio_container}" alpine:3.22 \
  tar -czf - /data >"${work_dir}/minio-data.tar.gz"

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
      SELECT '\''assessment_subjects='\'' || COUNT(*) FROM assessment_subject_ids;
      SELECT '\''academic_programmes='\'' || COUNT(*) FROM academic_programmes;
      SELECT '\''academic_batches='\'' || COUNT(*) FROM academic_batches;
      SELECT '\''academic_sections='\'' || COUNT(*) FROM sections;
      SELECT '\''attempts='\'' || COUNT(*) FROM assessment_attempts;
      SELECT '\''responses='\'' || COUNT(*) FROM attempt_responses;
      SELECT '\''selected_options='\'' || COUNT(*) FROM response_selected_options;
      SELECT '\''audit_events='\'' || COUNT(*) FROM audit_events;
      SELECT '\''commercial_subscriptions='\'' || COUNT(*) FROM organisation_subscriptions;
      SELECT '\''commercial_subscription_events='\'' || COUNT(*) FROM commercial_subscription_events;
      SELECT '\''commercial_invoices='\'' || COUNT(*) FROM commercial_invoices;
      SELECT '\''commercial_payments='\'' || COUNT(*) FROM commercial_payments;
      SELECT '\''commercial_receipts='\'' || COUNT(*) FROM commercial_receipts;
      SELECT '\''commercial_support_cases='\'' || COUNT(*) FROM commercial_support_cases;
    "' >"${work_dir}/source-reconciliation.txt"
minio_files="$(docker run --rm --network none --volumes-from "${minio_container}" \
  alpine:3.22 sh -euc 'find /data -type f | wc -l' | tr -d '[:space:]')"
printf 'minio_files=%s\n' "${minio_files}" >>"${work_dir}/source-reconciliation.txt"

release_commit="$(git rev-parse HEAD 2>/dev/null || printf unversioned)"
if [[ -z "$(git status --porcelain 2>/dev/null || true)" ]]; then
  worktree_state=clean
else
  worktree_state=dirty
fi
compose_version="$(docker compose version --short 2>/dev/null || printf unknown)"
postgres_version="$(
  "${compose[@]}" exec -T postgres sh -eu -c \
    'psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
      --tuples-only --no-align --command "SHOW server_version"' \
    2>/dev/null || printf unknown
)"
minio_image="$(docker inspect --format '{{.Config.Image}}' "${minio_container}" 2>/dev/null || printf unknown)"
finished_epoch="$(date -u +%s)"

cat >"${work_dir}/manifest.txt" <<EOF
Rabbit AiP Release 1.0 backup
backup_format_version=2
created_at=${stamp}
created_at_epoch=${finished_epoch}
capture_duration_seconds=$((finished_epoch - started_epoch))
release_commit=${release_commit}
worktree_state=${worktree_state}
compose_version=${compose_version}
database=${postgres_db}
postgres_version=${postgres_version}
minio_image=${minio_image}
quiesced=${quiesce}
storage_scope=local-postgresql-and-minio
includes=postgres.dump,minio-data.tar.gz,source-reconciliation.txt
EOF

(
  cd "${work_dir}"
  sha256sum manifest.txt postgres.dump minio-data.tar.gz source-reconciliation.txt >SHA256SUMS
)

mv "${work_dir}" "${backup_dir}"
work_dir="${backup_parent}/.rabbit-backup.completed-${stamp}"
if ((${#stopped_services[@]} > 0)); then
  "${compose[@]}" start "${stopped_services[@]}" >/dev/null
  for service in "${stopped_services[@]}"; do
    wait_healthy "${service}" \
      || { echo "Rabbit service did not recover after backup: ${service}" >&2; exit 1; }
  done
  stopped_services=()
fi
trap - EXIT HUP INT TERM
echo "Backup completed: ${backup_dir}"
