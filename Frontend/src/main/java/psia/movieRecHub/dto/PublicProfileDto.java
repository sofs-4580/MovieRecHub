package psia.movieRecHub.dto;

import java.util.Set;

public record PublicProfileDto(
    long userId,
    String username,
    String displayName,
    String bio,
    Set<MovieGenre> favoriteGenres,
    Set<Integer> favoriteDecades) {}
