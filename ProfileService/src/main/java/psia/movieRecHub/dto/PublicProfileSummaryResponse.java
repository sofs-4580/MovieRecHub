package psia.movieRecHub.dto;

import psia.movieRecHub.model.Profile;

public record PublicProfileSummaryResponse(long userId, String username, String displayName) {
  public static PublicProfileSummaryResponse from(Profile profile) {
    return new PublicProfileSummaryResponse(
        profile.getUserId(), profile.getUsername(), profile.getDisplayName());
  }
}
