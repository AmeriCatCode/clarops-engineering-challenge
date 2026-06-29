# AI Usage

## Tools Used

- Claude
- ChatGPT

## Prompts

### Prompt 1 — Requirement analysis
```
As a developer, help me to:
- Understand the main problem this challenge is trying to solve
- Identify the essential scenarios that should guide the implementation and tests.

Analyze the information documented in `@CLAUDE.md` and `@README.md`, especially the following README sections:
- Goal
- Functional Requirements
- Trace Statuses
- Status Response Contract
- Expected Behavior
- Waiting for Another Event
- Expected Event Arrives
- TTL Expiration
- Final Event
- Event Result
- Database Requirement
- Required Deliverables
- Unit Tests Requirement
- E2E Validation with Hurl

Add the output you produce in the @README.md under the following sections:
## Proposed Solution 
### Problem Understanding 
### Essential Functional Scenarios 
### POST /events Requirements Matrix 
### GET /traces/{traceId}/status Requirements Matrix

Content expectations:
- In Problem Understanding, explain the challenge in simple terms.
- In Essential Functional Scenarios, summarize the core scenarios, edge cases, validation scenarios, and persistence/database-related scenarios.
- In POST /events Requirements Matrix, create a summarized matrix covering first event, waiting event, final event, duplicate eventId, unexpected event, late event, completed trace, expired trace, invalid payloads, ERROR result, and metadata.
- In GET /traces/{traceId}/status Requirements Matrix, create a summarized matrix covering trace not found, STARTED, WAITING_OTHER_EVENT, TTL expired on query, COMPLETED, and already expired traces.

Important:
- Edit the `@README.md` file directly.
- Do not only print the proposed content in the response.
- Do not add implementation code yet.
- Do not invent unsupported requirements.
- Mark ambiguous behavior as an assumption.
- Keep the scope aligned with the MVP.
- Do not include background jobs, queues, retries, or alerting as required features.
- Keep the section concise, practical, and useful for guiding implementation and tests.
```

### Prompt 2 — Open Requirements and Edge Cases analysis
```
As a developer, help me identify the open requirements, edge cases, and recommended decisions for this challenge.

Analyze the information documented in `@CLAUDE.md` and `@README.md`, especially the existing ## Proposed Solution section and the original challenge requirements.

Add the output you produce to @README.md in a new section `### Open Requirements and Edge Cases` under the following existing section `## Proposed Solution`.

Content expectations:
* List the main open requirements or ambiguous behaviors described by the challenge.
* List the most important edge cases that should be considered during implementation and testing.
* For each item, include:
  * The open question or edge case
  * A recommended decision for this MVP
  * A short justification
  * Whether it should be covered by unit tests, Hurl tests, or both

Please include at least these topics:
* Duplicate `eventId`
* Unexpected `eventName`
* Expected event arriving after TTL expiration
* TTL calculation using `occurredAt` vs service received time
* Completed traces receiving more events
* First event also being a final event
* `result = ERROR` with or without `nextExpectedEvent`
* Missing or invalid required fields
* Invalid `nextExpectedEvent` / `nextEventTtlSeconds` combination
* Metadata storage
* Trace not found
* Avoiding inconsistent trace states
* Whether expired traces should accept more events
* Whether `STARTED` traces can remain open indefinitely

Important:

* Edit the `@README.md` file directly.
* Do not only print the proposed content in the response.
* Do not add implementation code yet.
* Do not invent unsupported requirements.
* Mark ambiguous behavior clearly as an assumption or proposed decision.
* Keep the scope aligned with the MVP.
* Do not include background jobs, queues, retries, or alerting as required features.
* Keep the section concise, practical, and useful for guiding implementation and tests.
```

### Prompt 3 — Task breakdown
```
As a developer, help me create or update `TASKS.md` with a concise and actionable development plan for this challenge.

Analyze `@README.md`, `@CLAUDE.md`, and the existing `## Proposed Solution section`.

The task list should reflect the work needed to implement the service, including:

* Database schema and SQL initialization script under `docker/init-scripts/db/`
* `POST /events` ingestion contract, validation, event persistence, and trace state creation/update
* Trace status logic for `STARTED`, `WAITING_OTHER_EVENT`, `TTL_EXPIRED_FOR_EVENT`, and `COMPLETED`
* `GET /traces/{traceId}/status` endpoint
* Layered code structure: controllers, services, repositories, DTOs, and DAOs/models
* Reusable error handling
* Basic logging for important service operations

Important:

* Edit or create `TASKS.md` directly.
* Keep the file concise and practical.
* Do not repeat the full README requirements.
* Do not include implementation code.
* Organize tasks as checklists that can be marked as completed.
* Add short notes only where a technical decision needs to be recorded.
* Keep the scope aligned with the MVP.
* Do not add background jobs, queues, retries, or alerting as required tasks.
```

### Prompt 3 — Unit test standard

## Manual Decisions
### Timestamp Handling 
The event model includes two timestamps: `occurredAt` and `receivedAt`.
`occurredAt` comes from the request payload and represents when the event occurred in the external system. The TTL is calculated from this `occurredAt` field.
`receivedAt` is generated internally when the event is stored and represents when the watchdog service received it. 
This distinction can support future observability needs, such as detecting delivery delays.

### Reusable error handling
A reusable error handling structure is used to keep error responses consistent across the application and make it easier to add new error types.

### Basic reusable logging for important operations
A basic reusable logging approach is added for important operations to capture execution start, end, duration, date/time, and error details when failures occur.

## Accepted Suggestions
- Use a database transaction and lock the trace row with `SELECT FOR UPDATE` to avoid inconsistent trace states during concurrent updates.
- Duplicate `eventId`: return `409 Conflict`.
- Completed trace receives event: return `409 Conflict`.
- Expired trace (`TTL_EXPIRED_FOR_EVENT`) receives any further event: return `409 Conflict`.
- First event is final: create the trace as `COMPLETED`.
- Final event is only accepted while the trace is active (`STARTED` or `WAITING_OTHER_EVENT`). A final event received after `COMPLETED` or `TTL_EXPIRED_FOR_EVENT` returns `409 Conflict`.
- `ERROR` with next expected event: accept the event and move to `WAITING_OTHER_EVENT`.
- `ERROR` without next expected event: accept the event and keep or set the trace as `STARTED` if it is not final.
- Missing or invalid required fields: return `400 Bad Request`.
- Incomplete next-event pair: return `400 Bad Request`.
- Unknown trace on status query: return `404 Not Found`.
- `STARTED` trace remains open indefinitely when no next expected event is defined.

## Rejected AI Suggestions and Final Decisions
- Rejected: return `200 OK` for a final event on an in-progress trace. Final decision: return `201 Created` because the event is persisted.
- Rejected: return `422 Unprocessable Entity` for late events after TTL expiration. Final decision: return `409 Conflict` because the event conflicts with the current trace state.
- Rejected: return `422 Unprocessable Entity` for unexpected events while waiting. Final decision: return `409 Conflict` because the event does not match the expected trace transition.
- Rejected: return `422 Unprocessable Entity` for any event on an expired trace. Final decision: return `409 Conflict` for consistency with all terminal state conflicts.
- Rejected: final events should move the trace to `COMPLETED` regardless of current state. Final decision: final events are only accepted while the trace is active; they return `409 Conflict` when the trace is already in a terminal state (`COMPLETED` or `TTL_EXPIRED_FOR_EVENT`).
- Rejected: use mixed success codes for `POST /events`. Final decision: return `201 Created` for every successfully persisted event.
