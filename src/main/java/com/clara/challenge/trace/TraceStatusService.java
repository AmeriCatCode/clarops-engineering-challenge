package com.clara.challenge.trace;

import com.clara.challenge.common.exception.NotFoundException;
import com.clara.challenge.common.logging.ServiceLogger;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TraceStatusService {

  private static final Logger log = LoggerFactory.getLogger(TraceStatusService.class);

  private final TraceStateRepository stateRepository;

  public TraceStatusResponse getStatus(String traceId) {
    long start = System.currentTimeMillis();
    ServiceLogger.logStart(log, traceId, "getStatus");
    try {
      TraceState state =
          stateRepository
              .findByTraceId(traceId)
              .orElseThrow(() -> new NotFoundException("Trace '" + traceId + "' not found."));

      TraceStatus effectiveStatus = resolveStatus(state);

      TraceStatusResponse response =
          new TraceStatusResponse(
              state.getTraceId(),
              effectiveStatus,
              state.getLastEventName(),
              state.getLastEventResult(),
              state.getNextExpectedEvent(),
              state.getTtlExpiresAt(),
              state.getEventsReceived(),
              state.getCreatedAt(),
              state.getUpdatedAt());

      ServiceLogger.logSuccess(log, traceId, "getStatus", start);
      return response;
    } catch (NotFoundException e) {
      ServiceLogger.logError(log, traceId, "getStatus", start, e);
      throw e;
    }
  }

  private TraceStatus resolveStatus(TraceState state) {
    if (state.getStatus() == TraceStatus.WAITING_OTHER_EVENT
        && state.getTtlExpiresAt() != null
        && Instant.now().isAfter(state.getTtlExpiresAt())) {
      return TraceStatus.TTL_EXPIRED_FOR_EVENT;
    }
    return state.getStatus();
  }
}
