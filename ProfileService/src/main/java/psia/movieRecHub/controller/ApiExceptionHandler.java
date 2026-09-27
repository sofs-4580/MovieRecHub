package psia.movieRecHub.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import psia.movieRecHub.dto.ApiErrorResponse;

@RestControllerAdvice(assignableTypes = InternalProfileController.class)
public class ApiExceptionHandler {

  @ExceptionHandler({
    HandlerMethodValidationException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ApiErrorResponse> invalidUserId() {
    return ResponseEntity.badRequest()
        .body(new ApiErrorResponse("INVALID_USER_ID", "User ID must be a positive 64-bit integer"));
  }
}
