# Validates: first event with no nextExpectedEvent and finalEvent=false → STARTED
# Spec scenario #1 | POST returns 201 Created

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "eventId": "hurl-started-evt-001",
    "traceId": "hurl-started-trace-001",
    "eventName": "payment-initiated",
    "result": "SUCCESS",
    "occurredAt": "2026-06-29T10:00:00Z"
}
HTTP 201

GET http://localhost:8080/api/traces/hurl-started-trace-001/status
HTTP 200
[Asserts]
jsonpath "$.traceId" == "hurl-started-trace-001"
jsonpath "$.status" == "STARTED"
jsonpath "$.lastEventName" == "payment-initiated"
jsonpath "$.lastEventResult" == "SUCCESS"
jsonpath "$.eventsReceived" == 1
