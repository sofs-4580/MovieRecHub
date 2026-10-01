package psia.movieRecHub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RecommendationRequest(
    @NotNull @Positive Long userId, @NotBlank @Size(max = 1000) String prompt) {}
