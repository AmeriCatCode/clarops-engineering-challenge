package com.clara.challenge.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(schema = "clarops_challenge_schema", name = "trace_events")
public class TraceEvent {

  @Id private UUID id;

  @Column(name = "event_id", nullable = false, unique = true)
  private String eventId;

  @Column(name = "trace_id", nullable = false)
  private String traceId;

  @Column(name = "event_name", nullable = false)
  private String eventName;

  @Column(nullable = false)
  private String result;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Column(name = "next_expected_event")
  private String nextExpectedEvent;

  @Column(name = "next_event_ttl_seconds")
  private Integer nextEventTtlSeconds;

  @Column(name = "final_event", nullable = false)
  private boolean finalEvent;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> metadata;
}
