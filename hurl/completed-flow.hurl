# Validates: trace progresses through WAITING_OTHER_EVENT then reaches COMPLETED via finalEvent=true
# Spec scenarios #2, #4, #5

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "eventId": "hurl-completed-evt-001",
    "traceId": "hurl-completed-trace-001",
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
    "eventId": "hurl-completed-evt-002",
    "traceId": "hurl-completed-trace-001",
    "eventName": "payment-confirmed",
    "result": "SUCCESS",
    "occurredAt": "2026-06-29T10:01:00Z",
    "finalEvent": true
}
HTTP 201

GET http://localhost:8080/api/traces/hurl-completed-trace-001/status
HTTP 200
[Asserts]
jsonpath "$.traceId" == "hurl-completed-trace-001"
jsonpath "$.status" == "COMPLETED"
jsonpath "$.lastEventName" == "payment-confirmed"
jsonpath "$.eventsReceived" == 2
