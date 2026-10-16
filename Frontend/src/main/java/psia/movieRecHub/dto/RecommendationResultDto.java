package psia.movieRecHub.dto;

import java.util.List;

// source pode ser llm, fallback ou pending
public record RecommendationResultDto(String source, List<RecommendationItemDto> items) {}
