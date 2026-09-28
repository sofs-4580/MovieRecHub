package psia.movieRecHub.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import psia.movieRecHub.dto.ApiErrorResponse;

@RestControllerAdvice(assignableTypes = InternalProfileController.class)
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiErrorResponse> invalidUserId() {
    return ResponseEntity.badRequest()
        .body(new ApiErrorResponse("INVALID_USER_ID", "User ID must be a positive 64-bit integer"));
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ApiErrorResponse> invalidMethodArgument(
      HandlerMethodValidationException exception) {
    boolean invalidUserId = exception.getParameterValidationResults().stream()
        .anyMatch(result -> result.getMethodParameter().getParameterIndex() == 0);
    return invalidUserId ? invalidUserId() : invalidInitialPreferences();
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  public ResponseEntity<ApiErrorResponse> invalidInitialPreferences() {
    return ResponseEntity.badRequest()
        .body(new ApiErrorResponse("INVALID_INITIAL_PROFILE", "Invalid initial profile preferences"));
  }
}
