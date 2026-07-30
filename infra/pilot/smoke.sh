#!/usr/bin/env bash
set -euo pipefail

rabbit_base_url="${1:-http://localhost}"
rabbit_smoke_tmp="$(mktemp -d)"
trap 'rm -rf "${rabbit_smoke_tmp}"' EXIT

for attempt in $(seq 1 90); do
    if curl --fail --silent --show-error \
        "${rabbit_base_url}/api/actuator/health/readiness" \
        >"${rabbit_smoke_tmp}/health.json"; then
        break
    fi
    if [[ "${attempt}" == "90" ]]; then
        echo "Rabbit did not become ready within the smoke-test window." >&2
        exit 1
    fi
    sleep 2
done

grep --quiet '"status":"UP"' "${rabbit_smoke_tmp}/health.json"

admin_cookies="${rabbit_smoke_tmp}/admin.cookies"
student_cookies="${rabbit_smoke_tmp}/student.cookies"

curl --fail --silent --show-error \
    --cookie-jar "${admin_cookies}" \
    --header "Content-Type: application/json" \
    --data '{"email":"admin@demo.rabbit.local","password":"Rabbit@123"}' \
    "${rabbit_base_url}/gateway/auth/login" \
    >"${rabbit_smoke_tmp}/admin-login.json"

grep --quiet '"requiresOrganisationSelection":false' \
    "${rabbit_smoke_tmp}/admin-login.json"

for page in dashboard question-bank assessments users pilot-readiness; do
    curl --fail --silent --show-error \
        --cookie "${admin_cookies}" \
        "${rabbit_base_url}/${page}" \
        >/dev/null
done

for endpoint in auth/me academic-catalog questions assessments pilot-readiness; do
    curl --fail --silent --show-error \
        --cookie "${admin_cookies}" \
        "${rabbit_base_url}/gateway/backend/${endpoint}" \
        >"${rabbit_smoke_tmp}/admin-${endpoint//\//-}.json"
done

grep --quiet '"email":"admin@demo.rabbit.local"' \
    "${rabbit_smoke_tmp}/admin-auth-me.json"
grep --quiet '"organisationName":"Rabbit Demo Academy"' \
    "${rabbit_smoke_tmp}/admin-auth-me.json"
grep --quiet '"totalChecks":15' \
    "${rabbit_smoke_tmp}/admin-pilot-readiness.json"

invited_email="milestone41.user@demo.rabbit.local"
invited_password="Activated@12345"
invitation_json="$(
    curl --fail --silent --show-error \
        --cookie "${admin_cookies}" \
        --header "Content-Type: application/json" \
        --data "{
            \"email\":\"${invited_email}\",
            \"firstName\":\"Milestone\",
            \"lastName\":\"User\",
            \"role\":\"FACULTY\",
            \"sectionId\":null
        }" \
        "${rabbit_base_url}/gateway/backend/users"
)"
activation_url="$(
    python3 -c 'import json,sys; print(json.load(sys.stdin)["activationUrl"])' \
        <<<"${invitation_json}"
)"
activation_token="$(
    python3 -c \
        'import sys,urllib.parse; print(urllib.parse.parse_qs(urllib.parse.urlsplit(sys.argv[1]).fragment)["token"][0])' \
        "${activation_url}"
)"

curl --fail --silent --show-error \
    --header "Content-Type: application/json" \
    --data "{\"token\":\"${activation_token}\"}" \
    "${rabbit_base_url}/gateway/backend/auth/invitations/validate" \
    >"${rabbit_smoke_tmp}/invitation-details.json"
grep --quiet "\"email\":\"${invited_email}\"" \
    "${rabbit_smoke_tmp}/invitation-details.json"

curl --fail --silent --show-error \
    --header "Content-Type: application/json" \
    --data "{\"token\":\"${activation_token}\",\"password\":\"${invited_password}\"}" \
    "${rabbit_base_url}/gateway/backend/auth/invitations/activate" \
    >"${rabbit_smoke_tmp}/activation.json"
grep --quiet '"activated":true' "${rabbit_smoke_tmp}/activation.json"

consumed_status="$(
    curl --silent --show-error \
        --output "${rabbit_smoke_tmp}/consumed-invitation.json" \
        --write-out "%{http_code}" \
        --header "Content-Type: application/json" \
        --data "{\"token\":\"${activation_token}\"}" \
        "${rabbit_base_url}/gateway/backend/auth/invitations/validate"
)"
if [[ "${consumed_status}" != "410" ]]; then
    echo "Consumed invitation remained usable (HTTP ${consumed_status})." >&2
    exit 1
fi

curl --fail --silent --show-error \
    --header "Content-Type: application/json" \
    --data "{\"email\":\"${invited_email}\",\"password\":\"${invited_password}\"}" \
    "${rabbit_base_url}/gateway/auth/login" \
    >"${rabbit_smoke_tmp}/invited-first-login.json"
grep --quiet '"firstLogin":true' \
    "${rabbit_smoke_tmp}/invited-first-login.json"

curl --fail --silent --show-error \
    --header "Content-Type: application/json" \
    --data "{\"email\":\"${invited_email}\",\"password\":\"${invited_password}\"}" \
    "${rabbit_base_url}/gateway/auth/login" \
    >"${rabbit_smoke_tmp}/invited-second-login.json"
grep --quiet '"firstLogin":false' \
    "${rabbit_smoke_tmp}/invited-second-login.json"

curl --fail --silent --show-error \
    --cookie "${admin_cookies}" \
    "${rabbit_base_url}/gateway/backend/users" \
    >"${rabbit_smoke_tmp}/users-after-activation.json"
python3 -c \
    'import json,sys; rows=json.load(sys.stdin); assert any(row["email"] == sys.argv[1] and row["status"] == "ACTIVE" for row in rows)' \
    "${invited_email}" <"${rabbit_smoke_tmp}/users-after-activation.json"

for failed_attempt in $(seq 1 5); do
    login_status="$(
        curl --silent --show-error \
            --output "${rabbit_smoke_tmp}/failed-login-${failed_attempt}.json" \
            --write-out "%{http_code}" \
            --header "Content-Type: application/json" \
            --data "{\"email\":\"${invited_email}\",\"password\":\"Wrong@123456\"}" \
            "${rabbit_base_url}/gateway/auth/login"
    )"
    expected_status="401"
    if [[ "${failed_attempt}" == "5" ]]; then
        expected_status="423"
    fi
    if [[ "${login_status}" != "${expected_status}" ]]; then
        echo "Failed login ${failed_attempt} returned HTTP ${login_status}; expected ${expected_status}." >&2
        exit 1
    fi
done
grep --quiet '"code":"ACCOUNT_LOCKED"' \
    "${rabbit_smoke_tmp}/failed-login-5.json"

locked_status="$(
    curl --silent --show-error \
        --output "${rabbit_smoke_tmp}/locked-correct-login.json" \
        --write-out "%{http_code}" \
        --header "Content-Type: application/json" \
        --data "{\"email\":\"${invited_email}\",\"password\":\"${invited_password}\"}" \
        "${rabbit_base_url}/gateway/auth/login"
)"
if [[ "${locked_status}" != "423" ]]; then
    echo "Locked account accepted a correct password (HTTP ${locked_status})." >&2
    exit 1
fi

if [[ -n "${RABBIT_SMOKE_LOCK_WAIT_SECONDS:-}" ]]; then
    sleep "${RABBIT_SMOKE_LOCK_WAIT_SECONDS}"
    curl --fail --silent --show-error \
        --header "Content-Type: application/json" \
        --data "{\"email\":\"${invited_email}\",\"password\":\"${invited_password}\"}" \
        "${rabbit_base_url}/gateway/auth/login" \
        >"${rabbit_smoke_tmp}/unlocked-login.json"
    grep --quiet '"requiresOrganisationSelection":false' \
        "${rabbit_smoke_tmp}/unlocked-login.json"
fi

curl --fail --silent --show-error \
    --cookie-jar "${student_cookies}" \
    --header "Content-Type: application/json" \
    --data '{"email":"student@demo.rabbit.local","password":"Rabbit@123"}' \
    "${rabbit_base_url}/gateway/auth/login" \
    >"${rabbit_smoke_tmp}/student-login.json"

available_json="$(
    curl --fail --silent --show-error \
        --cookie "${student_cookies}" \
        "${rabbit_base_url}/gateway/backend/student/assessments"
)"
assessment_id="$(
    python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])' \
        <<<"${available_json}"
)"

attempt_json="$(
    curl --fail --silent --show-error \
        --cookie "${student_cookies}" \
        --request POST \
        "${rabbit_base_url}/gateway/backend/student/assessments/${assessment_id}/attempts"
)"
attempt_id="$(
    python3 -c 'import json,sys; print(json.load(sys.stdin)["attemptId"])' \
        <<<"${attempt_json}"
)"
question_id="$(
    python3 -c 'import json,sys; print(json.load(sys.stdin)["questions"][0]["id"])' \
        <<<"${attempt_json}"
)"
option_id="$(
    python3 -c 'import json,sys; print(json.load(sys.stdin)["questions"][0]["options"][0]["id"])' \
        <<<"${attempt_json}"
)"

response_payload="$(
    python3 -c \
        'import json,sys; print(json.dumps({"questionId":sys.argv[1],"selectedOptionIds":[sys.argv[2]],"flagged":False,"timeSpentSeconds":1}))' \
        "${question_id}" "${option_id}"
)"

curl --fail --silent --show-error \
    --cookie "${student_cookies}" \
    --request PUT \
    --header "Content-Type: application/json" \
    --data "${response_payload}" \
    "${rabbit_base_url}/gateway/backend/student/attempts/${attempt_id}/responses" \
    >/dev/null

curl --fail --silent --show-error \
    --cookie "${student_cookies}" \
    --request POST \
    "${rabbit_base_url}/gateway/backend/student/attempts/${attempt_id}/submit" \
    >"${rabbit_smoke_tmp}/submitted-result.json"
grep --quiet '"publicationStatus":"PENDING_PUBLICATION"' \
    "${rabbit_smoke_tmp}/submitted-result.json"

curl --fail --silent --show-error \
    --cookie "${admin_cookies}" \
    --request POST \
    "${rabbit_base_url}/gateway/backend/evaluation/assessments/${assessment_id}/publish" \
    >"${rabbit_smoke_tmp}/publication.json"

curl --fail --silent --show-error \
    --cookie "${student_cookies}" \
    "${rabbit_base_url}/gateway/backend/student/results/${attempt_id}" \
    >"${rabbit_smoke_tmp}/published-result.json"
grep --quiet '"publicationStatus":"PUBLISHED"' \
    "${rabbit_smoke_tmp}/published-result.json"

echo "Rabbit controlled-pilot smoke test passed."
