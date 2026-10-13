package psia.movieRecHub.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import psia.movieRecHub.entities.ListType;
import psia.movieRecHub.entities.ListVisibility;

public record MovieListDetailDto(
    Long id,
    Long ownerId,
    String name,
    String description,
    ListType type,
    ListVisibility visibility,
    LocalDateTime createdAt,
    List<ListItemResponseDto> items) {}
