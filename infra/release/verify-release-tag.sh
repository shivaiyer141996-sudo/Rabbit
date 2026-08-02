#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
tag_name="${1:-}"

if [[ ! "${tag_name}" =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "Release tag must use the exact vX.Y.Z format." >&2
  exit 2
fi
version="${tag_name#v}"

for command_name in git python3; do
  command -v "${command_name}" >/dev/null 2>&1 \
    || { echo "${command_name} is required." >&2; exit 1; }
done

git -C "${repo_root}" rev-parse --verify --quiet "refs/tags/${tag_name}" >/dev/null \
  || { echo "Release tag does not exist locally: ${tag_name}" >&2; exit 1; }
[[ "$(git -C "${repo_root}" cat-file -t "refs/tags/${tag_name}")" == tag ]] \
  || { echo "Release tag must be annotated, not lightweight." >&2; exit 1; }

head_commit="$(git -C "${repo_root}" rev-parse HEAD)"
tag_commit="$(git -C "${repo_root}" rev-parse "refs/tags/${tag_name}^{}")"
[[ "${head_commit}" == "${tag_commit}" ]] \
  || { echo "Release tag does not point to the checked-out commit." >&2; exit 1; }

git -C "${repo_root}" rev-parse --verify --quiet refs/remotes/origin/main >/dev/null \
  || { echo "origin/main is unavailable; fetch it before verifying the release tag." >&2; exit 1; }
git -C "${repo_root}" merge-base --is-ancestor "${tag_commit}" refs/remotes/origin/main \
  || { echo "The tagged commit is not present on origin/main." >&2; exit 1; }

readarray -t project_versions < <(python3 - "${repo_root}" <<'PY'
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(sys.argv[1])
document = ET.parse(root / "backend" / "pom.xml").getroot()
namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
backend = document.findtext("m:version", namespaces=namespace)
frontend = json.loads((root / "frontend" / "package.json").read_text())["version"]
print(backend or "")
print(frontend or "")
PY
)
[[ "${project_versions[0]:-}" == "${version}" ]] \
  || { echo "Backend version does not match ${tag_name}." >&2; exit 1; }
[[ "${project_versions[1]:-}" == "${version}" ]] \
  || { echo "Frontend version does not match ${tag_name}." >&2; exit 1; }

"${repo_root}/infra/architecture/verify-local-only.sh" >/dev/null

release_workflow="${repo_root}/.github/workflows/release.yml"
if grep -Eqi 'ghcr\.io|docker/login-action|push:[[:space:]]*true|packages:[[:space:]]*write' \
    "${release_workflow}"; then
  echo "Release workflow contains prohibited registry publishing." >&2
  exit 1
fi
grep -Fq 'RABBIT_RELEASE_COMMIT' "${repo_root}/docker-compose.yml" \
  || { echo "Compose does not bind images to the release commit." >&2; exit 1; }
grep -Fq 'org.opencontainers.image.revision' "${repo_root}/backend/Dockerfile" \
  || { echo "Backend image has no revision label." >&2; exit 1; }
grep -Fq 'org.opencontainers.image.revision' "${repo_root}/frontend/Dockerfile" \
  || { echo "Frontend image has no revision label." >&2; exit 1; }

echo "Release tag verification passed: ${tag_name} -> ${tag_commit}"
echo "No image or artifact was published to a cloud registry."
