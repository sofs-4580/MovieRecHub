package psia.movieRecHub.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import psia.movieRecHub.repository.ProfileRepository;

@Service
public class ProfileService {

  private final ProfileRepository profiles;

  public ProfileService(ProfileRepository profiles) {
    this.profiles = profiles;
  }

  /** Ensures a default profile exists without modifying an existing profile. */
  @Transactional
  public boolean createInitialProfile(Long userId) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("User ID must be positive");
    }
    return profiles.insertInitialProfileIfAbsent(userId) == 1;
  }
}
