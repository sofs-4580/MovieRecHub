package psia.movieRecHub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "[a-z0-9_]+") String username,
    @NotBlank @Size(min = 12, max = 128) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String password,
    @NotEmpty @Size(max = 10)
        Set<@NotBlank @Pattern(regexp = "ACTION|COMEDY|DRAMA|HORROR|ROMANCE|SCI_FI|THRILLER|ANIMATION|DOCUMENTARY|FANTASY") String> favoriteGenres) {

  public RegisterRequest {
    if (username != null) {
      username = username.trim().toLowerCase(Locale.ROOT);
    }
    if (favoriteGenres != null) {
      favoriteGenres = Collections.unmodifiableSet(new LinkedHashSet<>(favoriteGenres));
    }
  }

  @Override
  public String toString() {
    return "RegisterRequest[username=" + username + ", password=[REDACTED]]";
  }
}
