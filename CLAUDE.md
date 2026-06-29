# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

A Spring Boot service (Java 21) for the **Distributed Event Watchdog Challenge**. The goal is to implement two endpoints that receive distributed flow events, track state by `traceId`, and detect TTL expiration for expected follow-up events.

Endpoints to build:

- `POST /api/events` — receive a flow event
- `GET /api/traces/{traceId}/status` — return current trace state

Trace statuses: `STARTED`, `WAITING_OTHER_EVENT`, `TTL_EXPIRED_FOR_EVENT`, `COMPLETED`.

## Commands

```bash
# Run the application (auto-starts Docker/PostgreSQL via spring-boot-docker-compose)
./mvnw spring-boot:run

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=YourTestClass

# Format code and run full verification (required before committing)
./mvnw clean spotless:apply verify

# Validate health endpoint
curl http://localhost:8080/api/health
```

If Docker does not start automatically, set the path manually:

```bash
export DOCKER_COMPOSE_FILE=/absolute/path/to/docker/docker-compose.yml
./mvnw spring-boot:run
```

To reset the database (re-runs init scripts):

```bash
cd docker && docker-compose down -v && docker-compose up --build
```

## Environment Setup

Copy `docker/example.env` to `docker/.env` and fill in values. The `application.yaml` datasource credentials must match `.env`. `docker/.env` is gitignored.

## Project Structure

```
src/main/java/com/clara/challenge/
  ClaropsChallengeApplication.java    — entry point
  HealthController.java               — existing GET /api/health endpoint
  health/                             — Health entity, repository, service (reference pattern)

src/main/resources/application.yaml  — server context-path (/api), datasource, JPA config
docker/
  docker-compose.yml                  — PostgreSQL 15 via Bitnami image
  init-scripts/db/01-init-schema.sql  — schema + health seed (add DDL here)
```

## Architecture Patterns

The existing `health` package defines the expected layer pattern:

- **Entity** (`@Entity`, Lombok `@Getter`) mapped to `clarops_challenge_schema`
- **Repository** (extends `JpaRepository`)
- **Service** (`@Service`, `@RequiredArgsConstructor`)
- **Controller** (`@RestController`, `@RequiredArgsConstructor`)

Follow this pattern for new features. All JPA entities must specify `schema = "clarops_challenge_schema"` in `@Table`.

## Key Configuration

- **Context path**: `/api` — all endpoints are under `/api/...`
- **DDL auto**: `none` — schema is managed entirely by `docker/init-scripts/db/01-init-schema.sql`
- **Spotless** enforces Google Java Format (style: GOOGLE), Markdown formatting, SQL formatting (DBeaver), and sorted `pom.xml` — runs at `verify` phase
- **MapStruct + Lombok** annotation processors are wired in `maven-compiler-plugin`

## Code Formatting

Spotless enforces Google Java Format. Run `./mvnw spotless:apply` to auto-fix before committing. The `verify` phase runs `spotless:check` and will fail if files are not formatted.

## Required Deliverables

Per the challenge spec, the submission must include:

- `TASKS.md` — task breakdown used during development
- `AI_USAGE.md` — AI tools used, prompts, accepted/rejected suggestions
- Updated `README.md` — assumptions, decisions, trade-offs, run instructions
- DDL added to `docker/init-scripts/db/01-init-schema.sql`
- Unit tests covering state transitions
- Hurl E2E tests in `hurl/` covering all four trace statuses

Run Hurl tests with:

```bash
hurl --test hurl/*.hurl
```

## Open Design Decisions to Document

The challenge intentionally leaves these open — document decisions in `README.md`:

- Duplicate `eventId` handling
- Unexpected event (wrong `eventName`) arriving while `WAITING_OTHER_EVENT`
- Late event arriving after TTL already expired
- Whether TTL is calculated from `occurredAt` or server receipt time
- Whether a `COMPLETED` trace accepts further events
- How `metadata` is stored (JSON column recommended for MVP)
- Response when querying a non-existent `traceId`
