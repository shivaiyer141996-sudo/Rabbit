#!/usr/bin/env bash
set -euo pipefail

if (($# != 1)); then
  echo "Usage: ./infra/backup/restore-drill.sh <backup-directory>" >&2
  exit 2
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
backup_dir="$1"
if [[ ! -d "${backup_dir}" ]]; then
  echo "Backup directory does not exist: ${backup_dir}" >&2
  exit 1
fi
backup_dir="$(cd "${backup_dir}" && pwd -P)"
"${repo_root}/infra/backup/verify.sh" "${backup_dir}"

for command_name in docker openssl; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "${command_name} is required for the isolated restore drill." >&2
    exit 1
  fi
done
if ! docker info >/dev/null 2>&1; then
  echo "Docker Engine is unavailable." >&2
  exit 1
fi

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
started_epoch="$(date -u +%s)"
suffix="${stamp//[^0-9A-Za-z]/}-$$"
postgres_container="rabbit-restore-postgres-${suffix}"
postgres_volume="rabbit-restore-postgres-data-${suffix}"
minio_volume="rabbit-restore-minio-data-${suffix}"
postgres_password="$(openssl rand -hex 24)"
drill_tmp="$(mktemp -d)"

cleanup() {
  docker rm --force "${postgres_container}" >/dev/null 2>&1 || true
  docker volume rm "${postgres_volume}" >/dev/null 2>&1 || true
  docker volume rm "${minio_volume}" >/dev/null 2>&1 || true
  rm -rf "${drill_tmp}"
}
trap cleanup EXIT HUP INT TERM

docker volume create "${postgres_volume}" >/dev/null
docker volume create "${minio_volume}" >/dev/null
docker run --detach \
  --name "${postgres_container}" \
  --network none \
  --env POSTGRES_DB=rabbit_restore_drill \
  --env POSTGRES_USER=rabbit_restore \
  --env "POSTGRES_PASSWORD=${postgres_password}" \
  --volume "${postgres_volume}:/var/lib/postgresql/data" \
  postgres:16-alpine >/dev/null

for attempt in $(seq 1 60); do
  if docker exec "${postgres_container}" \
    pg_isready --username rabbit_restore --dbname rabbit_restore_drill \
    >/dev/null 2>&1; then
    break
  fi
  if ((attempt == 60)); then
    docker logs "${postgres_container}" >&2
    echo "Temporary PostgreSQL did not become ready." >&2
    exit 1
  fi
  sleep 1
done

docker exec --interactive "${postgres_container}" pg_restore \
  --username rabbit_restore \
  --dbname rabbit_restore_drill \
  --no-owner \
  --no-privileges \
  --exit-on-error \
  <"${backup_dir}/postgres.dump"

docker exec "${postgres_container}" psql \
  --username rabbit_restore \
  --dbname rabbit_restore_drill \
  --tuples-only \
  --no-align \
  --set ON_ERROR_STOP=1 \
  --command "
    SELECT 'flyway_version=' || COALESCE(MAX(version), 'none') FROM flyway_schema_history WHERE success;
    SELECT 'organisations=' || COUNT(*) FROM organisations;
    SELECT 'users=' || COUNT(*) FROM user_accounts;
    SELECT 'memberships=' || COUNT(*) FROM organisation_memberships;
    SELECT 'questions=' || COUNT(*) FROM questions;
    SELECT 'question_options=' || COUNT(*) FROM question_options;
    SELECT 'assessments=' || COUNT(*) FROM assessments;
    SELECT 'assessment_questions=' || COUNT(*) FROM assessment_question_ids;
    SELECT 'attempts=' || COUNT(*) FROM assessment_attempts;
    SELECT 'responses=' || COUNT(*) FROM attempt_responses;
    SELECT 'selected_options=' || COUNT(*) FROM response_selected_options;
    SELECT 'audit_events=' || COUNT(*) FROM audit_events;
  " >"${drill_tmp}/database-reconciliation.txt"

docker run --rm \
  --network none \
  --volume "${minio_volume}:/restore" \
  --volume "${backup_dir}:/backup:ro" \
  alpine:3.22 \
  sh -euc '
    entries="$(tar -tzf /backup/minio-data.tar.gz)"
    if printf "%s\n" "$entries" | grep -Eq "(^/|(^|/)\.\.(/|$))"; then
      echo "Unsafe path found in MinIO archive." >&2
      exit 1
    fi
    tar -xzf /backup/minio-data.tar.gz -C /restore
    find /restore -type f | wc -l
  ' >"${drill_tmp}/minio-file-count.txt"

printf 'minio_files=%s\n' \
  "$(tr -d '[:space:]' <"${drill_tmp}/minio-file-count.txt")" \
  >>"${drill_tmp}/database-reconciliation.txt"
reconciliation_status=not_available
if [[ -f "${backup_dir}/source-reconciliation.txt" ]]; then
  if diff -u "${backup_dir}/source-reconciliation.txt" \
      "${drill_tmp}/database-reconciliation.txt" \
      >"${drill_tmp}/reconciliation-diff.txt"; then
    reconciliation_status=pass
  else
    cat "${drill_tmp}/reconciliation-diff.txt" >&2
    echo "Restored data does not reconcile to the backup source counts." >&2
    exit 1
  fi
fi

finished_epoch="$(date -u +%s)"

cat >"${drill_tmp}/restore-drill.txt" <<EOF
Rabbit M5.3-compatible isolated restore drill
completed_at_utc=${stamp}
source_backup=${backup_dir}
live_environment_modified=false
temporary_postgresql_volume=${postgres_volume}
temporary_minio_volume=${minio_volume}
database_restore=pass
minio_archive_restore=pass
minio_files=$(tr -d '[:space:]' <"${drill_tmp}/minio-file-count.txt")
source_reconciliation=${reconciliation_status}
restore_duration_seconds=$((finished_epoch - started_epoch))
rto_target_seconds=14400
EOF
cat "${drill_tmp}/database-reconciliation.txt" >>"${drill_tmp}/restore-drill.txt"
cp "${drill_tmp}/restore-drill.txt" "${backup_dir}/restore-drill-${stamp}.txt"
sha256sum "${backup_dir}/restore-drill-${stamp}.txt" \
  >"${backup_dir}/restore-drill-${stamp}.sha256"

echo "Isolated restore drill passed."
echo "Evidence: ${backup_dir}/restore-drill-${stamp}.txt"
echo "The live Rabbit database and MinIO volumes were not modified."
