#!/usr/bin/env bash
set -euo pipefail

if (($# != 2)) || [[ "$2" != '--confirm-retire-demo-users' ]]; then
  cat >&2 <<'EOF'
Usage: ./infra/pilot/retire-demo-users.sh \
  <activated-replacement-admin-email> --confirm-retire-demo-users

Before running this command, use Rabbit to invite and activate a non-demo
Organisation Admin, then prove that account can sign in. The command suspends
the four seeded @demo.rabbit.local identities and revokes their refresh tokens.
It does not delete academic or assessment demonstration data.
EOF
  exit 2
fi

replacement_email="${1,,}"
if [[ ! "${replacement_email}" =~ ^[A-Za-z0-9.!#$%\&\'*+/=?^_\`{|}~-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]] \
  || [[ "${replacement_email}" == *@demo.rabbit.local ]]; then
  echo "Replacement administrator must be a valid non-demo email address." >&2
  exit 2
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
cd "${repo_root}"
if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
  echo "Docker Engine is required and must be running." >&2
  exit 1
fi

docker compose exec -T postgres sh -eu -c \
  'psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --set ON_ERROR_STOP=1 --set replacement_email="$1"' \
  -- "${replacement_email}" <<'SQL'
BEGIN;

SELECT set_config(
  'rabbit.replacement_admin_email',
  :'replacement_email',
  true
);

DO $body$
DECLARE
  replacement RECORD;
  demo_organisation_id UUID;
  demo_user_count INTEGER;
  active_demo_count INTEGER;
  retired_count INTEGER;
BEGIN
  SELECT ua.id AS user_id, ua.email, om.organisation_id
    INTO replacement
  FROM user_accounts ua
  JOIN organisation_memberships om ON om.user_id = ua.id
  JOIN organisations organisation ON organisation.id = om.organisation_id
  WHERE lower(ua.email) = lower(current_setting('rabbit.replacement_admin_email'))
    AND ua.status = 'ACTIVE'
    AND om.status = 'ACTIVE'
    AND om.role = 'ORG_ADMIN'
    AND organisation.code = 'DEMO';

  IF NOT FOUND THEN
    RAISE EXCEPTION
      'Replacement user must be an active ORG_ADMIN in the DEMO organisation';
  END IF;

  SELECT id INTO demo_organisation_id
  FROM organisations
  WHERE code = 'DEMO';

  SELECT COUNT(*), COUNT(*) FILTER (WHERE status = 'ACTIVE')
    INTO demo_user_count, active_demo_count
  FROM user_accounts
  WHERE email LIKE '%@demo.rabbit.local';

  IF demo_user_count < 4 THEN
    RAISE EXCEPTION 'Expected at least 4 seeded demo users, found %', demo_user_count;
  END IF;
  IF active_demo_count = 0 THEN
    RAISE EXCEPTION 'Every demo user is already inactive';
  END IF;

  UPDATE refresh_tokens token
  SET revoked_at = COALESCE(token.revoked_at, now()),
      updated_at = now()
  FROM user_accounts demo_user
  WHERE token.user_id = demo_user.id
    AND demo_user.email LIKE '%@demo.rabbit.local';

  UPDATE organisation_memberships membership
  SET status = 'SUSPENDED',
      updated_at = now()
  FROM user_accounts demo_user
  WHERE membership.user_id = demo_user.id
    AND demo_user.email LIKE '%@demo.rabbit.local'
    AND membership.status = 'ACTIVE';

  UPDATE user_accounts
  SET status = 'SUSPENDED',
      password_hash = crypt(encode(gen_random_bytes(32), 'hex'), gen_salt('bf', 12)),
      failed_attempts = 0,
      locked_until = NULL,
      updated_at = now()
  WHERE email LIKE '%@demo.rabbit.local'
    AND status = 'ACTIVE';

  GET DIAGNOSTICS retired_count = ROW_COUNT;
  IF EXISTS (
    SELECT 1 FROM user_accounts
    WHERE email LIKE '%@demo.rabbit.local' AND status = 'ACTIVE'
  ) THEN
    RAISE EXCEPTION 'One or more demo users remained active after retirement';
  END IF;

  INSERT INTO audit_events (
    id, organisation_id, actor_user_id, module, action, entity_type,
    entity_id, status, before_value, after_value, actor_email, actor_role,
    ip_address, trace_id, created_at, updated_at
  ) VALUES (
    gen_random_uuid(), replacement.organisation_id, replacement.user_id,
    'OPS', 'RETIRE_DEMO_USERS', 'Organisation', demo_organisation_id,
    'SUCCESS', active_demo_count || ' demo identities active',
    retired_count || ' demo identities suspended; credentials invalidated; refresh tokens revoked',
    replacement.email, 'ORG_ADMIN', 'LOCAL_HOST',
    'm5.1-demo-retirement', now(), now()
  );
END
$body$;

COMMIT;
SQL

echo "Seeded demo identities are suspended, their credentials are invalidated,"
echo "and their refresh tokens are revoked."
echo "Replacement administrator retained: ${replacement_email}"
echo "The action was recorded in Rabbit's audit_events table."
