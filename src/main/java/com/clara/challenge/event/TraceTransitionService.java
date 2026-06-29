package com.clara.challenge.event;

import com.clara.challenge.common.exception.ConflictException;
import com.clara.challenge.trace.TraceState;
import com.clara.challenge.trace.TraceStatus;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Encapsulates all trace state transition rules. Has no persistence or HTTP dependencies so it can
 * be unit-tested in isolation.
 */
@Service
public class TraceTransitionService {

  public TransitionResult apply(TraceState current, EventRequest incoming) {
    if (current == null) {
      return applyFirstEvent(incoming);
    }
    return applySubsequentEvent(current, incoming);
  }

  private TransitionResult applyFirstEvent(EventRequest incoming) {
    if (incoming.isFinalEvent()) {
      return new TransitionResult(TraceStatus.COMPLETED, null);
    }
    if (incoming.getNextExpectedEvent() != null) {
      return new TransitionResult(
          TraceStatus.WAITING_OTHER_EVENT, ttlDeadline(incoming));
    }
    return new TransitionResult(TraceStatus.STARTED, null);
  }

  private TransitionResult applySubsequentEvent(TraceState current, EventRequest incoming) {
    TraceStatus currentStatus = current.getStatus();

    if (currentStatus == TraceStatus.COMPLETED
        || currentStatus == TraceStatus.TTL_EXPIRED_FOR_EVENT) {
      throw new ConflictException(
          "Trace '"
              + current.getTraceId()
              + "' is in terminal state "
              + currentStatus
              + " and cannot accept new events.");
    }

    if (currentStatus == TraceStatus.WAITING_OTHER_EVENT) {
      if (current.getTtlExpiresAt() != null && Instant.now().isAfter(current.getTtlExpiresAt())) {
        throw new ConflictException(
            "Event arrived after TTL expired for trace '" + current.getTraceId() + "'.");
      }
      if (!incoming.getEventName().equals(current.getNextExpectedEvent())) {
        throw new ConflictException(
            "Unexpected event '"
                + incoming.getEventName()
                + "' for trace '"
                + current.getTraceId()
                + "'. Expected: '"
                + current.getNextExpectedEvent()
                + "'.");
      }
    }

    if (incoming.isFinalEvent()) {
      return new TransitionResult(TraceStatus.COMPLETED, null);
    }
    if (incoming.getNextExpectedEvent() != null) {
      return new TransitionResult(TraceStatus.WAITING_OTHER_EVENT, ttlDeadline(incoming));
    }
    return new TransitionResult(TraceStatus.STARTED, null);
  }

  private Instant ttlDeadline(EventRequest incoming) {
    return incoming.getOccurredAt().plusSeconds(incoming.getNextEventTtlSeconds());
  }
}
