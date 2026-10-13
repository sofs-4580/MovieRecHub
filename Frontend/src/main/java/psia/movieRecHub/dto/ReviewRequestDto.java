package psia.movieRecHub.dto;

/** Corpo do POST /api/reviews. */
public record ReviewRequestDto(Long userId, Long movieId, int rating, String comment) {
}
