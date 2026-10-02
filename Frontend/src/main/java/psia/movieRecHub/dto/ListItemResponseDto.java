package psia.movieRecHub.dto;

import java.time.LocalDateTime;

public record ListItemResponseDto(int position, LocalDateTime addedAt, MovieResponseDto movie) {
}
