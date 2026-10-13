package psia.movieRecHub.exceptions;

public class ListItemNotFoundException extends RuntimeException {
  public ListItemNotFoundException(Long listId, Long movieId) {
    super("Movie " + movieId + " is not in list " + listId);
  }
}
