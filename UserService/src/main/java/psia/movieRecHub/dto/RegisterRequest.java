package psia.movieRecHub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "[a-z0-9_]+") String username,
    @NotBlank @Size(min = 12, max = 128) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String password) {

  public RegisterRequest {
    if (username != null) {
      username = username.trim().toLowerCase(Locale.ROOT);
    }
  }

  @Override
  public String toString() {
    return "RegisterRequest[username=" + username + ", password=[REDACTED]]";
  }
}
