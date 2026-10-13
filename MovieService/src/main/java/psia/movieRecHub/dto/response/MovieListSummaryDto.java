package psia.movieRecHub.dto.response;

import java.time.LocalDateTime;
import psia.movieRecHub.entities.ListType;
import psia.movieRecHub.entities.ListVisibility;

public record MovieListSummaryDto(
    Long id,
    Long ownerId,
    String name,
    String description,
    ListType type,
    ListVisibility visibility,
    LocalDateTime createdAt,
    int movieCount) {}
