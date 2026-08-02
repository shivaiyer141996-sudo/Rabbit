#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
env_file="${repo_root}/.env"
config_file="${repo_root}/.env.pilot-m5-3"
evidence_root="${repo_root}/artifacts/pilot-m5-3"
run_id="rabbit-m5.3-$(date -u +%Y%m%dT%H%M%SZ)"

usage() {
  cat <<'EOF'
Usage: ./infra/pilot/rollback-rehearsal.sh [options]

Options:
  --env-file PATH   Local pilot environment (default: .env)
  --config PATH     Protected M5.3 inputs (default: .env.pilot-m5-3)
  --output PATH     Evidence root (default: artifacts/pilot-m5-3)
  --run-id ID       Existing/new evidence run directory name
  -h, --help        Show help

This is a non-mutating tabletop rehearsal. It verifies the previous local image
pair and records the exact owner, communication path, and rollback command. It
does not switch the running application or restore data.
EOF
}

while (($# > 0)); do
  case "$1" in
    --env-file) (($# >= 2)) || { usage >&2; exit 2; }; env_file="$2"; shift 2 ;;
    --config) (($# >= 2)) || { usage >&2; exit 2; }; config_file="$2"; shift 2 ;;
    --output) (($# >= 2)) || { usage >&2; exit 2; }; evidence_root="$2"; shift 2 ;;
    --run-id) (($# >= 2)) || { usage >&2; exit 2; }; run_id="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ "${run_id}" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Unsafe run ID." >&2; exit 2; }
for required_file in "${env_file}" "${config_file}"; do
  [[ -f "${required_file}" ]] || { echo "Missing required file: ${required_file}" >&2; exit 1; }
done
for command_name in docker git sha256sum; do
  command -v "${command_name}" >/dev/null 2>&1 \
    || { echo "${command_name} is required." >&2; exit 1; }
done
docker info >/dev/null 2>&1 || { echo "Docker Engine is unavailable." >&2; exit 1; }

file_value() {
  local file="$1" key="$2" value
  value="$(awk -v key="${key}" '
    $0 !~ /^[[:space:]]*#/ && index($0, key "=") == 1 {
      sub(/^[^=]*=/, ""); gsub(/\r$/, ""); print; exit
    }
  ' "${file}")"
  if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "${value}"
}

required_value() {
  local file="$1" key="$2" value
  value="$(file_value "${file}" "${key}")"
  [[ -n "${value}" && "${value}" != *REPLACE* ]] \
    || { echo "${key} must be populated." >&2; exit 1; }
  printf '%s' "${value}"
}

current_version="$(required_value "${env_file}" RABBIT_RELEASE_VERSION)"
rollback_version="$(required_value "${config_file}" PILOT_ROLLBACK_RELEASE_VERSION)"
rollback_owner="$(required_value "${env_file}" PILOT_ROLLBACK_OWNER)"
support_contact="$(required_value "${env_file}" PILOT_SUPPORT_CONTACT)"
tester="$(required_value "${config_file}" PILOT_ROLLBACK_TESTER)"
incident_channel="$(required_value "${config_file}" PILOT_INCIDENT_CHANNEL)"
confirmation="$(file_value "${config_file}" PILOT_ROLLBACK_REHEARSAL_CONFIRMED)"

[[ "${rollback_version}" =~ ^[A-Za-z0-9._-]+$ ]] \
  || { echo "PILOT_ROLLBACK_RELEASE_VERSION contains unsafe characters." >&2; exit 1; }
[[ "${rollback_version}" != "${current_version}" ]] \
  || { echo "Rollback version must differ from the active release version." >&2; exit 1; }
[[ "${confirmation}" == yes ]] \
  || { echo "Set PILOT_ROLLBACK_REHEARSAL_CONFIRMED=yes after the named tester reviews the tabletop." >&2; exit 1; }

for image in "rabbit-aip-api:${rollback_version}" "rabbit-aip-web:${rollback_version}"; do
  docker image inspect "${image}" >/dev/null 2>&1 \
    || { echo "Verified rollback image is missing locally: ${image}" >&2; exit 1; }
done

compose=(
  docker compose --env-file "${env_file}"
  -f "${repo_root}/docker-compose.yml"
  -f "${repo_root}/infra/pilot/compose.local-pilot.yml"
)
for service in backend frontend; do
  container_id="$("${compose[@]}" ps -q "${service}" 2>/dev/null || true)"
  active_image="$(docker inspect --format '{{.Config.Image}}' "${container_id}" 2>/dev/null || true)"
  expected="rabbit-aip-web:${current_version}"
  [[ "${service}" == backend ]] && expected="rabbit-aip-api:${current_version}"
  [[ "${active_image}" == "${expected}" ]] \
    || { echo "Active ${service} image does not match ${expected}." >&2; exit 1; }
done

candidate_images="$(RABBIT_RELEASE_VERSION="${rollback_version}" "${compose[@]}" config --images)"
grep -Fxq "rabbit-aip-api:${rollback_version}" <<<"${candidate_images}" \
  || { echo "Rollback API image is absent from the rendered Compose plan." >&2; exit 1; }
grep -Fxq "rabbit-aip-web:${rollback_version}" <<<"${candidate_images}" \
  || { echo "Rollback web image is absent from the rendered Compose plan." >&2; exit 1; }

evidence_root="$(mkdir -p "${evidence_root}" && cd "${evidence_root}" && pwd -P)"
run_dir="${evidence_root}/${run_id}"
mkdir -p "${run_dir}"
command_text="RABBIT_RELEASE_VERSION=${rollback_version} docker compose --env-file .env -f docker-compose.yml -f infra/pilot/compose.local-pilot.yml up --detach --no-build backend frontend nginx"

cat >"${run_dir}/rollback-rehearsal.md" <<EOF
# Rabbit M5.3 rollback tabletop

- Generated (UTC): $(date -u +%Y%m%dT%H%M%SZ)
- Release commit: $(git -C "${repo_root}" rev-parse HEAD)
- Active local image tag: \`${current_version}\`
- Verified rollback image tag: \`${rollback_version}\`
- Rollback owner: ${rollback_owner}
- Tabletop tester: ${tester}
- Support contact: ${support_contact}
- Incident/maintenance communication path: ${incident_channel}
- Live environment changed by this rehearsal: **No**

## Exact application rollback command

\`\`\`bash
${command_text}
\`\`\`

Before running it, stop new assessment starts, notify the recorded channel, and
create a forensic backup. The command changes only the API/web image pair. If a
database restore is required, stop and use the separately tested destructive
restore procedure with its explicit confirmation flag; never improvise a schema
rollback during a live assessment.

After rollback, verify readiness, Admin/Student login, assessment discovery,
published result privacy, PDF/Excel export, audit search, and MinIO assets before
reopening Rabbit on the trusted LAN.
EOF

cat >"${run_dir}/rollback-rehearsal.txt" <<EOF
Rabbit M5.3 rollback rehearsal
status=pass
rehearsal_type=non-mutating-tabletop
active_release=${current_version}
rollback_release=${rollback_version}
rollback_images_present=true
rendered_compose_plan_verified=true
named_owner=true
named_tester=true
support_contact=true
communication_path=true
human_confirmation=yes
live_environment_modified=false
EOF
sha256sum "${run_dir}/rollback-rehearsal.md" "${run_dir}/rollback-rehearsal.txt" \
  >"${run_dir}/rollback-rehearsal.sha256"
echo "M5.3 rollback tabletop passed without changing the live stack."
echo "Evidence: ${run_dir}"
