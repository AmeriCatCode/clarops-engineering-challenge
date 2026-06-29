package com.clara.challenge.event;

import com.clara.challenge.common.exception.ConflictException;
import com.clara.challenge.common.logging.ServiceLogger;
import com.clara.challenge.trace.TraceState;
import com.clara.challenge.trace.TraceStateRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class EventService {

  private static final Logger log = LoggerFactory.getLogger(EventService.class);

  private final TraceEventRepository eventRepository;
  private final TraceStateRepository stateRepository;
  private final TraceTransitionService transitionService;

  public void receiveEvent(EventRequest request) {
    String traceId = request.getTraceId();
    long start = System.currentTimeMillis();
    ServiceLogger.logStart(log, traceId, "receiveEvent");
    try {
      if (eventRepository.existsByEventId(request.getEventId())) {
        throw new ConflictException(
            "Event '" + request.getEventId() + "' has already been received.");
      }

      Optional<TraceState> existing = stateRepository.findByTraceIdForUpdate(traceId);

      TransitionResult transition = transitionService.apply(existing.orElse(null), request);

      Instant now = Instant.now();
      TraceEvent event = eventRepository.save(buildEvent(request, now));

      TraceState state =
          existing.orElseGet(
              () -> {
                TraceState s = new TraceState();
                s.setId(UUID.randomUUID());
                s.setTraceId(traceId);
                s.setCreatedAt(now);
                s.setEventsReceived(0);
                return s;
              });

      state.setStatus(transition.newStatus());
      state.setLastEvent(event);
      state.setLastEventName(event.getEventName());
      state.setLastEventResult(event.getResult());
      state.setNextExpectedEvent(request.getNextExpectedEvent());
      state.setTtlExpiresAt(transition.ttlExpiresAt());
      state.setEventsReceived(state.getEventsReceived() + 1);
      state.setUpdatedAt(now);

      stateRepository.save(state);
      ServiceLogger.logSuccess(log, traceId, "receiveEvent", start);
    } catch (ConflictException e) {
      ServiceLogger.logError(log, traceId, "receiveEvent", start, e);
      throw e;
    }
  }

  private TraceEvent buildEvent(EventRequest request, Instant receivedAt) {
    TraceEvent event = new TraceEvent();
    event.setId(UUID.randomUUID());
    event.setEventId(request.getEventId());
    event.setTraceId(request.getTraceId());
    event.setEventName(request.getEventName());
    event.setResult(request.getResult());
    event.setOccurredAt(request.getOccurredAt());
    event.setReceivedAt(receivedAt);
    event.setNextExpectedEvent(request.getNextExpectedEvent());
    event.setNextEventTtlSeconds(request.getNextEventTtlSeconds());
    event.setFinalEvent(request.isFinalEvent());
    event.setMetadata(request.getMetadata());
    return event;
  }
}
