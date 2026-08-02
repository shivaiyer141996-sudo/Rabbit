# Local-only infrastructure policy

## Decision

**Status:** Accepted

**Decision date:** 2 August 2026

**Decision owner:** Shiva Iyer

**Applies until:** Rabbit has reached production scale and the owner explicitly
approves a replacement architecture in writing

Rabbit must prioritise zero-cost local infrastructure. Every feature and release
gate must run completely through Docker Compose with these locally operated
services:

- PostgreSQL for transactional and analytical data
- Redis for cache and distributed rate-limit state
- RabbitMQ for asynchronous events and delivery work
- MinIO for uploaded assets and object storage
- Spring Boot API, Next.js web application, and Nginx gateway

The default and Milestone 5 pilot path must not require AWS, Azure, GCP,
Kubernetes, a managed database, a managed cache/queue/object store, a paid cloud
service, a public tunnel, or a public Internet endpoint.

## Mandatory engineering rules

1. `docker-compose.yml` remains the complete runnable product boundary. A feature
   is incomplete if it works only against an external provider.
2. PostgreSQL, Redis, RabbitMQ, and MinIO remain Docker-internal in the base and
   pilot stacks. Only Nginx may publish a host port.
3. Nginx binds to `127.0.0.1` by default. A multi-user pilot may bind only to one
   explicit RFC1918 address assigned to the approved host. `0.0.0.0`, router port
   forwarding, public tunnels, and public addresses are prohibited.
4. Persistent state remains in the `postgres-data`, `redis-data`,
   `rabbitmq-data`, and `minio-data` named volumes. PostgreSQL and MinIO must be
   backed up to a separate approved local device.
5. Application code receives endpoints, credentials, pool limits, bucket names,
   and feature settings through environment configuration. Provider-specific
   endpoints must not be embedded in product logic.
6. No cloud SDK or Kubernetes client may become a required application
   dependency without a separately approved architecture decision.
7. Source control and CI may validate the repository, but the application,
   assessment journey, backup, restore, and pilot must not depend on a CI runner,
   registry, or hosted service being available.
8. Email and SMS adapters remain disabled until a provider and its ongoing cost,
   consent, monitoring, and operating owner are explicitly approved.
9. Release automation may build and scan images for verification, but it must not
   log in to a container registry, publish packages/images, or deploy Rabbit.
   Approved release images are exported with checksums to separate local media.
10. API and web release images carry the exact Git revision and release version
    as OCI labels; evidence must reject a stale or unlabelled application image.

Run the local policy check before pilot startup:

```bash
make architecture-check
```

The check verifies the required Compose services, private data network, local
volumes, release revision binding, absence of public infrastructure bindings,
cloud/Kubernetes infrastructure files, cloud SDKs, public tunnels, and registry
publishing permissions/actions.

## Portability without premature cloud adoption

Local-only does not mean hard-coded. The following boundaries remain external to
product code:

- JDBC URL, database credentials, Flyway locations, and Hikari pool settings
- Redis and RabbitMQ hosts, ports, and credentials
- MinIO/S3-compatible endpoint, access credentials, and bucket
- browser/API origins, web bind address, and release/environment identity

Another PostgreSQL instance can therefore be adopted later through an approved,
backup-led configuration and migration procedure. PostgreSQL is still the
Release 1.0 database engine: MySQL, SQL Server, Oracle, or a non-relational engine
would require new migrations and equivalent tenant-safety controls rather than a
simple setting change. See [Database portability](DATABASE_PORTABILITY.md).

## Future change gate

A future infrastructure change is permitted only after the owner approves:

- the measured local capacity limitation or production-scale requirement;
- one-time and recurring cost;
- data location, privacy, security, backup, recovery, and exit plan;
- configuration-only migration design, or any unavoidable application-code diff;
- rollback evidence and named operating ownership.

Until that decision exists, local Docker Compose is authoritative and no cloud
account, cluster, managed database, or paid infrastructure may be introduced.
