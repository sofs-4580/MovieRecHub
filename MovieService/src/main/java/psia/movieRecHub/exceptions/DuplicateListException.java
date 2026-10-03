package psia.movieRecHub.exceptions;

public class DuplicateListException extends RuntimeException {
  public DuplicateListException(String message) {
    super(message);
  }

  public static DuplicateListException name(Long ownerId, String name) {
    return new DuplicateListException(
        "User " + ownerId + " already has a list named '" + name + "'");
  }

  public static DuplicateListException movie(Long listId, Long movieId) {
    return new DuplicateListException("Movie " + movieId + " is already in list " + listId);
  }
}
