package psia.movieRecHub.dto;

import java.util.Set;

public record RecommendationProfileDto(Set<String> favoriteGenres, Set<Integer> favoriteDecades) {}
