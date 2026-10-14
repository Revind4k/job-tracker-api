# job-tracker-api

A REST API for tracking job applications: Java 21 and Spring Boot 4, Postgres, Redis caching, JWT auth, and a Redis rate limiter. It ships with integration tests, a load test, a Dockerfile, CI, and Terraform for AWS.

## Results

Load test (k6, 20 virtual users for 30 s) of `GET /applications/stats` for a user with **50,000 applications**. Measured locally on an Apple M4 with Postgres 17 and Redis 8:

| | p95 latency | p99 latency | Throughput | Failed requests |
|---|---|---|---|---|
| Redis cache on | **1.66 ms** | 2.94 ms | **22,584 req/s** | 0 |
| Cache off | 65.5 ms | 73.5 ms | 371 req/s | 0 |

The stats endpoint aggregates every application by status and by week. Caching it per user, and evicting that entry on every write, gives about **39x lower p95 latency and 61x more throughput**, with reads never stale.

## API

| Method | Path | |
|---|---|---|
| POST | `/auth/register`, `/auth/login` | returns a 60-minute HS256 JWT |
| GET | `/applications?status=&page=&size=` | paged, newest first |
| POST | `/applications` | create |
| GET / PATCH / DELETE | `/applications/{id}` | PATCH changes only the fields sent |
| GET | `/applications/stats` | totals by status, response rate, last 12 weeks (cached) |

## Design notes

- **Per-user isolation.** Every query is scoped to the token's user id, so another user's application id returns 404, the same as a missing one.
- **Cache correctness.** `@Cacheable` on stats, `@CacheEvict` on create, update, and delete. A test asserts that a write evicts the entry.
- **Rate limiting.** A fixed-window counter in Redis (120 requests/min, per user when a token is present, per IP otherwise). `INCR` and `EXPIRE` run in one Lua script so concurrent requests cannot race. Responses carry `X-RateLimit-*` headers, and a 429 carries `Retry-After`. If Redis is down, the limiter **fails open**: a cache outage should not take the API down. That behavior is unit-tested.
- **Auth.** BCrypt password hashes. Login returns the same error for an unknown email and a wrong password, so the endpoint does not reveal which emails are registered.
- **Schema.** Flyway migrations, with Hibernate set to `validate` so the code and the schema cannot drift apart silently.

## Tests

8 tests run against real Postgres and Redis (GitHub Actions service containers in CI):
- the full CRUD flow plus stats
- isolation between users
- missing and forged tokens
- login error uniformity
- field-level validation errors
- cache eviction
- the 429 limit
- fail-open when Redis is down

```bash
./gradlew test   # needs Postgres (database jobtracker_test) and Redis on localhost
```

## Run it

```bash
docker compose up --build            # API on :8080 with Postgres and Redis
# or against local services:
createdb jobtracker && ./gradlew bootRun
```

Load test:

```bash
curl -X POST localhost:8080/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"loadtest@example.com","password":"loadtest-password"}'
psql -d jobtracker -f loadtest/seed.sql     # 50,000 applications for that user
k6 run loadtest/stats.js                    # rerun with CACHE_TYPE=none to compare
```

## AWS (Terraform)

`infra/` defines:
- ECR
- ECS Fargate behind an Application Load Balancer
- RDS Postgres 17 (encrypted, not publicly accessible)
- ElastiCache Redis
- the database password and JWT secret, generated into Secrets Manager and injected into the task
- an execution role that can read only that one secret

It uses the default VPC with no NAT gateway to keep costs down. CI runs `terraform fmt -check` and `terraform validate` on every push.

```bash
cd infra && terraform init && terraform apply
```
