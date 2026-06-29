package com.clara.challenge.common.web;

import com.clara.challenge.common.exception.ConflictException;
import com.clara.challenge.common.exception.NotFoundException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ConflictException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleConflict(ConflictException ex) {
    return new ErrorResponse(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage());
  }

  @ExceptionHandler(NotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleNotFound(NotFoundException ex) {
    return new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
    String message =
        Stream.concat(
                ex.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage),
                ex.getBindingResult().getGlobalErrors().stream()
                    .map(ObjectError::getDefaultMessage))
            .collect(Collectors.joining("; "));
    return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Bad Request", message);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleUnreadable(HttpMessageNotReadableException ex) {
    return new ErrorResponse(
        HttpStatus.BAD_REQUEST.value(), "Bad Request", "Malformed or unreadable request body.");
  }
}
