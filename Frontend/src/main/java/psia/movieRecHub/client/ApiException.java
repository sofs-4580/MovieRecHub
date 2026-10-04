package psia.movieRecHub.client;

/**
 * UserService / ProfileService answered with an error (or did not answer). Message is user-facing.
 */
public class ApiException extends RuntimeException {

  private final int status;
  private final String code;

  public ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  public boolean isUnauthorized() {
    return status == 401;
  }
}
