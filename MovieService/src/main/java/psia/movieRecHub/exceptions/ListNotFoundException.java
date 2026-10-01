package psia.movieRecHub.exceptions;

public class ListNotFoundException extends RuntimeException {
  public ListNotFoundException(Long id) {
    super("List not found with id: " + id);
  }
}
