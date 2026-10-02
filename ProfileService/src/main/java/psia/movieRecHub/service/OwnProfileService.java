package psia.movieRecHub.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import psia.movieRecHub.dto.OwnProfileResponse;
import psia.movieRecHub.dto.UpdateProfileRequest;
import psia.movieRecHub.model.Profile;
import psia.movieRecHub.repository.ProfileRepository;

@Service
public class OwnProfileService {

  private final ProfileRepository profiles;

  public OwnProfileService(ProfileRepository profiles) {
    this.profiles = profiles;
  }

  @Transactional(readOnly = true)
  public OwnProfileResponse getOwn(long userId) {
    return OwnProfileResponse.from(profiles.findById(userId).orElseThrow(ProfileNotReadyException::new));
  }

  @Transactional
  public OwnProfileResponse updateOwn(long userId, UpdateProfileRequest update) {
    Profile profile = profiles.findByUserIdForUpdate(userId).orElseThrow(ProfileNotReadyException::new);
    profile.updateDetails(
        update.displayNamePresent() ? update.displayName() : profile.getDisplayName(),
        update.bioPresent() ? update.bio() : profile.getBio(),
        update.favoriteGenresPresent() ? update.favoriteGenres() : profile.getFavoriteGenres(),
        update.favoriteDecadesPresent() ? update.favoriteDecades() : profile.getFavoriteDecades());
    return OwnProfileResponse.from(profile);
  }
}
