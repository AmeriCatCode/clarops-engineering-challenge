# Validates: TTL_EXPIRED_FOR_EVENT is returned lazily at query time
# occurredAt is set far in the past with a 1-second TTL so the deadline is always expired.
# Decision (Open Req #4): TTL = occurredAt + nextEventTtlSeconds

POST http://localhost:8080/api/events
Content-Type: application/json
{
    "eventId": "hurl-expired-evt-001",
    "traceId": "hurl-expired-trace-001",
    "eventName": "payment-initiated",
    "result": "SUCCESS",
    "occurredAt": "2020-01-01T00:00:00Z",
    "nextExpectedEvent": "payment-confirmed",
    "nextEventTtlSeconds": 1
}
HTTP 201

GET http://localhost:8080/api/traces/hurl-expired-trace-001/status
HTTP 200
[Asserts]
jsonpath "$.traceId" == "hurl-expired-trace-001"
jsonpath "$.status" == "TTL_EXPIRED_FOR_EVENT"
jsonpath "$.lastEventName" == "payment-initiated"
jsonpath "$.nextExpectedEvent" == "payment-confirmed"
