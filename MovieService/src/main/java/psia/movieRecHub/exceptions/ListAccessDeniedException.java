package psia.movieRecHub.exceptions;

public class ListAccessDeniedException extends RuntimeException {
  public ListAccessDeniedException(Long listId) {
    super("You are not allowed to modify list " + listId);
  }
}
