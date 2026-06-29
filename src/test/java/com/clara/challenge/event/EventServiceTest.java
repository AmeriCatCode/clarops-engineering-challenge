package com.clara.challenge.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.clara.challenge.common.exception.ConflictException;
import com.clara.challenge.trace.TraceStateRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

  @Mock private TraceEventRepository eventRepository;
  @Mock private TraceStateRepository stateRepository;
  @Mock private TraceTransitionService transitionService;

  @InjectMocks private EventService eventService;

  @Test
  // Validates decision (Open Req #1): duplicate eventId is rejected before any state check
  void shouldThrowConflict_WhenDuplicateEventIdReceived() {
    EventRequest request = new EventRequest();
    request.setEventId("evt-duplicate");
    request.setTraceId("trace-abc");
    request.setEventName("payment-initiated");
    request.setResult("SUCCESS");
    request.setOccurredAt(Instant.now());

    when(eventRepository.existsByEventId("evt-duplicate")).thenReturn(true);

    assertThatThrownBy(() -> eventService.receiveEvent(request))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("evt-duplicate");
  }
}
