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

format_version=$(sed -n 's/^backup_format_version=//p' "$backup_dir/manifest.txt" | tail -n 1)
if [ "$format_version" = "2" ] && [ ! -f "$backup_dir/source-reconciliation.txt" ]; then
  echo "Version 2 backup is missing source-reconciliation.txt." >&2
  exit 1
fi

checksummed_files="manifest.txt postgres.dump minio-data.tar.gz"
if [ "$format_version" = "2" ]; then
  checksummed_files="$checksummed_files source-reconciliation.txt"
fi
for checksummed_file in $checksummed_files; do
  if ! awk -v expected="$checksummed_file" '$2 == expected { found = 1 } END { exit !found }' \
      "$backup_dir/SHA256SUMS"; then
    echo "Backup checksum manifest does not cover $checksummed_file." >&2
    exit 1
  fi
done

(
  cd "$backup_dir"
  sha256sum -c SHA256SUMS
)

echo "Backup checksum verification passed: $backup_dir"
