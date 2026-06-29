# Validates all 409 Conflict decisions
# Open Req #1 (duplicate), #2 (unexpected), #3 (late), #5 (completed), #14 (expired)

# --- Scenario 1: Duplicate eventId → 409 ---

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "eventId": "conflict-dup-evt-001",
    "traceId": "conflict-dup-trace-001",
    "eventName": "payment-initiated",
    "result": "SUCCESS",
    "occurredAt": "2026-06-29T10:00:00Z"
}
HTTP 201

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "eventId": "conflict-dup-evt-001",
    "traceId": "conflict-dup-trace-001",
    "eventName": "payment-initiated",
    "result": "SUCCESS",
    "occurredAt": "2026-06-29T10:00:00Z"
}
HTTP 409

# --- Scenario 2: Unexpected event while WAITING_OTHER_EVENT → 409 ---

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "eventId": "conflict-unexpected-evt-001",
    "traceId": "conflict-unexpected-trace-001",
    "eventName": "payment-initiated",
    "result": "SUCCESS",
    "occurredAt": "2026-06-29T10:00:00Z",
    "nextExpectedEvent": "payment-confirmed",
    "nextEventTtlSeconds": 300
}
HTTP 201

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "eventId": "conflict-unexpected-evt-002",
    "traceId": "conflict-unexpected-trace-001",
    "eventName": "payment-cancelled",
    "result": "SUCCESS",
    "occurredAt": "2026-06-29T10:01:00Z"
}
HTTP 409

# --- Scenario 3: Late event (correct name, TTL already expired) → 409 ---

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "eventId": "conflict-late-evt-001",
    "traceId": "conflict-late-trace-001",
    "eventName": "payment-initiated",
    "result": "SUCCESS",
    "occurredAt": "2020-01-01T00:00:00Z",
    "nextExpectedEvent": "payment-confirmed",
    "nextEventTtlSeconds": 1
}
HTTP 201

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "eventId": "conflict-late-evt-002",
    "traceId": "conflict-late-trace-001",
    "eventName": "payment-confirmed",
    "result": "SUCCESS",
    "occurredAt": "2026-06-29T10:01:00Z"
}
HTTP 409

# --- Scenario 4: Event on COMPLETED trace → 409 ---

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "eventId": "conflict-completed-evt-001",
    "traceId": "conflict-completed-trace-001",
    "eventName": "payment-confirmed",
    "result": "SUCCESS",
    "occurredAt": "2026-06-29T10:00:00Z",
    "finalEvent": true
}
HTTP 201

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "eventId": "conflict-completed-evt-002",
    "traceId": "conflict-completed-trace-001",
    "eventName": "payment-settled",
    "result": "SUCCESS",
    "occurredAt": "2026-06-29T10:01:00Z"
}
HTTP 409

# --- Scenario 5: Any event on expired trace → 409 (TTL check fires before name check) ---

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "eventId": "conflict-expired-evt-001",
    "traceId": "conflict-expired-trace-001",
    "eventName": "payment-initiated",
    "result": "SUCCESS",
    "occurredAt": "2020-01-01T00:00:00Z",
    "nextExpectedEvent": "payment-confirmed",
    "nextEventTtlSeconds": 1
}
HTTP 201

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "eventId": "conflict-expired-evt-002",
    "traceId": "conflict-expired-trace-001",
    "eventName": "any-other-event",
    "result": "SUCCESS",
    "occurredAt": "2026-06-29T10:01:00Z"
}
HTTP 409
