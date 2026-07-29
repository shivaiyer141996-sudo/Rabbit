.PHONY: up down logs test frontend-test backend-test

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
