package psia.movieRecHub.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import psia.movieRecHub.exceptions.DuplicateReviewException;
import psia.movieRecHub.exceptions.MovieNotFoundException;
import psia.movieRecHub.exceptions.ReviewNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  // Catch a specific custom exception
  @ExceptionHandler(MovieNotFoundException.class)
  public ProblemDetail handleMovieNotFoundException(MovieNotFoundException ex) {
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    problemDetail.setDetail("Movie not found");
    problemDetail.setProperty("message", ex.getMessage());
    problemDetail.setProperty("errorCode", "MOVIE_ERR_001");

    return problemDetail;
  }

  @ExceptionHandler(ReviewNotFoundException.class)
  public ProblemDetail handleReviewNotFoundException(ReviewNotFoundException ex) {
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    problemDetail.setDetail("Review not found");
    problemDetail.setProperty("message", ex.getMessage());
    problemDetail.setProperty("errorCode", "REVIEW_ERR_001");

    return problemDetail;
  }

  @ExceptionHandler(DuplicateReviewException.class)
  public ProblemDetail handleDuplicateReviewException(DuplicateReviewException ex) {
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
    problemDetail.setDetail("Duplicate review");
    problemDetail.setProperty("message", ex.getMessage());
    problemDetail.setProperty("errorCode", "REVIEW_ERR_002");

    return problemDetail;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problemDetail.setDetail("Invalid request");
    problemDetail.setProperty("message", ex.getMessage());
    return problemDetail;
  }

  // Catch generic exceptions as a fallback
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleGenericException(Exception ex) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
  }
}
