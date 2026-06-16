package com.clara.challenge.health;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HealthService {

  private final HealthRepository healthRepository;

  public String getMessage() {
    return healthRepository.findAll().stream()
        .findFirst()
        .map(Health::getMessage)
        .orElse("unavailable");
  }
}
