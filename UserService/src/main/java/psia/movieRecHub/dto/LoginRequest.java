package psia.movieRecHub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record LoginRequest(
    @NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "[a-z0-9_]+") String username,
    @NotBlank @Size(max = 128) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String password) {

  public LoginRequest {
    if (username != null) {
      username = username.trim().toLowerCase(Locale.ROOT);
    }
  }

  @Override
  public String toString() {
    return "LoginRequest[username=" + username + ", password=[REDACTED]]";
  }
}
