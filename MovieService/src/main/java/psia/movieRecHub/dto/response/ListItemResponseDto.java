package psia.movieRecHub.dto.response;

import java.time.LocalDateTime;

public record ListItemResponseDto(int position, LocalDateTime addedAt, MovieResponseDto movie) {}
