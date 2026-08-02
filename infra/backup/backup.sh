#!/usr/bin/env sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd -P)
cd "$repo_root"

if [ "$#" -gt 1 ]; then
  echo "Usage: ./infra/backup/backup.sh [backup-parent-directory]" >&2
  exit 2
fi

if [ "$#" -eq 1 ]; then
  backup_parent=$1
elif [ -f "$repo_root/.env" ]; then
  backup_parent=$(sed -n 's/^PILOT_BACKUP_DIRECTORY=//p' "$repo_root/.env" | tail -n 1)
else
  backup_parent=
fi
case "$backup_parent" in
  \"*\") backup_parent=${backup_parent#\"}; backup_parent=${backup_parent%\"} ;;
  \'*\') backup_parent=${backup_parent#\'}; backup_parent=${backup_parent%\'} ;;
esac
if [ -z "$backup_parent" ] \
  || [ "$backup_parent" = "/" ] \
  || [ "$backup_parent" = "REPLACE_WITH_SEPARATE_LOCAL_DEVICE_PATH" ]; then
  echo "Refusing an empty or root backup destination." >&2
  exit 2
fi

mkdir -p "$backup_parent"
backup_parent=$(CDPATH= cd -- "$backup_parent" && pwd -P)
chmod 700 "$backup_parent" 2>/dev/null || true
work_dir=$(mktemp -d "$backup_parent/.rabbit-backup.XXXXXX")
trap 'rm -rf "$work_dir"' EXIT HUP INT TERM

stamp=$(date -u +%Y%m%dT%H%M%SZ)
backup_dir="$backup_parent/rabbit-$stamp"
if [ -e "$backup_dir" ]; then
  echo "Backup destination already exists: $backup_dir" >&2
  exit 1
fi
postgres_db=$(docker compose exec -T postgres sh -eu -c 'printf %s "$POSTGRES_DB"')

docker compose exec -T postgres \
  sh -eu -c 'pg_dump --format=custom --no-owner --no-privileges \
    --username "$POSTGRES_USER" "$POSTGRES_DB"' \
  > "$work_dir/postgres.dump"

minio_container=$(docker compose ps -q minio)
if [ -z "$minio_container" ]; then
  echo "MinIO container is not running." >&2
  exit 1
fi
docker run --rm --volumes-from "$minio_container" alpine:3.22 \
  tar -czf - /data > "$work_dir/minio-data.tar.gz"

release_commit=$(git rev-parse HEAD 2>/dev/null || echo unversioned)
if [ -z "$(git status --porcelain 2>/dev/null || true)" ]; then
  worktree_state=clean
else
  worktree_state=dirty
fi
compose_version=$(docker compose version --short 2>/dev/null || echo unknown)
postgres_version=$(
  docker compose exec -T postgres sh -eu -c \
    'psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
      --tuples-only --no-align --command "SHOW server_version"' \
    2>/dev/null || echo unknown
)
minio_image=$(docker inspect --format '{{.Config.Image}}' "$minio_container" 2>/dev/null || echo unknown)

cat > "$work_dir/manifest.txt" <<EOF
Rabbit AiP Release 1.0 backup
created_at=$stamp
release_commit=$release_commit
worktree_state=$worktree_state
compose_version=$compose_version
database=$postgres_db
postgres_version=$postgres_version
minio_image=$minio_image
storage_scope=local-postgresql-and-minio
includes=postgres.dump,minio-data.tar.gz
EOF

(
  cd "$work_dir"
  sha256sum manifest.txt postgres.dump minio-data.tar.gz > SHA256SUMS
)

mv "$work_dir" "$backup_dir"
trap - EXIT HUP INT TERM
echo "Backup completed: $backup_dir"
