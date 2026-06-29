package com.clara.challenge.event;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventRequestValidationTest {

  private Validator validator;

  @BeforeEach
  void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  private EventRequest validRequest() {
    EventRequest req = new EventRequest();
    req.setEventId("evt-001");
    req.setTraceId("trace-abc");
    req.setEventName("payment-initiated");
    req.setResult("SUCCESS");
    req.setOccurredAt(Instant.now());
    return req;
  }

  @Test
  // Validates spec: eventId is a required field → 400 when absent
  void shouldFailValidation_WhenEventIdIsMissing() {
    EventRequest req = validRequest();
    req.setEventId(null);

    Set<ConstraintViolation<EventRequest>> violations = validator.validate(req);

    assertThat(violations).anyMatch(v -> v.getMessage().contains("eventId is required"));
  }

  @Test
  // Validates spec: result must be SUCCESS or ERROR → 400 for any other value
  void shouldFailValidation_WhenResultIsInvalid() {
    EventRequest req = validRequest();
    req.setResult("PENDING");

    Set<ConstraintViolation<EventRequest>> violations = validator.validate(req);

    assertThat(violations).anyMatch(v -> v.getMessage().contains("result must be SUCCESS or ERROR"));
  }

  @Test
  // Validates decision (Open Req #10): nextExpectedEvent without nextEventTtlSeconds → 400
  void shouldFailValidation_WhenNextExpectedEventPresentButTtlSecondsMissing() {
    EventRequest req = validRequest();
    req.setNextExpectedEvent("payment-confirmed");
    req.setNextEventTtlSeconds(null);

    Set<ConstraintViolation<EventRequest>> violations = validator.validate(req);

    assertThat(violations)
        .anyMatch(
            v ->
                v.getMessage()
                    .contains("nextExpectedEvent and nextEventTtlSeconds must both be present"));
  }

  @Test
  // Validates decision (Open Req #10): nextEventTtlSeconds without nextExpectedEvent → 400
  void shouldFailValidation_WhenNextEventTtlSecondsPresentButExpectedEventMissing() {
    EventRequest req = validRequest();
    req.setNextExpectedEvent(null);
    req.setNextEventTtlSeconds(300);

    Set<ConstraintViolation<EventRequest>> violations = validator.validate(req);

    assertThat(violations)
        .anyMatch(
            v ->
                v.getMessage()
                    .contains("nextExpectedEvent and nextEventTtlSeconds must both be present"));
  }
}
