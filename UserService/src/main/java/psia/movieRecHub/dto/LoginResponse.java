package psia.movieRecHub.dto;

public record LoginResponse(
    String accessToken, String tokenType, long expiresIn, Long userId, String username) {

  @Override
  public String toString() {
    return "LoginResponse[accessToken=[REDACTED], tokenType="
        + tokenType
        + ", expiresIn="
        + expiresIn
        + ", userId="
        + userId
        + ", username="
        + username
        + "]";
  }
}
