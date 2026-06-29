# Validates all 400 Bad Request and 404 Not Found decisions
# Open Req #9 (missing fields), #10 (incomplete pair), #12 (trace not found)

# --- Scenario 1: Missing required field (eventId) → 400 ---

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "traceId": "validation-trace-001",
    "eventName": "payment-initiated",
    "result": "SUCCESS",
    "occurredAt": "2026-06-29T10:00:00Z"
}
HTTP 400
[Asserts]
jsonpath "$.status" == 400

# --- Scenario 2: Invalid result value → 400 ---

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "eventId": "validation-evt-002",
    "traceId": "validation-trace-001",
    "eventName": "payment-initiated",
    "result": "PENDING",
    "occurredAt": "2026-06-29T10:00:00Z"
}
HTTP 400
[Asserts]
jsonpath "$.status" == 400

# --- Scenario 3: nextExpectedEvent without nextEventTtlSeconds → 400 ---

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "eventId": "validation-evt-003",
    "traceId": "validation-trace-001",
    "eventName": "payment-initiated",
    "result": "SUCCESS",
    "occurredAt": "2026-06-29T10:00:00Z",
    "nextExpectedEvent": "payment-confirmed"
}
HTTP 400
[Asserts]
jsonpath "$.status" == 400

# --- Scenario 4: nextEventTtlSeconds without nextExpectedEvent → 400 ---

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "eventId": "validation-evt-004",
    "traceId": "validation-trace-001",
    "eventName": "payment-initiated",
    "result": "SUCCESS",
    "occurredAt": "2026-06-29T10:00:00Z",
    "nextEventTtlSeconds": 300
}
HTTP 400
[Asserts]
jsonpath "$.status" == 400

# --- Scenario 5: Unknown traceId on status query → 404 ---

GET http://localhost:8080/api/traces/nonexistent-trace-xyz-404/status
HTTP 404
[Asserts]
jsonpath "$.status" == 404
