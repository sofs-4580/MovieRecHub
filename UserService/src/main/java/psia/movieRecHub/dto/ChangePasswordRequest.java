package psia.movieRecHub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank @Size(max = 128) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String currentPassword,
    @NotBlank @Size(min = 12, max = 128) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String newPassword) {

  @Override
  public String toString() {
    return "ChangePasswordRequest[currentPassword=[REDACTED], newPassword=[REDACTED]]";
  }
}
