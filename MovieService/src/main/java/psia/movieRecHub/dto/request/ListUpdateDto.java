package psia.movieRecHub.dto.request;

import jakarta.validation.constraints.Size;
import psia.movieRecHub.entities.ListVisibility;

/** PATCH: todos os campos sao opcionais; so os que vierem preenchidos sao alterados. */
public record ListUpdateDto(
    @Size(min = 1, max = 100) String name,
    @Size(max = 500) String description,
    ListVisibility visibility) {}
