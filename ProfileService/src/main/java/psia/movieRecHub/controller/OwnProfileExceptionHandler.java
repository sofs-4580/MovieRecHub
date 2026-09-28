package psia.movieRecHub.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import psia.movieRecHub.dto.ApiErrorResponse;
import psia.movieRecHub.service.InvalidProfileUpdateException;
import psia.movieRecHub.service.ProfileAuthenticationRequiredException;
import psia.movieRecHub.service.ProfileNotReadyException;
import psia.movieRecHub.service.SessionVerificationUnavailableException;

@RestControllerAdvice(assignableTypes = OwnProfileController.class)
public class OwnProfileExceptionHandler {

  @ExceptionHandler(ProfileAuthenticationRequiredException.class)
  public ResponseEntity<ApiErrorResponse> loginRequired() {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ApiErrorResponse("UNAUTHORIZED", "Login required"));
  }

  @ExceptionHandler(ProfileNotReadyException.class)
  public ResponseEntity<ApiErrorResponse> profileNotReady() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ApiErrorResponse("PROFILE_NOT_READY", "Profile is not available yet"));
  }

  @ExceptionHandler(SessionVerificationUnavailableException.class)
  public ResponseEntity<ApiErrorResponse> userServiceUnavailable() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ApiErrorResponse("AUTH_SERVICE_UNAVAILABLE", "Authentication is temporarily unavailable"));
  }

  @ExceptionHandler({InvalidProfileUpdateException.class, HttpMessageNotReadableException.class})
  public ResponseEntity<ApiErrorResponse> invalidUpdate() {
    return ResponseEntity.badRequest()
        .body(new ApiErrorResponse("INVALID_PROFILE_UPDATE", "Invalid profile update"));
  }
}
