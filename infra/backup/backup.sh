#!/usr/bin/env sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: ./infra/backup/backup.sh <backup-parent-directory>" >&2
  exit 2
fi

backup_parent=$1
if [ -z "$backup_parent" ] || [ "$backup_parent" = "/" ]; then
  echo "Refusing an empty or root backup destination." >&2
  exit 2
fi

mkdir -p "$backup_parent"
work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT HUP INT TERM

stamp=$(date -u +%Y%m%dT%H%M%SZ)
backup_dir="$backup_parent/rabbit-$stamp"
postgres_user=${POSTGRES_USER:-rabbit}
postgres_db=${POSTGRES_DB:-rabbit}

docker compose exec -T postgres \
  pg_dump --format=custom --no-owner --no-privileges \
  --username "$postgres_user" "$postgres_db" \
  > "$work_dir/postgres.dump"

minio_container=$(docker compose ps -q minio)
if [ -z "$minio_container" ]; then
  echo "MinIO container is not running." >&2
  exit 1
fi
docker run --rm --volumes-from "$minio_container" alpine:3.22 \
  tar -czf - /data > "$work_dir/minio-data.tar.gz"

cat > "$work_dir/manifest.txt" <<EOF
Rabbit AiP Release 1.0 backup
created_at=$stamp
database=$postgres_db
includes=postgres.dump,minio-data.tar.gz
EOF

(
  cd "$work_dir"
  sha256sum postgres.dump minio-data.tar.gz > SHA256SUMS
)

mv "$work_dir" "$backup_dir"
trap - EXIT HUP INT TERM
echo "Backup completed: $backup_dir"
