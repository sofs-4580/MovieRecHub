package psia.movieRecHub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import psia.movieRecHub.entities.ListVisibility;

public record ListRequestDto(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description,
    @NotNull ListVisibility visibility) {}
