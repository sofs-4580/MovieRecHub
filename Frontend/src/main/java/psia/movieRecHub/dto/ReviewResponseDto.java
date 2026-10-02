package psia.movieRecHub.dto;

import java.time.LocalDateTime;

public record ReviewResponseDto(Long id, Long userId, Long movieId, int rating, String comment,
		LocalDateTime createdAt) {
}
