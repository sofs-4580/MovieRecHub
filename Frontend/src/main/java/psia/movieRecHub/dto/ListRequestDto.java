package psia.movieRecHub.dto;

/** Corpo do POST /api/lists. */
public record ListRequestDto(String name, String description, ListVisibility visibility) {
}
