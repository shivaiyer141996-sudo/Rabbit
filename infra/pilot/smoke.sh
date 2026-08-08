#!/usr/bin/env bash
set -euo pipefail

rabbit_base_url="${1:-http://localhost}"
rabbit_smoke_tmp="$(mktemp -d)"
trap 'rm -rf "${rabbit_smoke_tmp}"' EXIT

rabbit_force_expiry="${RABBIT_SMOKE_FORCE_EXPIRY:-auto}"
if [[ "${rabbit_force_expiry}" == "auto" ]]; then
    if command -v docker >/dev/null 2>&1 \
        && docker compose ps --status running postgres >/dev/null 2>&1; then
        rabbit_force_expiry="true"
    else
        rabbit_force_expiry="false"
    fi
fi

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
faculty_cookies="${rabbit_smoke_tmp}/faculty.cookies"
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

for endpoint in auth/me academic-catalog questions assessments dashboard pilot-readiness; do
    curl --fail --silent --show-error \
        --cookie "${admin_cookies}" \
        "${rabbit_base_url}/gateway/backend/${endpoint}" \
        >"${rabbit_smoke_tmp}/admin-${endpoint//\//-}.json"
done

grep --quiet '"email":"admin@demo.rabbit.local"' \
    "${rabbit_smoke_tmp}/admin-auth-me.json"
grep --quiet '"organisationName":"Rabbit Demo Academy"' \
    "${rabbit_smoke_tmp}/admin-auth-me.json"
grep --quiet '"role":"ORG_ADMIN"' \
    "${rabbit_smoke_tmp}/admin-dashboard.json"
grep --quiet '"totalChecks":19' \
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
    --cookie-jar "${faculty_cookies}" \
    --header "Content-Type: application/json" \
    --data "{\"email\":\"${invited_email}\",\"password\":\"${invited_password}\"}" \
    "${rabbit_base_url}/gateway/auth/login" \
    >"${rabbit_smoke_tmp}/invited-first-login.json"
grep --quiet '"firstLogin":true' \
    "${rabbit_smoke_tmp}/invited-first-login.json"

curl --fail --silent --show-error \
    --cookie "${faculty_cookies}" \
    "${rabbit_base_url}/gateway/backend/dashboard" \
    >"${rabbit_smoke_tmp}/faculty-dashboard.json"
grep --quiet '"role":"FACULTY"' \
    "${rabbit_smoke_tmp}/faculty-dashboard.json"

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
    if [[ "${failed_attempt}" != "5" ]]; then
        sleep 6
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

curl --fail --silent --show-error \
    --cookie "${student_cookies}" \
    "${rabbit_base_url}/gateway/backend/dashboard" \
    >"${rabbit_smoke_tmp}/student-dashboard.json"
grep --quiet '"role":"STUDENT"' \
    "${rabbit_smoke_tmp}/student-dashboard.json"

available_json="$(
    curl --fail --silent --show-error \
        --cookie "${student_cookies}" \
        "${rabbit_base_url}/gateway/backend/student/assessments"
)"
assessment_id="$(
    python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])' \
        <<<"${available_json}"
)"

curl --fail --silent --show-error \
    --cookie "${student_cookies}" \
    "${rabbit_base_url}/gateway/backend/student/assessments/${assessment_id}" \
    >"${rabbit_smoke_tmp}/assessment-instructions.json"
python3 -c \
    'import json,sys; row=json.load(sys.stdin); assert row["id"] == sys.argv[1]; assert row["serverNow"]; assert row["attemptsAllowed"] >= 1; assert row["attemptsUsed"] == 0' \
    "${assessment_id}" <"${rabbit_smoke_tmp}/assessment-instructions.json"

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

resume_json="$(
    curl --fail --silent --show-error \
        --cookie "${student_cookies}" \
        --request POST \
        "${rabbit_base_url}/gateway/backend/student/assessments/${assessment_id}/attempts"
)"
python3 -c \
    'import json,sys; started=json.loads(sys.argv[1]); resumed=json.load(sys.stdin); assert resumed["attemptId"] == started["attemptId"]; assert [[q["id"] for q in row["questions"]] for row in (started,resumed)][0] == [[q["id"] for q in row["questions"]] for row in (started,resumed)][1]; assert [[[option["id"] for option in question["options"]] for question in row["questions"]] for row in (started,resumed)][0] == [[[option["id"] for option in question["options"]] for question in row["questions"]] for row in (started,resumed)][1]' \
    "${attempt_json}" <<<"${resume_json}"

curl --fail --silent --show-error \
    --cookie "${student_cookies}" \
    "${rabbit_base_url}/gateway/backend/student/attempts/history" \
    >"${rabbit_smoke_tmp}/history-in-progress.json"
python3 -c \
    'import json,sys; rows=json.load(sys.stdin); assert any(row["attemptId"] == sys.argv[1] and row["status"] == "IN_PROGRESS" for row in rows)' \
    "${attempt_id}" <"${rabbit_smoke_tmp}/history-in-progress.json"

curl --fail --silent --show-error \
    --cookie "${admin_cookies}" \
    "${rabbit_base_url}/gateway/backend/evaluation/assessments/${assessment_id}/monitor" \
    >"${rabbit_smoke_tmp}/monitor-in-progress.json"
python3 -c \
    'import json,sys; monitor=json.load(sys.stdin); assert monitor["inProgress"] >= 1; assert any(row["attemptId"] == sys.argv[1] and row["attemptStatus"] == "IN_PROGRESS" for row in monitor["attempts"])' \
    "${attempt_id}" <"${rabbit_smoke_tmp}/monitor-in-progress.json"

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

if [[ "${rabbit_force_expiry}" == "true" ]]; then
    docker compose exec -T postgres \
        psql \
        --username "${POSTGRES_USER:-rabbit}" \
        --dbname "${POSTGRES_DB:-rabbit}" \
        --set ON_ERROR_STOP=1 \
        --command "UPDATE assessment_attempts SET expires_at = now() - interval '1 second' WHERE id = '${attempt_id}'::uuid AND status = 'IN_PROGRESS';" \
        >/dev/null

    expiry_processed="false"
    for expiry_poll in $(seq 1 15); do
        curl --fail --silent --show-error \
            --cookie "${student_cookies}" \
            "${rabbit_base_url}/gateway/backend/student/attempts/history" \
            >"${rabbit_smoke_tmp}/history-expiry-poll.json"
        if python3 -c \
            'import json,sys; rows=json.load(sys.stdin); sys.exit(0 if any(row["attemptId"] == sys.argv[1] and row["status"] == "AUTO_SUBMITTED" for row in rows) else 1)' \
            "${attempt_id}" <"${rabbit_smoke_tmp}/history-expiry-poll.json"; then
            expiry_processed="true"
            break
        fi
        sleep 2
    done
    if [[ "${expiry_processed}" != "true" ]]; then
        echo "Server expiry worker did not auto-submit attempt ${attempt_id}." >&2
        exit 1
    fi
    curl --fail --silent --show-error \
        --cookie "${student_cookies}" \
        "${rabbit_base_url}/gateway/backend/student/results/${attempt_id}" \
        >"${rabbit_smoke_tmp}/submitted-result.json"
else
    curl --fail --silent --show-error \
        --cookie "${student_cookies}" \
        --request POST \
        "${rabbit_base_url}/gateway/backend/student/attempts/${attempt_id}/submit" \
        >"${rabbit_smoke_tmp}/submitted-result.json"
fi
grep --quiet '"publicationStatus":"PENDING_PUBLICATION"' \
    "${rabbit_smoke_tmp}/submitted-result.json"

curl --fail --silent --show-error \
    --cookie "${student_cookies}" \
    "${rabbit_base_url}/gateway/backend/student/attempts/history" \
    >"${rabbit_smoke_tmp}/history-pending.json"
python3 -c \
    'import json,sys; row=next(row for row in json.load(sys.stdin) if row["attemptId"] == sys.argv[1]); assert row["status"] in ("SUBMITTED", "AUTO_SUBMITTED"); assert row["publicationStatus"] == "PENDING_PUBLICATION"; assert "score" not in row and "percentage" not in row' \
    "${attempt_id}" <"${rabbit_smoke_tmp}/history-pending.json"

curl --fail --silent --show-error \
    --cookie "${admin_cookies}" \
    "${rabbit_base_url}/gateway/backend/evaluation/assessments/${assessment_id}/monitor" \
    >"${rabbit_smoke_tmp}/monitor-submitted.json"
python3 -c \
    'import json,sys; monitor=json.load(sys.stdin); row=next(row for row in monitor["attempts"] if row["attemptId"] == sys.argv[1]); assert row["attemptStatus"] in ("SUBMITTED", "AUTO_SUBMITTED"); assert row["secondsRemaining"] == 0' \
    "${attempt_id}" <"${rabbit_smoke_tmp}/monitor-submitted.json"

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

short_reason_status="$(
    curl --silent --show-error \
        --output "${rabbit_smoke_tmp}/short-re-evaluation.json" \
        --write-out "%{http_code}" \
        --cookie "${admin_cookies}" \
        --request POST \
        --header "Content-Type: application/json" \
        --data '{"reason":"short"}' \
        "${rabbit_base_url}/gateway/backend/evaluation/attempts/${attempt_id}/re-evaluate"
)"
if [[ "${short_reason_status}" != "400" ]]; then
    echo "Short re-evaluation reason returned HTTP ${short_reason_status}; expected 400." >&2
    exit 1
fi

curl --fail --silent --show-error \
    --cookie "${admin_cookies}" \
    --request POST \
    --header "Content-Type: application/json" \
    --data '{"reason":"Verified scoring rules after governed review."}' \
    "${rabbit_base_url}/gateway/backend/evaluation/attempts/${attempt_id}/re-evaluate" \
    >"${rabbit_smoke_tmp}/re-evaluated.json"
grep --quiet '"publicationStatus":"PENDING_PUBLICATION"' \
    "${rabbit_smoke_tmp}/re-evaluated.json"
grep --quiet '"evaluationVersion":2' \
    "${rabbit_smoke_tmp}/re-evaluated.json"

curl --fail --silent --show-error \
    --cookie "${student_cookies}" \
    "${rabbit_base_url}/gateway/backend/student/attempts/history" \
    >"${rabbit_smoke_tmp}/history-after-re-evaluation.json"
python3 -c \
    'import json,sys; row=next(row for row in json.load(sys.stdin) if row["attemptId"] == sys.argv[1]); assert row["publicationStatus"] == "PENDING_PUBLICATION"; assert row["evaluationVersion"] == 2; assert "score" not in row' \
    "${attempt_id}" <"${rabbit_smoke_tmp}/history-after-re-evaluation.json"

curl --fail --silent --show-error \
    --cookie "${admin_cookies}" \
    --request POST \
    "${rabbit_base_url}/gateway/backend/evaluation/assessments/${assessment_id}/publish" \
    >"${rabbit_smoke_tmp}/republication.json"

curl --fail --silent --show-error \
    --cookie "${student_cookies}" \
    "${rabbit_base_url}/gateway/backend/student/results/${attempt_id}" \
    >"${rabbit_smoke_tmp}/republished-result.json"
grep --quiet '"publicationStatus":"PUBLISHED"' \
    "${rabbit_smoke_tmp}/republished-result.json"
grep --quiet '"evaluationVersion":2' \
    "${rabbit_smoke_tmp}/republished-result.json"

curl --fail --silent --show-error \
    --cookie "${admin_cookies}" \
    "${rabbit_base_url}/gateway/backend/reports/students?query=Rohan&subjectId=22222222-2222-2222-2222-222222222201&assessmentType=CHAPTER_TEST&departmentId=11111111-1111-1111-1111-111111111130&sectionId=11111111-1111-1111-1111-111111111140&submittedFrom=2020-01-01T00%3A00%3A00Z&submittedBefore=2100-01-01T00%3A00%3A00Z" \
    >"${rabbit_smoke_tmp}/filtered-student-report.json"
python3 -c \
    'import json,sys; report=json.load(sys.stdin); assert report["totalStudents"] == 1; assert report["publishedResults"] >= 1; assert any(row["studentUserId"] == sys.argv[1] for row in report["students"]); assert report["departments"] and report["sections"]' \
    "33333333-3333-3333-3333-333333333304" \
    <"${rabbit_smoke_tmp}/filtered-student-report.json"

curl --fail --silent --show-error \
    --cookie "${admin_cookies}" \
    "${rabbit_base_url}/gateway/backend/reports/students/33333333-3333-3333-3333-333333333304" \
    >"${rabbit_smoke_tmp}/individual-student-report.json"
grep --quiet '"studentName":"Rohan Iyer"' \
    "${rabbit_smoke_tmp}/individual-student-report.json"

curl --fail --silent --show-error \
    --cookie "${student_cookies}" \
    "${rabbit_base_url}/gateway/backend/student/assessments/${assessment_id}" \
    >"${rabbit_smoke_tmp}/instructions-after-attempt.json"
python3 -c \
    'import json,sys; row=json.load(sys.stdin); assert row["attemptsUsed"] >= 1; assert "inProgressAttemptId" not in row' \
    <"${rabbit_smoke_tmp}/instructions-after-attempt.json"

curl --fail --silent --show-error \
    --cookie "${student_cookies}" \
    "${rabbit_base_url}/gateway/backend/student/attempts/history" \
    >"${rabbit_smoke_tmp}/history-published.json"
python3 -c \
    'import json,sys; row=next(row for row in json.load(sys.stdin) if row["attemptId"] == sys.argv[1]); assert row["publicationStatus"] == "PUBLISHED"; assert row["evaluationVersion"] == 2; assert row["score"] is not None' \
    "${attempt_id}" <"${rabbit_smoke_tmp}/history-published.json"

echo "Rabbit controlled-pilot smoke test passed."
