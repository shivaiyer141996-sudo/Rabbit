.PHONY: up down logs test frontend-test backend-test architecture-check \
	pilot-env pilot-config pilot-up pilot-down pilot-logs pilot-preflight \
	pilot-backup pilot-restore-drill pilot-retire-demo-users \
	pilot-ui-install pilot-ui-evidence pilot-performance pilot-security \
	pilot-functional-restore-drill pilot-rollback-rehearsal pilot-m5-3 \
	pilot-m5-4-freeze pilot-m5-4-reconcile pilot-m5-5-prepare pilot-m5-5-finalize \
	pilot-m5-6-prepare pilot-m5-6-verify-tag release-tag-check \
	m6-contract m6-activation-check

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

pilot-ui-install:
	cd frontend && npm ci && npx playwright install chromium

pilot-ui-evidence:
	./infra/pilot/ui-evidence.sh

pilot-performance:
	./infra/performance/run-pilot-load.sh

pilot-security:
	./infra/security/pilot-security-review.sh

pilot-functional-restore-drill:
	@test -n "$(PILOT_BACKUP)" || (echo "Set PILOT_BACKUP to the backup directory to test." >&2; exit 2)
	./infra/backup/functional-restore-drill.sh "$(PILOT_BACKUP)"

pilot-rollback-rehearsal:
	./infra/pilot/rollback-rehearsal.sh

pilot-m5-3:
	./infra/pilot/m5-3-evidence.sh

pilot-m5-4-freeze:
	./infra/pilot/m5-4-evidence.sh freeze

pilot-m5-4-reconcile:
	@test -n "$(PILOT_FREEZE_MANIFEST)" || (echo "Set PILOT_FREEZE_MANIFEST to a passed freeze-manifest.json." >&2; exit 2)
	./infra/pilot/m5-4-evidence.sh reconcile --freeze-manifest "$(PILOT_FREEZE_MANIFEST)"

pilot-m5-5-prepare:
	./infra/pilot/m5-5-evidence.sh prepare

pilot-m5-5-finalize:
	@test -n "$(PILOT_M5_5_PREPARED_MANIFEST)" || (echo "Set PILOT_M5_5_PREPARED_MANIFEST to a passed prepare-manifest.json." >&2; exit 2)
	./infra/pilot/m5-5-evidence.sh finalize --prepared-manifest "$(PILOT_M5_5_PREPARED_MANIFEST)"

pilot-m5-6-prepare:
	./infra/release/m5-6-evidence.sh prepare

pilot-m5-6-verify-tag:
	@test -n "$(PILOT_M5_6_PREPARED_MANIFEST)" || (echo "Set PILOT_M5_6_PREPARED_MANIFEST to a passed release-manifest.json." >&2; exit 2)
	./infra/release/m5-6-evidence.sh verify-tag --prepared-manifest "$(PILOT_M5_6_PREPARED_MANIFEST)"

release-tag-check:
	@test -n "$(RELEASE_TAG)" || (echo "Set RELEASE_TAG to the annotated vX.Y.Z tag." >&2; exit 2)
	./infra/release/verify-release-tag.sh "$(RELEASE_TAG)"

m6-contract:
	python3 infra/commercial/verify-m6-contract.py --repo-root .

m6-activation-check:
	@test -n "$(M6_ENV)" || (echo "Set M6_ENV to the protected local environment file." >&2; exit 2)
	python3 infra/commercial/verify-m6-contract.py --repo-root . --env-file "$(M6_ENV)"
