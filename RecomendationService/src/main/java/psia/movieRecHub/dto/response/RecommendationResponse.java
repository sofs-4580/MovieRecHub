package psia.movieRecHub.dto.response;

public record RecommendationResponse(Long userId, String prompt, String recommendations) {}
