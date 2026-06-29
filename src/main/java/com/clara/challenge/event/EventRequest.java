package com.clara.challenge.event;

import com.clara.challenge.event.validation.ValidNextEventPair;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ValidNextEventPair
public class EventRequest {

  @NotBlank(message = "eventId is required")
  private String eventId;

  @NotBlank(message = "traceId is required")
  private String traceId;

  @NotBlank(message = "eventName is required")
  private String eventName;

  @NotBlank(message = "result is required")
  @Pattern(regexp = "SUCCESS|ERROR", message = "result must be SUCCESS or ERROR")
  private String result;

  @NotNull(message = "occurredAt is required")
  private Instant occurredAt;

  private String nextExpectedEvent;
  private Integer nextEventTtlSeconds;
  private boolean finalEvent = false;
  private Map<String, Object> metadata;
}
