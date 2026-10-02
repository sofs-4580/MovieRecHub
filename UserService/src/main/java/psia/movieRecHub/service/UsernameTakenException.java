package psia.movieRecHub.service;

public class UsernameTakenException extends RuntimeException {

  public UsernameTakenException() {
    super("Username already in use");
  }
}
