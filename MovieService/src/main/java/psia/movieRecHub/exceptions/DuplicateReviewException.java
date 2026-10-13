package psia.movieRecHub.exceptions;

public class DuplicateReviewException extends RuntimeException {
  public DuplicateReviewException(Long userId, Long movieId) {
    super("User " + userId + " has already reviewed movie " + movieId);
  }
}
