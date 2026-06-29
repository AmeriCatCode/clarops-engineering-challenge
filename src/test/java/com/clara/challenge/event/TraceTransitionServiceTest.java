package com.clara.challenge.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clara.challenge.common.exception.ConflictException;
import com.clara.challenge.trace.TraceState;
import com.clara.challenge.trace.TraceStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TraceTransitionServiceTest {

  private TraceTransitionService service;

  @BeforeEach
  void setUp() {
    service = new TraceTransitionService();
  }

  // ---- helpers ----

  private EventRequest request(
      String eventName,
      String result,
      boolean finalEvent,
      String nextExpectedEvent,
      Integer nextEventTtlSeconds,
      Instant occurredAt) {
    EventRequest req = new EventRequest();
    req.setEventId(UUID.randomUUID().toString());
    req.setTraceId("trace-test");
    req.setEventName(eventName);
    req.setResult(result);
    req.setFinalEvent(finalEvent);
    req.setNextExpectedEvent(nextExpectedEvent);
    req.setNextEventTtlSeconds(nextEventTtlSeconds);
    req.setOccurredAt(occurredAt != null ? occurredAt : Instant.now());
    return req;
  }

  private TraceState stateWith(
      TraceStatus status, String nextExpectedEvent, Instant ttlExpiresAt) {
    TraceState state = new TraceState();
    state.setId(UUID.randomUUID());
    state.setTraceId("trace-test");
    state.setStatus(status);
    state.setNextExpectedEvent(nextExpectedEvent);
    state.setTtlExpiresAt(ttlExpiresAt);
    state.setLastEventName("previous-event");
    state.setLastEventResult("SUCCESS");
    state.setEventsReceived(1);
    state.setCreatedAt(Instant.now());
    state.setUpdatedAt(Instant.now());
    return state;
  }

  // ---- state transitions — spec scenarios ----

  @Test
  // Validates spec: first event with no continuation → STARTED
  void shouldReturnStarted_WhenFirstEventHasNoNextExpectedEventAndIsNotFinal() {
    EventRequest req = request("payment-initiated", "SUCCESS", false, null, null, null);

    TransitionResult result = service.apply(null, req);

    assertThat(result.newStatus()).isEqualTo(TraceStatus.STARTED);
    assertThat(result.ttlExpiresAt()).isNull();
  }

  @Test
  // Validates spec: first event with nextExpectedEvent + TTL → WAITING_OTHER_EVENT
  void shouldReturnWaitingOtherEvent_WhenFirstEventDefinesNextExpectedEvent() {
    Instant occurredAt = Instant.now();
    EventRequest req =
        request("payment-initiated", "SUCCESS", false, "payment-confirmed", 300, occurredAt);

    TransitionResult result = service.apply(null, req);

    assertThat(result.newStatus()).isEqualTo(TraceStatus.WAITING_OTHER_EVENT);
    assertThat(result.ttlExpiresAt()).isEqualTo(occurredAt.plusSeconds(300));
  }

  @Test
  // Validates decision (Open Req #6): first event is also final → COMPLETED immediately
  void shouldReturnCompleted_WhenFirstEventIsFinal() {
    EventRequest req = request("payment-confirmed", "SUCCESS", true, null, null, null);

    TransitionResult result = service.apply(null, req);

    assertThat(result.newStatus()).isEqualTo(TraceStatus.COMPLETED);
    assertThat(result.ttlExpiresAt()).isNull();
  }

  @Test
  // Validates spec: expected event arrives within TTL and defines a next step
  void shouldReturnWaitingOtherEvent_WhenActiveTraceReceivesMatchingEventWithNextExpected() {
    TraceState current =
        stateWith(
            TraceStatus.WAITING_OTHER_EVENT, "payment-confirmed", Instant.now().plusSeconds(300));
    Instant occurredAt = Instant.now();
    EventRequest req =
        request("payment-confirmed", "SUCCESS", false, "payment-settled", 60, occurredAt);

    TransitionResult result = service.apply(current, req);

    assertThat(result.newStatus()).isEqualTo(TraceStatus.WAITING_OTHER_EVENT);
    assertThat(result.ttlExpiresAt()).isEqualTo(occurredAt.plusSeconds(60));
  }

  @Test
  // Validates spec: final event on active trace → COMPLETED
  void shouldReturnCompleted_WhenActiveTraceReceivesFinalEvent() {
    TraceState current =
        stateWith(
            TraceStatus.WAITING_OTHER_EVENT, "payment-confirmed", Instant.now().plusSeconds(300));
    EventRequest req = request("payment-confirmed", "SUCCESS", true, null, null, null);

    TransitionResult result = service.apply(current, req);

    assertThat(result.newStatus()).isEqualTo(TraceStatus.COMPLETED);
  }

  @Test
  // Validates spec: expected event arrives within TTL with no further continuation → STARTED
  void shouldReturnStarted_WhenActiveWaitingTraceReceivesMatchingEventWithNoNextExpected() {
    TraceState current =
        stateWith(
            TraceStatus.WAITING_OTHER_EVENT, "payment-confirmed", Instant.now().plusSeconds(300));
    EventRequest req = request("payment-confirmed", "SUCCESS", false, null, null, null);

    TransitionResult result = service.apply(current, req);

    assertThat(result.newStatus()).isEqualTo(TraceStatus.STARTED);
    assertThat(result.ttlExpiresAt()).isNull();
  }

  // ---- conflict detection — decisions ----

  @Test
  // Validates decision (Open Req #5): COMPLETED trace rejects any further event
  void shouldThrowConflict_WhenCompletedTraceReceivesAnyEvent() {
    TraceState current = stateWith(TraceStatus.COMPLETED, null, null);
    EventRequest req = request("any-event", "SUCCESS", false, null, null, null);

    assertThatThrownBy(() -> service.apply(current, req)).isInstanceOf(ConflictException.class);
  }

  @Test
  // Validates decision (Open Req #14): TTL_EXPIRED_FOR_EVENT trace rejects any further event
  void shouldThrowConflict_WhenExpiredTraceReceivesAnyEvent() {
    TraceState current = stateWith(TraceStatus.TTL_EXPIRED_FOR_EVENT, null, null);
    EventRequest req = request("any-event", "SUCCESS", false, null, null, null);

    assertThatThrownBy(() -> service.apply(current, req)).isInstanceOf(ConflictException.class);
  }

  @Test
  // Validates decision (Open Req #2): unexpected eventName while WAITING_OTHER_EVENT → conflict
  void shouldThrowConflict_WhenUnexpectedEventArrivesWhileWaiting() {
    TraceState current =
        stateWith(
            TraceStatus.WAITING_OTHER_EVENT, "payment-confirmed", Instant.now().plusSeconds(300));
    EventRequest req = request("payment-cancelled", "SUCCESS", false, null, null, null);

    assertThatThrownBy(() -> service.apply(current, req)).isInstanceOf(ConflictException.class);
  }

  @Test
  // Validates decision (Open Req #3): correct eventName arrives after TTL expired → conflict
  void shouldThrowConflict_WhenLateEventArrivesAfterTtlExpired() {
    TraceState current =
        stateWith(
            TraceStatus.WAITING_OTHER_EVENT, "payment-confirmed", Instant.now().minusSeconds(10));
    EventRequest req = request("payment-confirmed", "SUCCESS", false, null, null, null);

    assertThatThrownBy(() -> service.apply(current, req)).isInstanceOf(ConflictException.class);
  }

  // ---- TTL calculation — decision ----

  @Test
  // Validates decision (Open Req #4): TTL deadline = occurredAt + nextEventTtlSeconds
  void shouldSetTtlDeadlineToOccurredAtPlusTtlSeconds_WhenNextExpectedEventDefined() {
    Instant occurredAt = Instant.parse("2026-01-01T10:00:00Z");
    EventRequest req =
        request("payment-initiated", "SUCCESS", false, "payment-confirmed", 120, occurredAt);

    TransitionResult result = service.apply(null, req);

    assertThat(result.ttlExpiresAt()).isEqualTo(Instant.parse("2026-01-01T10:02:00Z"));
  }

  // ---- ERROR result — spec ----

  @Test
  // Validates spec: result=ERROR does not prevent WAITING_OTHER_EVENT transition
  void shouldReturnWaitingOtherEvent_WhenResultIsErrorAndNextExpectedEventDefined() {
    Instant occurredAt = Instant.now();
    EventRequest req =
        request("payment-initiated", "ERROR", false, "payment-confirmed", 300, occurredAt);

    TransitionResult result = service.apply(null, req);

    assertThat(result.newStatus()).isEqualTo(TraceStatus.WAITING_OTHER_EVENT);
  }

  @Test
  // Validates decision (Open Req #8): result=ERROR without nextExpected → STARTED, not a failure
  void shouldReturnStarted_WhenResultIsErrorAndNoNextExpectedEventAndNotFinal() {
    EventRequest req = request("payment-initiated", "ERROR", false, null, null, null);

    TransitionResult result = service.apply(null, req);

    assertThat(result.newStatus()).isEqualTo(TraceStatus.STARTED);
  }
}
