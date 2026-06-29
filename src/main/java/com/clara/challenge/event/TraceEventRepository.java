package com.clara.challenge.event;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TraceEventRepository extends JpaRepository<TraceEvent, UUID> {

  boolean existsByEventId(String eventId);
}
