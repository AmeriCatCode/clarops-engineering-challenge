package com.clara.challenge.trace;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TraceStateRepository extends JpaRepository<TraceState, UUID> {

  Optional<TraceState> findByTraceId(String traceId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT ts FROM TraceState ts WHERE ts.traceId = :traceId")
  Optional<TraceState> findByTraceIdForUpdate(@Param("traceId") String traceId);
}
