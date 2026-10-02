package psia.movieRecHub.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import psia.movieRecHub.dto.ApiErrorResponse;
import psia.movieRecHub.service.InvalidPublicProfileQueryException;
import psia.movieRecHub.service.ProfileAuthenticationRequiredException;
import psia.movieRecHub.service.PublicProfileNotFoundException;
import psia.movieRecHub.service.SessionVerificationUnavailableException;

@RestControllerAdvice(assignableTypes = PublicProfileController.class)
public class PublicProfileExceptionHandler {

  @ExceptionHandler(ProfileAuthenticationRequiredException.class)
  public ResponseEntity<ApiErrorResponse> loginRequired() {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ApiErrorResponse("UNAUTHORIZED", "Login required"));
  }

  @ExceptionHandler(SessionVerificationUnavailableException.class)
  public ResponseEntity<ApiErrorResponse> authUnavailable() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ApiErrorResponse("AUTH_SERVICE_UNAVAILABLE", "Authentication is temporarily unavailable"));
  }

  @ExceptionHandler(PublicProfileNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> notFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ApiErrorResponse("PROFILE_NOT_FOUND", "Profile not found"));
  }

  @ExceptionHandler({InvalidPublicProfileQueryException.class, MethodArgumentTypeMismatchException.class})
  public ResponseEntity<ApiErrorResponse> invalidQuery() {
    return ResponseEntity.badRequest()
        .body(new ApiErrorResponse("INVALID_PROFILE_QUERY", "Invalid profile query"));
  }
}
