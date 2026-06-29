package com.clara.challenge.common.logging;

import org.slf4j.Logger;

public final class ServiceLogger {

  private ServiceLogger() {}

  public static void logStart(Logger log, String traceId, String operation) {
    log.info("[traceId={}] Starting {}", traceId, operation);
  }

  public static void logSuccess(Logger log, String traceId, String operation, long startedAt) {
    log.info(
        "[traceId={}] Completed {} in {}ms", traceId, operation, elapsed(startedAt));
  }

  public static void logError(
      Logger log, String traceId, String operation, long startedAt, Throwable ex) {
    log.error(
        "[traceId={}] Failed {} in {}ms: {}: {}",
        traceId,
        operation,
        elapsed(startedAt),
        ex.getClass().getSimpleName(),
        ex.getMessage());
  }

  private static long elapsed(long startedAt) {
    return System.currentTimeMillis() - startedAt;
  }
}
