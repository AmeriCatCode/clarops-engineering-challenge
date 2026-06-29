package com.clara.challenge.trace;

import com.clara.challenge.event.TraceEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(schema = "clarops_challenge_schema", name = "trace_state")
public class TraceState {

  @Id private UUID id;

  @Column(name = "trace_id", nullable = false, unique = true)
  private String traceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TraceStatus status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "last_event_id", nullable = false)
  private TraceEvent lastEvent;

  @Column(name = "last_event_name", nullable = false)
  private String lastEventName;

  @Column(name = "last_event_result", nullable = false)
  private String lastEventResult;

  @Column(name = "next_expected_event")
  private String nextExpectedEvent;

  @Column(name = "ttl_expires_at")
  private Instant ttlExpiresAt;

  @Column(name = "events_received", nullable = false)
  private int eventsReceived;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
