package psia.movieRecHub.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MovieListDetailDto(Long id, Long ownerId, String name, String description, ListType type,
		ListVisibility visibility, LocalDateTime createdAt, List<ListItemResponseDto> items) {
}
