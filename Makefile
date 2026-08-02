.PHONY: up down logs test frontend-test backend-test architecture-check \
	pilot-env pilot-config pilot-up pilot-down pilot-logs pilot-preflight \
	pilot-backup pilot-restore-drill pilot-retire-demo-users

PILOT_COMPOSE = docker compose --env-file .env -f docker-compose.yml -f infra/pilot/compose.local-pilot.yml

up:
	docker compose up --build

down:
	docker compose down

logs:
	docker compose logs -f

test: frontend-test backend-test

frontend-test:
	cd frontend && npm ci && npm run check

backend-test:
	cd backend && mvn test

architecture-check:
	./infra/architecture/verify-local-only.sh

pilot-env:
	./infra/pilot/prepare-local-env.sh

pilot-config:
	$(PILOT_COMPOSE) config --quiet

pilot-up: architecture-check pilot-config
	$(PILOT_COMPOSE) up --detach --build

pilot-down:
	$(PILOT_COMPOSE) down

pilot-logs:
	$(PILOT_COMPOSE) logs --follow

pilot-preflight:
	./infra/pilot/preflight.sh --runtime

pilot-backup:
	./infra/backup/backup.sh

pilot-restore-drill:
	@test -n "$(PILOT_BACKUP)" || (echo "Set PILOT_BACKUP to the backup directory to test." >&2; exit 2)
	./infra/backup/restore-drill.sh "$(PILOT_BACKUP)"

pilot-retire-demo-users:
	@test -n "$(PILOT_REPLACEMENT_ADMIN_EMAIL)" || (echo "Set PILOT_REPLACEMENT_ADMIN_EMAIL to an activated non-demo ORG_ADMIN." >&2; exit 2)
	@test "$(CONFIRM_RETIRE_DEMO_USERS)" = "yes" || (echo "Set CONFIRM_RETIRE_DEMO_USERS=yes after verifying the replacement administrator login." >&2; exit 2)
	./infra/pilot/retire-demo-users.sh "$(PILOT_REPLACEMENT_ADMIN_EMAIL)" --confirm-retire-demo-users
