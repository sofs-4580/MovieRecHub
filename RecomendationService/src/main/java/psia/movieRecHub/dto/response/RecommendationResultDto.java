package psia.movieRecHub.dto.response;

import java.util.List;

public record RecommendationResultDto(String source, List<RecommendationResponseItemDto> items) {}
