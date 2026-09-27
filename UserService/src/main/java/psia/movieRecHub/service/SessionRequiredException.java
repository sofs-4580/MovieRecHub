package psia.movieRecHub.service;

public class SessionRequiredException extends RuntimeException {
  public SessionRequiredException() {
    super("Login required");
  }
}
