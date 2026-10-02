package psia.movieRecHub.dto;

import java.time.Instant;
import java.util.Set;
import psia.movieRecHub.model.MovieGenre;
import psia.movieRecHub.model.Profile;

public record OwnProfileResponse(
    long userId,
    String displayName,
    String bio,
    Set<MovieGenre> favoriteGenres,
    Set<Integer> favoriteDecades,
    Instant createdAt,
    Instant updatedAt) {

  public static OwnProfileResponse from(Profile profile) {
    return new OwnProfileResponse(profile.getUserId(), profile.getDisplayName(), profile.getBio(),
        Set.copyOf(profile.getFavoriteGenres()), Set.copyOf(profile.getFavoriteDecades()),
        profile.getCreatedAt(), profile.getUpdatedAt());
  }
}
