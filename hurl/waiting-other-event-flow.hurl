# Validates: event with nextExpectedEvent + TTL → WAITING_OTHER_EVENT
# Spec scenario #2 | GET returns nextExpectedEvent and ttlExpiresAt

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "eventId": "hurl-waiting-evt-001",
    "traceId": "hurl-waiting-trace-001",
    "eventName": "payment-initiated",
    "result": "SUCCESS",
    "occurredAt": "2026-06-29T10:00:00Z",
    "nextExpectedEvent": "payment-confirmed",
    "nextEventTtlSeconds": 300
}
HTTP 201

GET http://localhost:8080/api/traces/hurl-waiting-trace-001/status
HTTP 200
[Asserts]
jsonpath "$.traceId" == "hurl-waiting-trace-001"
jsonpath "$.status" == "WAITING_OTHER_EVENT"
jsonpath "$.lastEventName" == "payment-initiated"
jsonpath "$.nextExpectedEvent" == "payment-confirmed"
jsonpath "$.eventsReceived" == 1
