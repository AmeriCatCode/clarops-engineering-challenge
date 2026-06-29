package com.clara.challenge.common.web;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ErrorResponse {

  private final int status;
  private final String error;
  private final String message;
}
