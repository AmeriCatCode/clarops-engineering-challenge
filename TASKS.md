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

---

## Task 7 — Add unit tests

**Standard:**
- Test method names follow `shouldExpectedBehavior_WhenCondition`.
- Each test validates one business rule only.
- Use Arrange / Act / Assert structure.
- No Spring context. Tests instantiate classes directly.

### `TraceTransitionServiceTest`

Validates the state machine in isolation. Covers spec scenarios and decisions documented in Task 5.

State transitions:
- [ ] `shouldReturnStarted_WhenFirstEventHasNoNextExpectedEventAndIsNotFinal`
- [ ] `shouldReturnWaitingOtherEvent_WhenFirstEventDefinesNextExpectedEvent`
- [ ] `shouldReturnCompleted_WhenFirstEventIsFinal`
- [ ] `shouldReturnWaitingOtherEvent_WhenActiveTraceReceivesMatchingEventWithNextExpected`
- [ ] `shouldReturnCompleted_WhenActiveTraceReceivesFinalEvent`
- [ ] `shouldReturnStarted_WhenActiveWaitingTraceReceivesMatchingEventWithNoNextExpected`

Conflict detection — validates decisions: terminal states, unexpected/late events:
- [ ] `shouldThrowConflict_WhenCompletedTraceReceivesAnyEvent`
- [ ] `shouldThrowConflict_WhenExpiredTraceReceivesAnyEvent`
- [ ] `shouldThrowConflict_WhenUnexpectedEventArrivesWhileWaiting`
- [ ] `shouldThrowConflict_WhenLateEventArrivesAfterTtlExpired`

TTL calculation — validates decision: TTL calculated from `occurredAt`, not receipt time:
- [ ] `shouldSetTtlDeadlineToOccurredAtPlusTtlSeconds_WhenNextExpectedEventDefined`

`ERROR` result — validates spec: `result` does not drive state transitions:
- [ ] `shouldReturnWaitingOtherEvent_WhenResultIsErrorAndNextExpectedEventDefined`
- [ ] `shouldReturnStarted_WhenResultIsErrorAndNoNextExpectedEventAndNotFinal`

### `EventRequestValidationTest`

Validates DTO constraints directly using Jakarta Validator. Covers spec validation and the incomplete-pair decision.

- [ ] `shouldFailValidation_WhenEventIdIsMissing`
- [ ] `shouldFailValidation_WhenResultIsInvalid`
- [ ] `shouldFailValidation_WhenNextExpectedEventPresentButTtlSecondsMissing`
- [ ] `shouldFailValidation_WhenNextEventTtlSecondsPresentButExpectedEventMissing`

### `EventServiceTest`

Validates duplicate `eventId` detection at the persistence layer, mocking the repository.

- [ ] `shouldThrowConflict_WhenDuplicateEventIdReceived`

---

## Task 8 — Add Hurl E2E tests

**Standard:**
- Validate the service from the outside using only the public HTTP API.
- Each file covers one clear flow or scenario.
- Validate HTTP status codes and key response body fields.
- No direct database queries. All assertions use `POST /events` and `GET /traces/{traceId}/status`.
- All covered scenarios must pass in 100% of runs.

**File structure:**
```
hurl/
  started-flow.hurl
  waiting-other-event-flow.hurl
  completed-flow.hurl
  ttl-expired-flow.hurl
  conflict-scenarios.hurl
  validation-scenarios.hurl
```

Required coverage (spec):
- [ ] `started-flow.hurl` — POST first event with no continuation → GET returns `STARTED` and `201 Created`.
- [ ] `waiting-other-event-flow.hurl` — POST event with `nextExpectedEvent` + TTL → GET returns `WAITING_OTHER_EVENT`.
- [ ] `completed-flow.hurl` — POST events → final event → GET returns `COMPLETED`.
- [ ] `ttl-expired-flow.hurl` — POST event with `occurredAt` far in the past and `nextEventTtlSeconds: 1` → GET returns `TTL_EXPIRED_FOR_EVENT`.

Conflict scenarios (decisions from Open Requirements #1, 2, 3, 5, 14):
- [ ] `conflict-scenarios.hurl` — Covers:
  - Duplicate `eventId` → `409 Conflict`
  - Unexpected event while `WAITING_OTHER_EVENT` → `409 Conflict`
  - Late event after TTL expired → `409 Conflict`
  - Event on `COMPLETED` trace → `409 Conflict`
  - Event on `TTL_EXPIRED_FOR_EVENT` trace → `409 Conflict`

Validation scenarios (spec + decisions from Open Requirements #9, 10, 12):
- [ ] `validation-scenarios.hurl` — Covers:
  - Missing required field → `400 Bad Request`
  - Invalid `result` value → `400 Bad Request`
  - Incomplete `nextExpectedEvent` / `nextEventTtlSeconds` pair → `400 Bad Request`
  - Unknown `traceId` on status query → `404 Not Found`


