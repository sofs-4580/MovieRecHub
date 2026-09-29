package psia.movieRecHub.dto;

import java.util.List;

public record PublicProfilePageResponse(
    List<PublicProfileSummaryResponse> items, int page, int size,
    long totalElements, int totalPages) {}
