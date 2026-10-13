package psia.movieRecHub.exceptions;

public class LlmUnavailableException extends RuntimeException {
  public LlmUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

  public LlmUnavailableException(String message) {
    super(message);
  }
}
