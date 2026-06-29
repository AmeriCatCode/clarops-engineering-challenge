package com.clara.challenge.trace;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/traces")
@RequiredArgsConstructor
public class TraceStatusController {

  private final TraceStatusService traceStatusService;

  @GetMapping("/{traceId}/status")
  public TraceStatusResponse getStatus(@PathVariable String traceId) {
    return traceStatusService.getStatus(traceId);
  }
}
