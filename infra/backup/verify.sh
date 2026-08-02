#!/usr/bin/env sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: ./infra/backup/verify.sh <backup-directory>" >&2
  exit 2
fi

backup_dir=$1
for required_file in manifest.txt postgres.dump minio-data.tar.gz SHA256SUMS; do
  if [ ! -f "$backup_dir/$required_file" ]; then
    echo "Backup is missing $required_file." >&2
    exit 1
  fi
done

(
  cd "$backup_dir"
  sha256sum -c SHA256SUMS
)

echo "Backup checksum verification passed: $backup_dir"
