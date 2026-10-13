package psia.movieRecHub.dto.response;

import java.util.Set;

public record RecommendationResponseItemDto(
    long movieId, String title, int year, Set<String> genres, String reason) {}
