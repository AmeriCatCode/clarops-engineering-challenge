package com.clara.challenge.event.validation;

import com.clara.challenge.event.EventRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NextEventPairValidator
    implements ConstraintValidator<ValidNextEventPair, EventRequest> {

  @Override
  public boolean isValid(EventRequest request, ConstraintValidatorContext context) {
    if (request == null) {
      return true;
    }
    boolean hasEvent =
        request.getNextExpectedEvent() != null && !request.getNextExpectedEvent().isBlank();
    boolean hasTtl = request.getNextEventTtlSeconds() != null;
    return hasEvent == hasTtl;
  }
}
