package psia.movieRecHub.service;

public class CurrentPasswordMismatchException extends RuntimeException {
  public CurrentPasswordMismatchException() {
    super("Current password is incorrect");
  }
}
