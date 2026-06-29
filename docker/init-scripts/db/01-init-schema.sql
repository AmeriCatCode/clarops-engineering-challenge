-- ============================================================
-- Clarops Challenge — Initial Schema
-- Distributed event tracking, TTL expiration, and operational
-- flow analysis.
-- Idempotent — safe to re-execute.
-- UUIDs must be provided by the application layer.
-- ============================================================
CREATE
  SCHEMA IF NOT EXISTS clarops_challenge_schema;
SET
search_path TO clarops_challenge_schema;

-- -------------------------
-- health
-- Single-row table used by the health endpoint.
-- -------------------------
CREATE
  TABLE
    IF NOT EXISTS health(
      id BIGSERIAL PRIMARY KEY,
      message VARCHAR(255) NOT NULL
    );

INSERT
  INTO
    health(message) SELECT
      'clarops sr engineer challenge'
    WHERE
      NOT EXISTS(
        SELECT
          1
        FROM
          health
      );

-- -------------------------
-- trace_events
-- Full history of every received and accepted event.
-- -------------------------
CREATE
  TABLE
    IF NOT EXISTS trace_events(
      id UUID PRIMARY KEY,
      event_id VARCHAR(255) NOT NULL,
      trace_id VARCHAR(255) NOT NULL,
      event_name VARCHAR(255) NOT NULL,
      result VARCHAR(50) NOT NULL,
      occurred_at TIMESTAMPTZ NOT NULL,
      received_at TIMESTAMPTZ NOT NULL,
      next_expected_event VARCHAR(255),
      next_event_ttl_seconds INTEGER,
      final_event BOOLEAN NOT NULL DEFAULT FALSE,
      metadata JSONB,
      CONSTRAINT uq_trace_events_event_id UNIQUE(event_id)
    );

CREATE
  INDEX IF NOT EXISTS idx_trace_events_trace_id ON
  trace_events(trace_id);

-- -------------------------
-- trace_state
-- One row per traceId. Holds the current status and TTL deadline.
-- -------------------------
CREATE
  TABLE
    IF NOT EXISTS trace_state(
      id UUID PRIMARY KEY,
      trace_id VARCHAR(255) NOT NULL,
      status VARCHAR(50) NOT NULL,
      last_event_id UUID NOT NULL REFERENCES trace_events(id),
      last_event_name VARCHAR(255) NOT NULL,
      last_event_result VARCHAR(50) NOT NULL,
      next_expected_event VARCHAR(255),
      ttl_expires_at TIMESTAMPTZ,
      events_received INTEGER NOT NULL DEFAULT 1,
      created_at TIMESTAMPTZ NOT NULL,
      updated_at TIMESTAMPTZ NOT NULL,
      CONSTRAINT uq_trace_state_trace_id UNIQUE(trace_id)
    );

CREATE
  INDEX IF NOT EXISTS idx_trace_state_trace_id ON
  trace_state(trace_id);
