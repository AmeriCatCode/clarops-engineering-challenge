package com.clara.challenge.event;

import com.clara.challenge.trace.TraceStatus;
import java.time.Instant;

public record TransitionResult(TraceStatus newStatus, Instant ttlExpiresAt) {}
