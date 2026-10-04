package psia.movieRecHub.dto;

import java.util.Set;

public record OwnProfileDto(
    long userId,
    String displayName,
    String bio,
    Set<MovieGenre> favoriteGenres,
    Set<Integer> favoriteDecades) {}
