package psia.movieRecHub.dto;

import java.time.Instant;
import java.util.Set;
import psia.movieRecHub.model.MovieGenre;
import psia.movieRecHub.model.Profile;

public record PublicProfileResponse(
    long userId, String username, String displayName, String bio,
    Set<MovieGenre> favoriteGenres, Set<Integer> favoriteDecades, Instant updatedAt) {

  public static PublicProfileResponse from(Profile profile) {
    return new PublicProfileResponse(profile.getUserId(), profile.getUsername(),
        profile.getDisplayName(), profile.getBio(), Set.copyOf(profile.getFavoriteGenres()),
        Set.copyOf(profile.getFavoriteDecades()), profile.getUpdatedAt());
  }
}
