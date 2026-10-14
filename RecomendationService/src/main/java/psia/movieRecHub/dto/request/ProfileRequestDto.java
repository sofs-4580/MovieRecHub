package psia.movieRecHub.dto.request;

import java.util.Set;

public record ProfileRequestDto(Set<String> favoriteGenres, Set<Integer> favoriteDecades) {}
