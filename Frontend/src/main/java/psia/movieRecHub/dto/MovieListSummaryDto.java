package psia.movieRecHub.dto;

import java.time.LocalDateTime;

public record MovieListSummaryDto(Long id, Long ownerId, String name, String description, ListType type,
		ListVisibility visibility, LocalDateTime createdAt, int movieCount) {
}
