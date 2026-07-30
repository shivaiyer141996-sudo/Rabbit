#!/usr/bin/env sh
set -eu

if [ "$#" -ne 2 ] || [ "$2" != "--confirm-destructive-restore" ]; then
  echo "Usage: ./infra/backup/restore.sh <backup-directory> --confirm-destructive-restore" >&2
  exit 2
fi

backup_dir=$1
if [ ! -f "$backup_dir/postgres.dump" ] \
  || [ ! -f "$backup_dir/minio-data.tar.gz" ] \
  || [ ! -f "$backup_dir/SHA256SUMS" ]; then
  echo "Backup directory is incomplete." >&2
  exit 1
fi

(
  cd "$backup_dir"
  sha256sum -c SHA256SUMS
)

postgres_user=${POSTGRES_USER:-rabbit}
postgres_db=${POSTGRES_DB:-rabbit}

docker compose stop backend frontend nginx
docker compose exec -T postgres psql \
  --username "$postgres_user" --dbname "$postgres_db" \
  --set ON_ERROR_STOP=1 \
  --command "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
docker compose exec -T postgres pg_restore \
  --username "$postgres_user" --dbname "$postgres_db" \
  --no-owner --no-privileges --exit-on-error \
  < "$backup_dir/postgres.dump"

minio_container=$(docker compose ps -q minio)
if [ -z "$minio_container" ]; then
  echo "MinIO container is not running." >&2
  exit 1
fi
docker run --rm --volumes-from "$minio_container" \
  -v "$backup_dir:/backup:ro" alpine:3.22 \
  sh -c "find /data -mindepth 1 -maxdepth 1 -exec rm -rf {} + && tar -xzf /backup/minio-data.tar.gz -C /"

docker compose start backend frontend nginx
echo "Restore completed. Run the smoke-test checklist before reopening access."
