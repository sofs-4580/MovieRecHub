package psia.movieRecHub.dto;

import java.util.Set;

public record RecommendationItemDto(
    Long movieId, String title, int year, Set<String> genres, String reason) {}
