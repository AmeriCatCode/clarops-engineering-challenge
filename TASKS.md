# Tasks

## Task 1 — Define database schema and DDL

- [ ] Add DDL to `docker/init-scripts/db/01-init-schema.sql` inside `clarops_challenge_schema`.
- [ ] Create `trace_events` table to store the full history of received events.
  - Columns: `id` (PK), `event_id` (unique), `trace_id`, `event_name`, `result`, `occurred_at`, `received_at`, `next_expected_event`, `next_event_ttl_seconds`, `final_event`, `metadata` (JSONB).
  - Unique constraint on `event_id` (deduplication).
  - Index on `trace_id` (status lookups and event history).
- [ ] Create `trace_state` table to store one row per `traceId` with the current state.
  - Columns: `id` (PK), `trace_id` (unique), `status`, `last_event_id` (FK → `trace_events`), `last_event_name`, `last_event_result`, `next_expected_event`, `ttl_expires_at`, `events_received`, `created_at`, `updated_at`.
  - Unique constraint on `trace_id`.
  - Index on `trace_id`.
  > **Decision:** Store both event history and current trace state separately. History supports auditability; the state row supports efficient status queries without scanning all events.
  > `received_at` is set by the service at insert time. `ttl_expires_at` is calculated as `occurred_at + nextEventTtlSeconds` and stored as an absolute timestamp.

---

## Task 2 — Implement reusable error handling

- [ ] Create a standard error response DTO (e.g., `ErrorResponse`) with `status`, `error`, and `message` fields.
- [ ] Create typed exception classes: `ConflictException` (409), `NotFoundException` (404).
  > **Decision:** Validation errors (`400`) are handled by Spring's `MethodArgumentNotValidException`. Only business-logic errors need custom exceptions.
- [ ] Create a `@RestControllerAdvice` global handler that maps each exception type to the correct HTTP status and `ErrorResponse` body.

---

## Task 3 — Implement basic reusable logging

- [ ] Add structured logging at service entry and exit points using SLF4J.
- [ ] Log `traceId`, operation name, duration, and outcome (success or error class) for every service call.
- [ ] Log full exception details (class, message) on errors without exposing internal stack traces in the HTTP response.

---

## Task 4 — Implement `POST /events` ingestion

### 4a — Request contract and validation

- [ ] Create `EventRequest` DTO with Bean Validation:
  - `@NotBlank` on `eventId`, `traceId`, `eventName`, `result`, `occurredAt`.
  - `@Pattern` or `@NotNull` on `result` to enforce `SUCCESS` or `ERROR`.
  - Cross-field validation: `nextExpectedEvent` and `nextEventTtlSeconds` must both be present or both absent → `400 Bad Request`.
- [ ] Map `metadata` to a `Map<String, Object>` or `JsonNode`; store as JSONB.

### 4b — Controller

- [ ] Create `EventController` mapped to `POST /api/events`.
- [ ] Return `201 Created` for every successfully persisted event.

### 4c — Service: event persistence and trace state

- [ ] Check for duplicate `eventId` → throw `ConflictException` (409).
- [ ] Load the current `trace_state` row for the `traceId` using `SELECT FOR UPDATE` inside a transaction.
- [ ] Apply state transition rules (see Task 5) to determine acceptance or rejection.
- [ ] Persist the event to `trace_events` (always, if accepted).
- [ ] Create or update the `trace_state` row based on the transition result.
- [ ] Inject `receivedAt` = current UTC time before persisting.
- [ ] Calculate and store `ttl_expires_at` = `occurredAt + nextEventTtlSeconds` when applicable.

### 4d — Repositories

- [ ] Create `TraceEventRepository` (extends `JpaRepository<TraceEvent, UUID>`).
- [ ] Create `TraceStateRepository` (extends `JpaRepository<TraceState, UUID>`) with a `findByTraceId` method.

---

## Task 5 — Implement trace state transition logic

- [ ] Implement a `TraceStateService` (or equivalent) that encapsulates all state transition rules. Keep this class free of HTTP or persistence concerns so it can be unit-tested in isolation.
- [ ] **No existing trace:** create new `trace_state`.
  - `finalEvent = true` → `COMPLETED`.
  - `nextExpectedEvent` + `nextEventTtlSeconds` present → `WAITING_OTHER_EVENT`, set `ttl_expires_at`.
  - Otherwise → `STARTED`.
- [ ] **Existing trace in `STARTED` or `WAITING_OTHER_EVENT` (active):**
  - If current status is `WAITING_OTHER_EVENT` and TTL has expired → reject with `ConflictException` (409, late event).
  - If current status is `WAITING_OTHER_EVENT` and `eventName` ≠ `nextExpectedEvent` → reject with `ConflictException` (409, unexpected event).
  - If `finalEvent = true` → `COMPLETED`.
  - Else if `nextExpectedEvent` + TTL present → `WAITING_OTHER_EVENT`, update `ttl_expires_at`.
  - Else → `STARTED`.
- [ ] **Existing trace in terminal state (`COMPLETED` or `TTL_EXPIRED_FOR_EVENT`):** reject with `ConflictException` (409).
  > **Decision:** `result` (SUCCESS/ERROR) never drives state transitions. It is stored as-is and surfaced in the status response for observability only.
  > **Decision:** TTL expiration check at write time (on `POST /events`) uses `ttl_expires_at < now()`. Lazy evaluation at read time (on `GET`) also uses `ttl_expires_at < now()`.

---

## Task 6 — Implement `GET /traces/{traceId}/status`

- [ ] Create `TraceStatusController` mapped to `GET /api/traces/{traceId}/status`.
- [ ] Load `trace_state` by `traceId`; throw `NotFoundException` (404) if not found.
- [ ] Apply lazy TTL check: if `status = WAITING_OTHER_EVENT` and `ttl_expires_at < now()` → return `TTL_EXPIRED_FOR_EVENT`.
- [ ] Create `TraceStatusResponse` DTO with at minimum:
  - `traceId`, `status`, `lastEventName`, `lastEventResult`, `nextExpectedEvent`, `nextExpectedBefore` (= `ttl_expires_at`), `eventsReceived`.
  > **Decision:** `metadata` is not included in the status response for this MVP. It is available via the event history if needed.
- [ ] Return `200 OK` with the response body for all valid traces.


