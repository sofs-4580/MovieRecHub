package psia.movieRecHub.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import psia.movieRecHub.dto.ApiErrorResponse;
import psia.movieRecHub.service.InvalidCredentialsException;
import psia.movieRecHub.service.CurrentPasswordMismatchException;
import psia.movieRecHub.service.UsernameTakenException;
import psia.movieRecHub.service.SessionRequiredException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> invalidFields() {
    return ResponseEntity.badRequest()
        .body(new ApiErrorResponse("VALIDATION_ERROR", "Invalid authentication request"));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> invalidJson() {
    return ResponseEntity.badRequest()
        .body(new ApiErrorResponse("INVALID_REQUEST", "Invalid JSON request"));
  }

  @ExceptionHandler(UsernameTakenException.class)
  public ResponseEntity<ApiErrorResponse> usernameTaken() {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiErrorResponse("USERNAME_TAKEN", "Username already in use"));
  }
  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ApiErrorResponse> invalidCredentials() {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ApiErrorResponse("INVALID_CREDENTIALS", "Invalid credentials"));
  }
  @ExceptionHandler(CurrentPasswordMismatchException.class)
  public ResponseEntity<ApiErrorResponse> currentPasswordMismatch() {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ApiErrorResponse("INVALID_CURRENT_PASSWORD", "Current password is incorrect"));
  }

  @ExceptionHandler(SessionRequiredException.class)
  public ResponseEntity<ApiErrorResponse> sessionRequired() {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ApiErrorResponse("UNAUTHORIZED", "Login required"));
  }
}
