# Distributed Event Watchdog Challenge

## Context

In Clarops, some operational flows are distributed across multiple services. Each service may emit events when it completes an action. In many cases, after one event is received, another event is expected to arrive within a specific time window.

The goal of this challenge is to build a small service that receives distributed events, tracks the state of a flow by `traceId`, and reports whether the flow is started, waiting for another event, completed, or expired because an expected event did not arrive within the configured TTL.

This challenge is intentionally small in scope, but some requirements are intentionally open. We want to understand how you analyze ambiguity, define assumptions, split development work into tasks, use AI tools, and make technical decisions.

You may use AI tools such as ChatGPT, Claude, Cursor, Copilot, or similar.

---

## Running the Project

For setup and run instructions see **[SETUP.md](SETUP.md)**.

---

## Running the Hurl Tests

Install [Hurl](https://hurl.dev/docs/installation.html), then run all E2E tests with:

```bash
hurl --test hurl/*.hurl
```

The app must be running (`./mvnw spring-boot:run`) and the database must be clean before executing the full suite. Each Hurl file is self-contained and covers one clear flow or scenario:

| File | Coverage |
|------|----------|
| `started-flow.hurl` | Trace reaches `STARTED` |
| `waiting-other-event-flow.hurl` | Trace moves to `WAITING_OTHER_EVENT` |
| `completed-flow.hurl` | Trace reaches `COMPLETED` via `finalEvent = true` |
| `ttl-expired-flow.hurl` | Trace reaches `TTL_EXPIRED_FOR_EVENT` (lazy evaluation at query time) |
| `conflict-scenarios.hurl` | Duplicate `eventId`, unexpected event, late event, completed/expired trace receiving new events |
| `validation-scenarios.hurl` | Missing fields, invalid `result`, incomplete next-event pair, unknown `traceId` |

---

## Expected Duration

This challenge is designed to be completed in **3 to 4 hours**.

We do not expect a production-ready system. We expect a clear, functional MVP with good analysis, documented trade-offs, meaningful tests, and a reasonable implementation.

---

## Repository Notes

The repository already includes the base setup required to run with:

- Spring Boot
- PostgreSQL
- Basic application structure
- Database connectivity
- An existing endpoint that reads information from the database

You should build your solution on top of the existing project.

You do **not** need to add Kafka, SQS, Pub/Sub, Flyway, Liquibase, Docker, or any external event infrastructure.

---

# Goal

Build a service with two endpoints:

```
POST /events
GET /traces/{traceId}/status
```

The service must:

1. Receive events associated with a `traceId`.
2. Track the current state of the distributed flow.
3. Allow an event to define the next expected event and the TTL to wait for it.
4. Allow an event to indicate that the flow has finished.
5. Return the current status of the flow by `traceId`.
6. Detect TTL expiration when the expected next event does not arrive on time.

---

# Functional Requirements

## 1. Receive Events

The service must expose:

```
POST /events
```

This endpoint receives an event related to a distributed flow.

### Request Body Contract

```json
{
  "eventId": "evt-001",
  "traceId": "trace-123",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:00:00Z",
  "nextExpectedEvent": "RULES_EVALUATED",
  "nextEventTtlSeconds": 120,
  "finalEvent": false,
  "metadata": {
    "country": "MX",
    "entityId": "company-123"
  }
}
```

### Field Description

|         Field         |   Type   | Required |                            Description                             |
|-----------------------|----------|----------|--------------------------------------------------------------------|
| `eventId`             | string   | Yes      | Unique identifier of the event.                                    |
| `traceId`             | string   | Yes      | Identifier of the distributed flow.                                |
| `eventName`           | string   | Yes      | Name of the event being reported.                                  |
| `result`              | string   | Yes      | Result of the event. Allowed values: `SUCCESS`, `ERROR`.           |
| `occurredAt`          | datetime | Yes      | Date and time when the event occurred.                             |
| `nextExpectedEvent`   | string   | No       | Name of the next event expected for this trace.                    |
| `nextEventTtlSeconds` | number   | No       | Maximum number of seconds to wait for the next expected event.     |
| `finalEvent`          | boolean  | No       | Indicates whether this event completes the flow. Default: `false`. |
| `metadata`            | object   | No       | Flexible metadata associated with the event.                       |

---

## 2. Query Trace Status

The service must expose:

```
GET /traces/{traceId}/status
```

This endpoint returns the current state of the flow associated with the given `traceId`.

The status must be calculated based on the received events and the TTL of the next expected event.

The service does **not** need to implement a scheduler or background job. TTL expiration may be evaluated when the status endpoint is called.

---

# Trace Statuses

The service must support the following statuses:

|         Status          |                                               Description                                                |
|-------------------------|----------------------------------------------------------------------------------------------------------|
| `STARTED`               | The first event was received, but there is no next expected event defined and the flow is not completed. |
| `WAITING_OTHER_EVENT`   | The latest event defined a next expected event and its TTL has not expired yet.                          |
| `TTL_EXPIRED_FOR_EVENT` | The expected next event did not arrive before the configured TTL expired.                                |
| `COMPLETED`             | A received event marked the flow as completed using `finalEvent = true`.                                 |

---

# Status Response Contract

The exact response structure is part of the design you must define.

However, the response should include enough information to understand the current state of the trace, such as:

```json
{
  "traceId": "trace-123",
  "status": "WAITING_OTHER_EVENT",
  "lastEventName": "APPLICATION_RECEIVED",
  "lastEventResult": "SUCCESS",
  "nextExpectedEvent": "RULES_EVALUATED",
  "nextExpectedBefore": "2026-06-15T10:02:00Z",
  "eventsReceived": 1
}
```

You may add or remove fields if your design justifies it. Please document your decision.

---

# Expected Behavior

## First Event

When the first event for a `traceId` is received, the service must create the trace state.

The resulting status depends on the event:

- If the event defines a next expected event and TTL, the trace should move to `WAITING_OTHER_EVENT`.
- If the event does not define a next expected event and is not final, the trace should remain in `STARTED`.
- If the event is marked as final, the trace should move to `COMPLETED`.

---

## Waiting for Another Event

When an event defines `nextExpectedEvent` and `nextEventTtlSeconds`, the trace should wait for that event until the TTL expires.

You must decide and document how the TTL is calculated.

For example, you may calculate it from:

- `occurredAt`
- the time the service received the event
- another approach you consider better for this MVP

---

## Expected Event Arrives

If the next received event matches the expected event, the trace should advance.

The new event may:

- define another expected event;
- complete the flow;
- leave the flow in another valid state according to your design.

---

## TTL Expiration

If the expected event does not arrive within the configured TTL, the trace status must be reported as:

```
TTL_EXPIRED_FOR_EVENT
```

A real alerting system is not required. For this MVP, the expired status is enough to represent the alert.

---

## Final Event

If an event is received with `finalEvent = true`, the trace must be marked as:

```
COMPLETED
```

A completed trace should not be reported as expired.

---

## Event Result

The event result must support:

```
SUCCESS
ERROR
```

The `result` indicates the outcome of the event, not necessarily the overall status of the trace.

For example, a flow may be completed with a final event whose result is `ERROR`.

---

# Database Requirement

You must define the database schema required for your solution.

One of your development tasks must be:

> Define the DDL for the data model and include the initialization script inside the `docker/init-scripts/db/` folder.

You do **not** need to use Flyway, Liquibase, or any similar migration tool. A plain SQL initialization script is enough.

Your schema should support the behavior required by the challenge. You are expected to make and document your own decisions regarding:

- tables;
- columns;
- constraints;
- indexes;
- whether to store event history, current trace state, or both;
- how to store `metadata`.

---

# Open Questions

Some requirements are intentionally open. You are expected to identify them, make reasonable decisions, and document your assumptions.

Please include these decisions in your `README.md`.

Examples of open questions:

1. What should happen if the same `eventId` is received more than once?
2. What should happen if an event arrives with a different `eventName` than the currently expected event?
3. What should happen if the expected event arrives after the TTL already expired?
4. Should TTL be calculated from `occurredAt` or from the time the event was received by the service?
5. Should a completed trace accept more events?
6. What should happen if the first event is also a final event?
7. What should happen if an event with `result = ERROR` defines a next expected event?
8. Should `metadata` be stored as JSON, structured columns, or ignored?
9. How should the service avoid inconsistent trace states?
10. What should be returned when querying a `traceId` that does not exist?

There is no single correct answer for all of these. We care about your reasoning, trade-offs, and implementation quality.

---

# Required Deliverables

Your submitted fork must include:

1. Functional code.
2. Database schema DDL.
3. SQL initialization script inside the `docker/init-scripts/db/` folder.
4. `README.md` updated with:
   - problem understanding;
   - assumptions;
   - technical decisions;
   - trade-offs;
   - how to run the project;
   - how to run the Hurl tests;
   - request and response examples.
5. `TASKS.md` with the tasks used during development.
6. `AI_USAGE.md` with the AI tools and prompts used.
7. Unit tests.
8. Hurl end-to-end tests covering the expected use cases.
9. All Hurl tests must pass successfully.

---

Submission Instructions

# TASKS.md Requirement

The `TASKS.md` file is important.

We want to understand how you split the problem before implementing it, especially if you use an LLM to help you write the code.

A good task breakdown should avoid vague instructions such as:

```
Implement the whole challenge.
```

or:

```
Create the watchdog service.
```

Those tasks are too broad and may cause an LLM to infer too much, spend more time, produce unnecessary code, or use more tokens than needed.

Instead, prefer small and explicit tasks.

Example:

```markdown
# Tasks

## Task 1 — Define data model and DDL

- Define the tables required to support the solution.
- Define constraints and indexes.
- Add SQL initialization script under `docker/init-scripts/db/`.

## Task 2 — Implement event ingestion contract

- Create the request contract for `POST /events`.
- Validate required fields.
- Persist the received event.
- Create or update trace state.

## Task 3 — Implement trace status logic

- Implement the logic to return `STARTED`, `WAITING_OTHER_EVENT`, `TTL_EXPIRED_FOR_EVENT`, or `COMPLETED`.
- Define how TTL is calculated.
- Document edge-case decisions.

## Task 4 — Implement status endpoint

- Create `GET /traces/{traceId}/status`.
- Return the current trace state.
- Handle unknown `traceId`.

## Task 5 — Add tests

- Add unit tests for the core state transition logic.
- Add Hurl tests for at least two end-to-end scenarios.
```

You may define your own tasks. The important part is that they are clear, scoped, and useful for development.

---

# Unit Tests Requirement

You must include unit tests.

We do not expect unit tests to be randomly selected by an LLM. We expect you to define a clear testing standard and then use it consistently.

Your unit tests should focus on the core business logic, especially:

- first event creates the trace state;
- event with next expected event moves the trace to `WAITING_OTHER_EVENT`;
- TTL expiration returns `TTL_EXPIRED_FOR_EVENT`;
- final event moves the trace to `COMPLETED`;
- duplicate event behavior, if you choose to support it;
- late event behavior, if you choose to support it.

You do not need to test every possible edge case. We care more about whether your tests are meaningful, consistent, and aligned with your decisions.

---

## Unit Test Prompt Requirement

If you use AI to define or generate unit tests, you must include the prompt you used in `AI_USAGE.md`.

The prompt should define a testing standard. It should not simply ask the LLM to “write any tests”.

Avoid prompts like:

```
Write unit tests for this service.
```

Prefer prompts that define clear expectations, for example:

```
Define unit tests for the Event Watchdog state transition service.

Use the following standard:
- Test method names should follow: shouldExpectedBehavior_WhenCondition.
- Each test should validate one business rule only.
- Use Arrange / Act / Assert structure.
- Avoid testing framework or Spring wiring unless necessary.
- Focus on state transitions, TTL expiration, final event behavior, duplicate events, and late events.
- Do not add tests for behavior not defined in the challenge unless clearly marked as an assumption.
- For every test, explain which requirement or assumption it validates.
```

You may use your own standard. The important part is that your prompt makes the expected testing style explicit.

---

# E2E Validation with Hurl

The Hurl tests are part of the official validation criteria for this challenge.

We will use the Hurl end-to-end tests to validate the expected use cases implemented by the candidate. These tests are expected to run successfully in **100% of the covered scenarios**.

The goal of the Hurl tests is to validate the service from the outside, using the public HTTP contract, not internal implementation details.

The Hurl tests should validate behavior through the public HTTP API:

```
POST /events
GET /traces/{traceId}/status
```

Do not rely on direct database queries for Hurl validation unless you clearly justify it.

---

## Required Hurl Coverage

At minimum, the Hurl tests must cover:

1. A trace that starts and reaches `STARTED`.
2. A trace that moves to `WAITING_OTHER_EVENT`.
3. A trace that reaches `COMPLETED`.
4. A trace that reaches `TTL_EXPIRED_FOR_EVENT`.

If your implementation includes additional decisions, such as duplicate event handling, late event handling, or unexpected event handling, please include Hurl tests for those scenarios as well.

A suggested folder structure is:

```
hurl/
  started-flow.hurl
  waiting-other-event-flow.hurl
  completed-flow.hurl
  ttl-expired-flow.hurl
```

Please include instructions in the README explaining how to run the Hurl tests.

Example:

```bash
hurl --test hurl/*.hurl
```

---

# AI-Assisted Implementation

You may use AI tools freely to implement this challenge.

This includes, but is not limited to:

- data model design;
- DDL definition;
- Spring components;
- controllers;
- services;
- repositories;
- DTOs;
- unit tests;
- Hurl scripts;
- documentation;
- refactoring;
- debugging.

Using AI is allowed and expected.

However, the candidate is responsible for understanding the final solution.

During the technical interview, we may ask questions about any part of the implementation, including:

- why the data model was designed that way;
- why certain tables, columns, constraints, or indexes were created;
- why a specific state transition was implemented;
- how TTL expiration is calculated;
- how duplicate events are handled;
- how late events are handled;
- how unexpected events are handled;
- why certain unit tests were included or excluded;
- why the Hurl scenarios were selected;
- how the implementation would change with more time;
- what parts were generated by AI and what parts were manually adjusted.

The purpose is not to penalize AI usage. The purpose is to understand whether the candidate can reason about what the LLM produced and whether they can explain, validate, and challenge the generated code.

Generated code is acceptable. Unexplained generated code is not.

---

# AI_USAGE.md Requirement

Please include an `AI_USAGE.md` file with:

1. AI tools used.
2. Main prompts used.
3. Prompt used to define the unit test standard.
4. Prompts used to generate or refine Hurl tests.
5. What parts of the solution were generated or assisted by AI.
6. Which AI suggestions you accepted.
7. Which AI suggestions you rejected and why.
8. Any important correction you made to the AI output.
9. Any part of the generated code that required manual review or adjustment.

Example structure:

```markdown
# AI Usage

## Tools Used

- Claude
- ChatGPT
- Cursor

## Prompts

### Prompt 1 — Requirement analysis

> Help me identify edge cases for a service that receives distributed events and tracks TTL expiration for the next expected event.

### Prompt 2 — Task breakdown

> Split this challenge into small implementation tasks suitable for an LLM-assisted workflow. Avoid broad tasks that require too much inference.

### Prompt 3 — Unit test standard

> Define unit tests for the Event Watchdog state transition service.
>
> Use the following standard:
> - Test method names should follow: shouldExpectedBehavior_WhenCondition.
> - Each test should validate one business rule only.
> - Use Arrange / Act / Assert structure.
> - Focus on state transitions and TTL expiration.
> - Do not add tests for behavior not defined in the challenge unless clearly marked as an assumption.

### Prompt 4 — Hurl E2E scenarios

> Define Hurl end-to-end tests for the public HTTP contract of the Event Watchdog service.
>
> Cover these scenarios:
> - trace starts successfully;
> - trace waits for another event;
> - trace completes successfully;
> - trace expires when the expected event does not arrive within TTL.
>
> Validate only public API responses. Do not assert internal database details.

## Accepted Suggestions

- Example: Using lazy TTL evaluation in the status endpoint.
- Example: Storing event metadata as JSON.

## Rejected Suggestions

- Example: Kafka-based implementation, because the challenge does not require real event infrastructure.
- Example: Scheduler-based expiration, because lazy evaluation is enough for the MVP.

## Manual Decisions

- Example: TTL is calculated from `occurredAt`.
- Example: Duplicate `eventId` returns a conflict response.
- Example: A completed trace does not accept new events.
```

---

# Out of Scope

The following items are not required:

- Kafka, SQS, Pub/Sub, or any real message broker.
- Scheduler or background job.
- External alert notification system.
- UI.
- Authentication or authorization.
- Docker changes, unless you consider them necessary.
- Flyway, Liquibase, or database migration tools.
- Multi-tenant support.
- Multiple dynamic flow definitions.
- Production-grade observability.
- Distributed locks.
- Retry mechanisms.

---

# Submission Instructions

Please submit your solution as a fork of this repository.

## Expected Submission Format

1. Fork this repository.
2. Implement your solution in your fork.
3. Push all required changes to your fork.
4. Share the repository URL with the recruiting or interview team.

If your fork is private, please make sure the required reviewers have access before submitting it.

Do not submit the solution as a ZIP file unless explicitly requested by the team.

## Submission Checklist

Before submitting, please make sure your fork includes:

- Functional implementation.
- Database schema DDL.
- SQL initialization script inside the `docker/init-scripts/db/` folder.
- Updated `README.md`.
- `TASKS.md`.
- `AI_USAGE.md`.
- Unit tests.
- Hurl E2E tests.
- Instructions to run the application.
- Instructions to run the Hurl tests.
- All covered Hurl scenarios passing successfully.

The submitted repository should be self-contained enough for reviewers to run, inspect, and discuss during the technical interview.

# Evaluation Criteria

We will evaluate more than the final code.

Because this challenge is designed to be completed in **3 to 4 hours**, we do not expect every area to be equally polished. Candidates should prioritize the most important parts first.

## Highest-Weight Criteria

The following criteria carry the most weight:

| Priority |                    Criteria                    |                                                                       What we are looking for                                                                        |
|----------|------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1        | Requirement analysis and task breakdown        | Ability to identify ambiguity, define reasonable assumptions, and split the work into clear, scoped tasks that reduce unnecessary inference when using AI tools.     |
| 2        | Core implementation and technical design       | A simple but coherent implementation of events, trace state, TTL expiration, and completion using a reasonable data model.                                           |
| 3        | Validation and understanding of generated code | Hurl E2E tests passing for the expected use cases, meaningful unit tests, and ability to explain and defend implementation decisions during the technical interview. |

## Full Evaluation Criteria

|            Criteria             |                                       What we are looking for                                       |
|---------------------------------|-----------------------------------------------------------------------------------------------------|
| Requirement analysis            | Ability to identify ambiguity and define reasonable assumptions.                                    |
| Task breakdown                  | Clear, scoped tasks that reduce unnecessary inference when using AI tools.                          |
| Technical design                | Simple but coherent model for events, trace state, TTL, and completion.                             |
| Database design                 | Reasonable DDL, constraints, indexes, and initialization script.                                    |
| Implementation                  | Functional endpoints and correct core behavior.                                                     |
| Unit tests                      | Meaningful tests focused on business rules and state transitions.                                   |
| Hurl E2E tests                  | Public API validation for all expected use cases, with tests passing successfully.                  |
| AI usage                        | Clear documentation of prompts, accepted suggestions, rejected suggestions, and manual corrections. |
| Understanding of generated code | Ability to explain and defend implementation decisions during the technical interview.              |
| Communication                   | Clear README, trade-offs, and explanation of decisions.                                             |

A strong submission does not need to over-engineer the solution. A strong submission should show good judgment, a working core flow, clear validation, and ownership of the AI-assisted implementation.

---

# Interview Follow-up

After submitting the challenge, the technical interview may include questions about the implementation.

The interviewer may ask the candidate to explain:

- a specific class, function, or state transition;
- a DDL decision;
- a unit test;
- a Hurl scenario;
- a prompt used to generate part of the solution;
- a trade-off documented in the README;
- an edge case that was intentionally left out;
- how the implementation would evolve with more time.

The goal is to evaluate how the candidate works with AI-generated code, how they review it, how they reason about it, and whether they can take ownership of the final solution.

---

# Final Notes

Keep the solution simple.

We are not looking for a perfect event-driven platform. We are looking for a small and thoughtful implementation that shows how you reason about distributed events, TTL expiration, ambiguous requirements, database modeling, AI-assisted development, task decomposition, and testing standards.

---

## Proposed Solution

### Problem Understanding

In a distributed system, a business flow spans multiple services. Each service emits an event when it finishes a step. The challenge is that finishing one step often means another step must happen next — and it must happen within a bounded time window.

Without a centralized tracker, there is no easy way to know whether a flow is progressing normally, stuck waiting for the next step, or silently broken because a critical event never arrived.

This service solves that problem by acting as a stateful watchdog:

1. **It receives events** from any service involved in a flow, keyed by a shared `traceId`.
2. **It tracks flow state** — determining at any point whether the flow has started, is waiting for a follow-up event, has completed, or has expired because the follow-up never arrived in time.
3. **It evaluates TTL lazily** — expiration is not detected by a background scheduler, but calculated on demand when the status endpoint is called.

The core business rule is: if an event declares that another event is expected within N seconds, and that event does not arrive within N seconds, the flow is considered expired.

Each event carries two timestamps: `occurredAt` (provided by the caller — when the external service generated the event) and `receivedAt` (set internally by this service when the event is persisted). This separation makes it possible to detect clock skew between producers and to support future observability use cases without a schema change.

---

### Essential Functional Scenarios

#### Core Scenarios

| # | Scenario | Description |
|---|----------|-------------|
| 1 | First event, no continuation | A trace is created with status `STARTED`. |
| 2 | First event with next expected event | Trace is created with status `WAITING_OTHER_EVENT`; TTL window begins. |
| 3 | First event is also final | Trace is created and immediately moves to `COMPLETED`. |
| 4 | Expected event arrives before TTL | Trace advances; new state is determined by the arriving event's fields. |
| 5 | Final event on an in-progress trace | Trace moves to `COMPLETED` if the final event is accepted while the trace is still active (`STARTED` or `WAITING_OTHER_EVENT`). |
| 6 | Status queried before TTL expires | Returns `WAITING_OTHER_EVENT`. |
| 7 | Status queried after TTL expires | Returns `TTL_EXPIRED_FOR_EVENT` (evaluated at query time). |

#### Edge Cases

| # | Scenario | Decided Behavior |
|---|----------|-----------------|
| 8 | Duplicate `eventId` received | Return `409 Conflict`; do not reprocess or update trace state. |
| 9 | Unexpected event arrives while `WAITING_OTHER_EVENT` | Return `409 Conflict`; the trace keeps waiting. The event conflicts with the current expected state transition. |
| 10 | Late event arrives after TTL already expired | Return `409 Conflict`; trace remains `TTL_EXPIRED_FOR_EVENT`. The event conflicts with the terminal expired state. |
| 11 | Event received for a `COMPLETED` trace | Return `409 Conflict`; completed traces are closed and immutable. |
| 12 | `result = ERROR` with `nextExpectedEvent` defined | Accept the event. `result` reflects the step outcome, not the flow. Trace moves to `WAITING_OTHER_EVENT`. |
| 13 | `result = ERROR` without `nextExpectedEvent`, not final | Accept the event. `result` does not drive state transitions. Trace stays in `STARTED`. |

#### Validation Scenarios

| # | Scenario | Behavior |
|---|----------|----------|
| 14 | Missing required field (`eventId`, `traceId`, `eventName`, `result`, `occurredAt`) | `400 Bad Request` |
| 15 | Invalid `result` value (not `SUCCESS` or `ERROR`) | `400 Bad Request` |
| 16 | `nextExpectedEvent` provided without `nextEventTtlSeconds` (or vice versa) | `400 Bad Request`; they are treated as a required pair. |

#### Persistence / Database Scenarios

| # | Scenario | Behavior |
|---|----------|----------|
| 17 | Each received event is persisted | Full event history is retained for auditability. |
| 18 | Trace state is stored separately | A `trace_state` table holds the current status, last event info, and TTL deadline. |
| 19 | `metadata` is stored as a JSON column | Flexible enough for MVP without requiring a schema change per use case. |
| 20 | TTL deadline stored as an absolute timestamp | Calculated at write time from `occurredAt + nextEventTtlSeconds`; comparison at read time requires no calculation. |

---

### POST /events Requirements Matrix

| Scenario | Input Condition | Expected HTTP Response | Trace Status After | Source |
|----------|----------------|------------------------|-------------------|--------|
| First event, no `nextExpectedEvent`, not final | New `traceId`, no `nextExpectedEvent`, `finalEvent = false` | `201 Created` | `STARTED` | Spec |
| First event with `nextExpectedEvent` + TTL | New `traceId`, `nextExpectedEvent` set, `nextEventTtlSeconds` set | `201 Created` | `WAITING_OTHER_EVENT` | Spec |
| First event is final | New `traceId`, `finalEvent = true` | `201 Created` | `COMPLETED` | Spec |
| Expected event arrives before TTL | Existing trace in `WAITING_OTHER_EVENT`, `eventName` matches `nextExpectedEvent`, within TTL | `201 Created` | Determined by incoming event fields | Spec |
| Final event on in-progress trace | Existing trace, `finalEvent = true` | `201 Created` | `COMPLETED` | Decision |
| Duplicate `eventId` | Same `eventId` sent again | `409 Conflict` | Unchanged | Decision |
| Event for a `COMPLETED` trace | `traceId` already `COMPLETED` | `409 Conflict` | `COMPLETED` | Decision |
| Late event (TTL already expired) | Existing trace has TTL deadline in the past; event arrives after expiry | `409 Conflict` | `TTL_EXPIRED_FOR_EVENT` | Decision |
| Unexpected event while waiting | Existing trace `WAITING_OTHER_EVENT`, `eventName` does not match `nextExpectedEvent` | `409 Conflict` | `WAITING_OTHER_EVENT` | Decision |
| `result = ERROR` with `nextExpectedEvent` | Any trace, `result = ERROR`, `nextExpectedEvent` defined | `201 Created` | `WAITING_OTHER_EVENT` | Spec |
| Missing required field | `eventId`, `traceId`, `eventName`, `result`, or `occurredAt` absent | `400 Bad Request` | No change | Spec |
| Invalid `result` value | `result` is not `SUCCESS` or `ERROR` | `400 Bad Request` | No change | Spec |
| `nextExpectedEvent` without `nextEventTtlSeconds` | Only one of the pair provided | `400 Bad Request` | No change | Decision |
| `metadata` present | Optional JSON object included | `201 Created` | Stored alongside event | Decision |

---

### GET /traces/{traceId}/status Requirements Matrix

| Scenario | Query-Time Condition | Expected HTTP Response | Status Returned | Source |
|----------|---------------------|------------------------|-----------------|--------|
| Trace not found | `traceId` does not exist | `404 Not Found` | — | Decision |
| `STARTED` | First event received, no `nextExpectedEvent`, not final | `200 OK` | `STARTED` | Spec |
| `WAITING_OTHER_EVENT` | Last event set a `nextExpectedEvent`; TTL deadline has not passed at query time | `200 OK` | `WAITING_OTHER_EVENT` | Spec |
| `TTL_EXPIRED_FOR_EVENT` (lazy evaluation) | Last event set a `nextExpectedEvent`; TTL deadline has passed at query time; expected event never arrived | `200 OK` | `TTL_EXPIRED_FOR_EVENT` | Spec |
| `COMPLETED` | A `finalEvent = true` event was accepted and persisted | `200 OK` | `COMPLETED` | Spec |
| `COMPLETED` takes precedence over expired TTL | Final event was accepted while the trace was still in `WAITING_OTHER_EVENT`, before the TTL deadline passed | `200 OK` | `COMPLETED` | Decision |

---

### Open Requirements and Edge Cases

| # | Open Question / Edge Case | Recommended Decision | Justification | Test Coverage |
|---|--------------------------|---------------------|---------------|---------------|
| 1 | **Duplicate `eventId`**: The same `eventId` is submitted more than once. | Reject with `409 Conflict`. Do not reprocess or update trace state. | `eventId` is described as a unique event identifier. Silently ignoring duplicates could mask producer bugs; updating state on replay would corrupt the flow. | Unit + Hurl |
| 2 | **Unexpected `eventName`**: An event arrives for a trace in `WAITING_OTHER_EVENT` but its `eventName` does not match `nextExpectedEvent`. | Return `409 Conflict`. Keep the trace in `WAITING_OTHER_EVENT`. | The event is structurally valid but conflicts with the current expected state transition. `409` signals a state conflict, not a payload problem. | Unit + Hurl |
| 3 | **Expected event arrives after TTL has already expired**: The correct `eventName` arrives but the TTL deadline has passed. | Return `409 Conflict`. Trace remains `TTL_EXPIRED_FOR_EVENT`. | The event conflicts with the terminal expired state. Accepting it would retroactively fix a broken flow and undermine the reliability of TTL expiration as an alert signal. | Unit + Hurl |
| 4 | **TTL calculation: `occurredAt` vs. service receipt time**: The spec explicitly leaves this open. | Calculate TTL deadline as `occurredAt + nextEventTtlSeconds`. Store the absolute deadline (`ttl_expires_at`) at write time. | `occurredAt` reflects the actual business moment the step completed, independent of network or processing latency. Using receipt time would penalize slow producers and make the TTL semantics harder to reason about. | Unit |
| 5 | **Completed trace receiving more events**: An event arrives for a trace that is already `COMPLETED`. | Reject with `409 Conflict`. Completed traces are closed and immutable. | A completed flow has reached its terminal state. Accepting new events would reopen a finished flow, which has no defined meaning in the spec and risks corrupting a clean audit trail. | Unit + Hurl |
| 6 | **First event is also a final event**: The very first event for a `traceId` has `finalEvent = true`. | Create the trace and immediately set status to `COMPLETED`. | The spec states that a `finalEvent = true` event always marks the flow as completed. The first-event rule does not override this; both rules apply and completion wins. | Unit |
| 7 | **`result = ERROR` with `nextExpectedEvent` defined**: A step failed but the event still declares a next expected step and TTL. | Accept the event normally. Set trace to `WAITING_OTHER_EVENT`. | The spec explicitly states that `result` reflects the step outcome, not the overall flow status. A flow may have an error at one step yet still expect a follow-up (e.g., a retry notification or a compensating action). | Unit |
| 8 | **`result = ERROR` without `nextExpectedEvent` and not final**: A step failed, no next step is declared, and `finalEvent` is false. | Accept the event. Set or keep trace in `STARTED`. | The same reasoning applies: `result` does not drive state transitions. The trace simply has no continuation defined, which is a valid open state. | Unit |
| 9 | **Missing or invalid required fields**: `eventId`, `traceId`, `eventName`, `result`, or `occurredAt` is absent or malformed. | Reject with `400 Bad Request`. Return a descriptive validation error per field. | These fields are the minimum contract for the service to identify and process any event. Without them, no meaningful state can be derived. | Unit + Hurl |
| 10 | **`nextExpectedEvent` and `nextEventTtlSeconds` as an incomplete pair**: Only one of the two is present. | Reject with `400 Bad Request`. Both must be present together or both must be absent. | A next expected event without a deadline is unenforceable. A deadline without a named event cannot be matched. Treating them as a required pair prevents silent no-ops. | Unit |
| 11 | **`metadata` storage format**: The spec defines `metadata` as a flexible object with no fixed structure. | Store as a `JSONB` column in PostgreSQL. Return it as-is in query responses. | JSONB allows arbitrary structures without schema migrations, supports indexing if needed later, and is natively supported by the existing PostgreSQL setup. | None required for MVP |
| 12 | **Trace not found on status query**: `GET /traces/{traceId}/status` is called for a `traceId` that has never received an event. | Return `404 Not Found` with a descriptive message. | Returning a default status for an unknown trace would be misleading. A 404 clearly signals that no flow has been observed for that identifier. | Hurl |
| 13 | **Avoiding inconsistent trace state under concurrent writes**: Two events for the same `traceId` arrive simultaneously. | Use a unique constraint on `eventId` for deduplication and row-level locking (`SELECT FOR UPDATE`) on `trace_state` during updates. | For this MVP scale, database locking is sufficient. The spec explicitly excludes distributed locks. This keeps the implementation simple while preventing the most common race condition. | None required for MVP |
| 14 | **Expired traces receiving further events**: A trace in `TTL_EXPIRED_FOR_EVENT` receives a new event (not necessarily the originally expected one). | Return `409 Conflict`. Expired traces are terminal for this MVP. | The event conflicts with the terminal expired state — same reasoning as late events and events on completed traces. Keeping all terminal states consistent under `409` makes the error contract predictable. | Unit |
| 15 | **`STARTED` traces remaining open indefinitely**: A trace in `STARTED` has no TTL and no defined next step. It can stay open forever. | Accept this as valid for the MVP. No timeout is applied to `STARTED` traces. | The spec only defines TTL for the `WAITING_OTHER_EVENT` transition. Applying a global timeout on `STARTED` is an operational concern (e.g., stale trace cleanup) that is out of scope for this challenge. | None required for MVP |
